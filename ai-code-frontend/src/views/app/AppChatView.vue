<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'

import request from '@/request'
import aiAvatarUrl from '@/assets/aiAvatar.png'
import {
  deleteAppByAdmin,
  deleteMyApp,
  deployApp,
  downloadAppCode,
  getAppByIdForAdmin,
  getMyAppById,
  listAppFiles,
} from '@/api/appController'
import { listAppChatHistory } from '@/api/chatHistoryController'
import { useLoginUserStore } from '@/stores/loginUserStore'
import { getStaticPreviewUrl } from '@/config/env'
import {
  buildPreviewSrcDoc,
  buildVisualEditPrompt,
  createVisualIframeEditor,
  type VisualEditElementInfo,
  type VisualIframeEditor,
} from '@/utils/visualEdit'

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  streaming?: boolean
  createTime?: string
  statusText?: string
}

interface RenderSegment {
  type: 'text' | 'code'
  html: string
  language?: string
  markdown?: string
}

type WithStringAppId<T> = Omit<T, 'appId'> & { appId: string }
type WithStringId<T> = Omit<T, 'id'> & { id: string }

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const HISTORY_PAGE_SIZE = 10

const appInfo = ref<API.AppVO>()
const loadingApp = ref(false)
const sending = ref(false)
const deploying = ref(false)
const downloadingCode = ref(false)
const previewReady = ref(false)
const previewUrl = ref('')
const previewSrcDoc = ref('')
const inputPrompt = ref('')
const messages = ref<ChatMessage[]>([])
const messageContainerRef = ref<HTMLElement>()
const codeViewerContainerRef = ref<HTMLElement>()
const previewIframeRef = ref<HTMLIFrameElement>()
const eventSourceRef = ref<EventSource | null>(null)
const loadingHistory = ref(false)
const loadingMoreHistory = ref(false)
const hasMoreHistory = ref(false)
const historyCursor = ref<string>()
const totalHistory = ref(0)
const historyLoadedCount = ref(0)

const deployModalOpen = ref(false)
const deployedUrl = ref('')
const appDetailOpen = ref(false)
const deletingApp = ref(false)
const visualEditMode = ref(false)
const hoverElementInfo = ref<VisualEditElementInfo | null>(null)
const selectedElementInfo = ref<VisualEditElementInfo | null>(null)
const visualIframeEditorRef = ref<VisualIframeEditor | null>(null)
const CHAT_STREAM_PATH = '/app/chat/gen/code'
let previewSrcDocLoadToken = 0

// --- Workspace Tab & Code Viewer State ---
const activeTab = ref<'preview' | 'code'>('preview')
const files = ref<string[]>([])
const selectedKeys = ref<string[]>([])
const selectedFileContent = ref<string>('')
const expandedKeys = ref<string[]>([])
const loadingFiles = ref(false)

const getAllFolderPaths = (fileList: string[]): string[] => {
  const folders = new Set<string>()
  fileList.forEach((file) => {
    const parts = file.split('/')
    for (let i = 1; i < parts.length; i++) {
      folders.add(parts.slice(0, i).join('/'))
    }
  })
  return Array.from(folders)
}

watch(
  () => files.value,
  (newFiles) => {
    expandedKeys.value = getAllFolderPaths(newFiles)
  },
  { deep: true, immediate: true }
)
const loadingContent = ref(false)

const activeToolCallId = ref<string | null>(null)
const activeToolName = ref<string | null>(null)
const accumulatedArgs = ref<string>('')

const lineCount = computed(() => {
  if (!selectedFileContent.value) return 0
  return selectedFileContent.value.split('\n').length
})

const getLanguageFromPath = (filePath: string | undefined): string => {
  if (!filePath) return 'plaintext'
  const parts = filePath.split('.')
  const ext = parts[parts.length - 1]?.toLowerCase()
  if (!ext) return 'plaintext'
  if (ext === 'html' || ext === 'vue') return 'xml'
  if (ext === 'js' || ext === 'jsx') return 'javascript'
  if (ext === 'ts' || ext === 'tsx') return 'typescript'
  return ext
}

const highlightedCode = computed(() => {
  if (!selectedFileContent.value) return ''
  const lang = getLanguageFromPath(selectedKeys.value[0])
  return applyCodeHighlight(selectedFileContent.value, lang)
})

interface AntdTreeNode {
  title: string
  key: string
  isLeaf?: boolean
  children?: AntdTreeNode[]
}

const buildFileTree = (fileList: string[]): AntdTreeNode[] => {
  const root: AntdTreeNode[] = []

  fileList.forEach((file) => {
    const parts = file.split('/')
    let currentLevel = root

    parts.forEach((part, index) => {
      const isLeaf = index === parts.length - 1
      const key = parts.slice(0, index + 1).join('/')

      let existing = currentLevel.find((item) => item.title === part)
      if (!existing) {
        existing = {
          title: part,
          key: key,
          isLeaf: isLeaf,
        }
        if (!isLeaf) {
          existing.children = []
        }
        currentLevel.push(existing)
      }
      if (!isLeaf && existing.children) {
        currentLevel = existing.children
      }
    })
  })

  const sortTree = (nodes: AntdTreeNode[]) => {
    nodes.sort((a, b) => {
      if (a.isLeaf !== b.isLeaf) {
        return a.isLeaf ? 1 : -1
      }
      return a.title.localeCompare(b.title)
    })
    nodes.forEach((node) => {
      if (node.children) {
        sortTree(node.children)
      }
    })
  }

  sortTree(root)
  return root
}

const fileTreeData = computed(() => buildFileTree(files.value))

const fetchFileContent = async (filePath: string) => {
  if (!appId.value || !appInfo.value?.codeGenType) return
  loadingContent.value = true
  try {
    const codeGenType = appInfo.value.codeGenType
    const url = `/api/static/${codeGenType}_${appId.value}/${filePath}`
    const res = await fetch(`${url}?t=${Date.now()}`)
    if (res.ok) {
      selectedFileContent.value = await res.text()
    } else {
      selectedFileContent.value = `加载文件失败: ${res.statusText}`
    }
  } catch (err: any) {
    selectedFileContent.value = `加载文件失败: ${err.message || err}`
  } finally {
    loadingContent.value = false
  }
}

const loadProjectFiles = async () => {
  if (!appId.value) return
  loadingFiles.value = true
  try {
    const res = await listAppFiles({ appId: appId.value })
    if (res.data?.code === 0 && res.data?.data) {
      files.value = res.data.data
      if (files.value.length > 0) {
        const currentFile = selectedKeys.value[0]
        if (!currentFile || !files.value.includes(currentFile)) {
          const defaultFile = files.value.find(f => f.endsWith('index.html')) || files.value[0] || ''
          selectedKeys.value = [defaultFile]
          void fetchFileContent(defaultFile)
        } else {
          void fetchFileContent(currentFile)
        }
      } else {
        selectedKeys.value = []
        selectedFileContent.value = ''
      }
    }
  } catch (err) {
    console.error('Failed to load project files:', err)
  } finally {
    loadingFiles.value = false
  }
}

const onFileSelect = (keys: any[], info: any) => {
  if (info.node.isLeaf && keys.length > 0) {
    void fetchFileContent(keys[0])
  } else {
    if (selectedKeys.value.length === 0 && keys.length === 0) {
      selectedKeys.value = info.node.isLeaf ? [info.node.key] : selectedKeys.value
    }
  }
}

const parsePartialJson = (jsonStr: string): { relativeFilePath?: string; content?: string } => {
  let relativeFilePath: string | undefined
  let content: string | undefined

  const fileKeyIdx = jsonStr.indexOf('"relativeFilePath"')
  if (fileKeyIdx !== -1) {
    const startIdx = jsonStr.indexOf(':', fileKeyIdx)
    if (startIdx !== -1) {
      const quoteIdx = jsonStr.indexOf('"', startIdx + 1)
      if (quoteIdx !== -1) {
        let endIdx = -1
        for (let i = quoteIdx + 1; i < jsonStr.length; i++) {
          if (jsonStr[i] === '"' && jsonStr[i - 1] !== '\\') {
            endIdx = i
            break
          }
        }
        if (endIdx !== -1) {
          relativeFilePath = jsonStr.slice(quoteIdx + 1, endIdx)
        }
      }
    }
  }

  const contentKeyIdx = jsonStr.indexOf('"content"')
  if (contentKeyIdx !== -1) {
    const startIdx = jsonStr.indexOf(':', contentKeyIdx)
    if (startIdx !== -1) {
      const quoteIdx = jsonStr.indexOf('"', startIdx + 1)
      if (quoteIdx !== -1) {
        let unescapedStr = ''
        for (let i = quoteIdx + 1; i < jsonStr.length; i++) {
          if (jsonStr[i] === '"' && jsonStr[i - 1] !== '\\') {
            break
          }
          if (jsonStr[i] === '\\' && i + 1 < jsonStr.length) {
            const nextChar = jsonStr[i + 1]
            if (nextChar === 'n') unescapedStr += '\n'
            else if (nextChar === 't') unescapedStr += '\t'
            else if (nextChar === 'r') unescapedStr += '\r'
            else if (nextChar === '"') unescapedStr += '"'
            else if (nextChar === '\\') unescapedStr += '\\'
            else unescapedStr += nextChar
            i++
          } else {
            unescapedStr += jsonStr[i]
          }
        }
        content = unescapedStr
      }
    }
  }

  const newContentKeyIdx = jsonStr.indexOf('"newContent"')
  if (newContentKeyIdx !== -1) {
    const startIdx = jsonStr.indexOf(':', newContentKeyIdx)
    if (startIdx !== -1) {
      const quoteIdx = jsonStr.indexOf('"', startIdx + 1)
      if (quoteIdx !== -1) {
        let unescapedStr = ''
        for (let i = quoteIdx + 1; i < jsonStr.length; i++) {
          if (jsonStr[i] === '"' && jsonStr[i - 1] !== '\\') {
            break
          }
          if (jsonStr[i] === '\\' && i + 1 < jsonStr.length) {
            const nextChar = jsonStr[i + 1]
            if (nextChar === 'n') unescapedStr += '\n'
            else if (nextChar === 't') unescapedStr += '\t'
            else if (nextChar === 'r') unescapedStr += '\r'
            else if (nextChar === '"') unescapedStr += '"'
            else if (nextChar === '\\') unescapedStr += '\\'
            else unescapedStr += nextChar
            i++
          } else {
            unescapedStr += jsonStr[i]
          }
        }
        content = unescapedStr
      }
    }
  }

  if (relativeFilePath) {
    relativeFilePath = relativeFilePath.replace(/\\(.)/g, '$1')
  }

  return { relativeFilePath, content }
}

const scrollCodeViewerToBottom = async () => {
  await nextTick()
  const box = codeViewerContainerRef.value
  if (box) {
    box.scrollTop = box.scrollHeight
  }
}

const handleToolCallStream = (toolCall: any, target?: ChatMessage) => {
  // 1. Detect if this is a new tool call and reset state
  if (toolCall.id && toolCall.id !== activeToolCallId.value) {
    activeToolCallId.value = toolCall.id
    accumulatedArgs.value = ''
    activeToolName.value = toolCall.name || null
  }

  // 2. If name is sent in a chunk, update it
  if (toolCall.name) {
    activeToolName.value = toolCall.name
  }

  const currentToolName = activeToolName.value
  let actionText = '正在调用工具'
  if (currentToolName === 'writeFile') {
    actionText = '正在生成文件'
  } else if (currentToolName === 'modifyFile') {
    actionText = '正在修改文件'
  } else if (currentToolName === 'deleteFile') {
    actionText = '正在删除文件'
  } else if (currentToolName === 'readDir') {
    actionText = '正在读取目录'
  } else if (currentToolName === 'readFile') {
    actionText = '正在读取文件'
  } else if (currentToolName) {
    actionText = `正在执行: ${currentToolName}`
  }

  if (target) {
    target.statusText = `${actionText}...`
  }

  if (currentToolName !== 'writeFile' && currentToolName !== 'modifyFile') {
    return
  }

  // 3. Accumulate arguments
  if (toolCall.partialArguments) {
    const part = toolCall.partialArguments
    if (part.startsWith('{')) {
      accumulatedArgs.value = part
    } else {
      accumulatedArgs.value += part
    }
  }

  const { relativeFilePath, content } = parsePartialJson(accumulatedArgs.value)

  if (relativeFilePath) {
    const normalizedPath = relativeFilePath.trim()
    if (target) {
      target.statusText = `${actionText}: ${normalizedPath}`
    }
    if (normalizedPath && !files.value.includes(normalizedPath)) {
      files.value.push(normalizedPath)
    }

    selectedKeys.value = [normalizedPath]
    activeTab.value = 'code'

    if (content !== undefined) {
      selectedFileContent.value = content
      void scrollCodeViewerToBottom()
    }
  }
}

const guessFilePath = (language?: string): string => {
  if (!language) return ''
  if (language.includes('.') || language.includes('/')) {
    return language
  }
  const ext = language.toLowerCase()
  if (ext === 'html') return files.value.find(f => f.endsWith('.html')) || ''
  if (ext === 'css') return files.value.find(f => f.endsWith('.css')) || ''
  if (ext === 'js' || ext === 'javascript') return files.value.find(f => f.endsWith('.js') || f.endsWith('.jsx')) || ''
  if (ext === 'vue') return files.value.find(f => f.endsWith('.vue')) || ''
  return ''
}

const getFileIcon = (title: string, isLeaf: boolean): string => {
  if (!isLeaf) {
    // Folder icon - green themed like screenshot
    return `<svg width="16" height="16" viewBox="0 0 24 24" fill="#16a34a" style="display:block;"><path d="M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"/></svg>`
  }
  const name = title.toLowerCase()
  if (name === 'index.html') {
    // HTML5 orange shield
    return `<svg width="16" height="16" viewBox="0 0 24 24" fill="#ea580c" style="display:block;"><path d="M12 2L2 5l1.8 13.5L12 22l8.2-3.5L22 5z M18 9h-3.4l-0.3 3h3.4l-0.4 4.5L12 18.2l-5.3-1.7L6.3 12h3.3l-0.2-2.1H6.1l0.3-3h11.9z"/></svg>`
  }
  if (name === 'package.json') {
    // package.json red icon
    return `<svg width="16" height="16" viewBox="0 0 24 24" fill="#dc2626" style="display:block;"><path d="M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zM4 9h5v3H7v2H4V9zm11 5H11V9h4v5zm5-2h-3v2h-2V9h5v3z"/></svg>`
  }
  if (name === 'vite.config.js' || name === 'vite.config.ts') {
    // Vite purple triangle lightning logo
    return `<svg width="16" height="16" viewBox="0 0 24 24" fill="#c084fc" style="display:block;"><path d="M12 2L2 14h9v8l11-12h-9z"/></svg>`
  }
  if (name.endsWith('.jsx') || name.endsWith('.tsx') || name.endsWith('.js') || name.endsWith('.ts') || name.endsWith('.vue')) {
    // React/JSX/JS/TS cyan atom logo
    return `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#22d3ee" stroke-width="2.5" style="display:block;"><ellipse cx="12" cy="12" rx="10" ry="4" transform="rotate(30 12 12)" /><ellipse cx="12" cy="12" rx="10" ry="4" transform="rotate(90 12 12)" /><ellipse cx="12" cy="12" rx="10" ry="4" transform="rotate(150 12 12)" /><circle cx="12" cy="12" r="1.5" fill="#22d3ee" /></svg>`
  }
  // Default generic document
  return `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="display:block;"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>`
}

const viewCode = (segment: RenderSegment) => {
  activeTab.value = 'code'
  const guessed = guessFilePath(segment.language)
  if (guessed && files.value.includes(guessed)) {
    selectedKeys.value = [guessed]
    void fetchFileContent(guessed)
  }
}

const appId = computed(() => {
  const rawId = Array.isArray(route.params.id) ? route.params.id[0] : route.params.id
  return typeof rawId === 'string' ? rawId.trim() : ''
})

const adminQueryEnabled = computed(() => {
  const adminQuery = Array.isArray(route.query.admin) ? route.query.admin[0] : route.query.admin
  return adminQuery === '1'
})
const adminModeEnabled = ref(false)

const appName = computed(() => appInfo.value?.appName || `应用 #${appId.value}`)
const baseApiUrl = computed(() => String(request.defaults.baseURL || '/api').replace(/\/$/, ''))
const loginUserName = computed(() => loginUserStore.loginUser?.userName || '我')
const loginUserAvatar = computed(() => loginUserStore.loginUser?.userAvatar || '')
const isOwner = computed(() => {
  const loginUserId = loginUserStore.loginUser?.id
  return Boolean(loginUserId && appInfo.value?.userId && loginUserId === appInfo.value.userId)
})
const isAdmin = computed(() => loginUserStore.loginUser?.userRole === 'admin')
const canManageApp = computed(() => isOwner.value || isAdmin.value)
const appCreatorName = computed(() => {
  if (appInfo.value?.user?.userName) {
    return appInfo.value.user.userName
  }
  if (isOwner.value && loginUserStore.loginUser?.userName) {
    return loginUserStore.loginUser.userName
  }
  return '匿名用户'
})
const appCreatorAvatar = computed(() => {
  if (appInfo.value?.user?.userAvatar) {
    return appInfo.value.user.userAvatar
  }
  if (isOwner.value && loginUserStore.loginUser?.userAvatar) {
    return loginUserStore.loginUser.userAvatar
  }
  return ''
})
const selectedElementTitle = computed(() => {
  const elementInfo = selectedElementInfo.value
  if (!elementInfo) {
    return ''
  }

  const elementName = elementInfo.id
    ? `${elementInfo.tagName}#${elementInfo.id}`
    : elementInfo.className
      ? `${elementInfo.tagName}.${elementInfo.className.split(/\s+/)[0]}`
      : elementInfo.tagName
  return `已选中元素：${elementName}`
})
const selectedElementDescription = computed(() => {
  const elementInfo = selectedElementInfo.value
  if (!elementInfo) {
    return ''
  }

  const text = elementInfo.text ? `；文本：${elementInfo.text}` : ''
  return `选择器：${elementInfo.selector}${text}`
})
const activeHoverElementInfo = computed(() => {
  if (!hoverElementInfo.value) {
    return null
  }
  if (selectedElementInfo.value?.selector === hoverElementInfo.value.selector) {
    return null
  }
  return hoverElementInfo.value
})

const getMessageAvatar = (role: ChatMessage['role']) => {
  return role === 'assistant' ? aiAvatarUrl : loginUserAvatar.value
}

const getMessageAvatarFallback = (role: ChatMessage['role']) => {
  return role === 'assistant' ? 'AI' : loginUserName.value.slice(0, 1).toUpperCase()
}

const isHistoryMessage = (messageItem: ChatMessage) => {
  return messageItem.id.startsWith('history-')
}

const withStringAppId = <T extends { appId?: unknown }>(params: WithStringAppId<T>) => {
  return params as unknown as T
}

const withStringId = <T extends { id: unknown }>(params: WithStringId<T>) => {
  return params as unknown as T
}

const compareCreateTime = (left?: string, right?: string) => {
  const leftTime = left ? new Date(left).getTime() : 0
  const rightTime = right ? new Date(right).getTime() : 0
  return leftTime - rightTime
}

const buildHistoryMessageId = (record: API.ChatHistory, index: number) => {
  if (record.id !== undefined && record.id !== null) {
    return `history-${record.id}`
  }
  return `history-${record.createTime || 'unknown'}-${record.messageType || 'unknown'}-${index}`
}

const normalizeHistoryMessageRole = (messageType?: string): ChatMessage['role'] => {
  const normalized = (messageType || '').trim().toLowerCase()
  if (['user', 'users', 'human', 'question', 'prompt'].includes(normalized)) {
    return 'user'
  }
  return 'assistant'
}

const mapHistoryMessages = (records: API.ChatHistory[] = []) => {
  return records
    .map((record, index) => ({
      id: buildHistoryMessageId(record, index),
      role: normalizeHistoryMessageRole(record.messageType),
      content: record.message || '',
      createTime: record.createTime,
    }))
    .sort((left, right) => compareCreateTime(left.createTime, right.createTime))
}

const updateHistoryLoadedCount = () => {
  historyLoadedCount.value = messages.value.filter((item) => isHistoryMessage(item)).length
}

const updateHasMoreHistory = (fetchedCount: number, totalRow?: number) => {
  if (typeof totalRow === 'number' && totalRow >= 0) {
    totalHistory.value = totalRow
    hasMoreHistory.value = historyLoadedCount.value < totalRow
    return
  }
  hasMoreHistory.value = fetchedCount >= HISTORY_PAGE_SIZE
}

const mergeChatMessages = (incomingMessages: ChatMessage[], currentMessages: ChatMessage[]) => {
  const nextMessages: ChatMessage[] = []
  const seenIds = new Set<string>()

  for (const item of [...incomingMessages, ...currentMessages]) {
    if (seenIds.has(item.id)) {
      continue
    }
    seenIds.add(item.id)
    nextMessages.push(item)
  }

  return nextMessages
}

const getOldestCreateTime = (records: API.ChatHistory[] = []) => {
  return records
    .map((record) => record.createTime?.trim())
    .filter((value): value is string => Boolean(value))
    .sort(compareCreateTime)[0]
}

const scrollToBottom = async () => {
  await nextTick()
  const box = messageContainerRef.value
  if (box) {
    box.scrollTop = box.scrollHeight
  }
}

const closeStream = () => {
  if (eventSourceRef.value) {
    eventSourceRef.value.close()
    eventSourceRef.value = null
  }
}

const destroyVisualIframeEditor = () => {
  visualIframeEditorRef.value?.destroy()
  visualIframeEditorRef.value = null
}

const setupVisualIframeEditor = () => {
  destroyVisualIframeEditor()

  if (!previewIframeRef.value || !previewReady.value || !previewUrl.value) {
    return false
  }

  visualIframeEditorRef.value = createVisualIframeEditor({
    iframe: previewIframeRef.value,
    onHover: (elementInfo) => {
      hoverElementInfo.value = elementInfo
    },
    onSelect: (elementInfo) => {
      selectedElementInfo.value = elementInfo
    },
  })

  return visualEditMode.value ? visualIframeEditorRef.value.enable() : true
}

const handlePreviewLoad = () => {
  const setupSucceeded = setupVisualIframeEditor()
  if (visualEditMode.value && !setupSucceeded) {
    message.warning('预览页面暂时无法进入可视化编辑模式')
    visualEditMode.value = false
  }
}

const toggleVisualEditMode = () => {
  if (!previewReady.value || !previewUrl.value) {
    message.warning('预览生成完成后才能进入编辑模式')
    return
  }

  if (!visualEditMode.value) {
    const enabled = visualIframeEditorRef.value?.enable() || setupVisualIframeEditor()
    if (!enabled) {
      message.warning('预览页面暂时无法进入可视化编辑模式')
      return
    }
    visualEditMode.value = true
    message.info('已进入可视化编辑模式，点击预览中的元素即可选中')
    return
  }

  visualEditMode.value = false
  hoverElementInfo.value = null
  visualIframeEditorRef.value?.disable()
}

const clearSelectedElement = () => {
  selectedElementInfo.value = null
  visualIframeEditorRef.value?.clearSelected()
}

const resetVisualEditState = () => {
  visualEditMode.value = false
  hoverElementInfo.value = null
  clearSelectedElement()
  visualIframeEditorRef.value?.disable()
}

const getVisualEditBoxStyle = (elementInfo: VisualEditElementInfo) => ({
  transform: `translate(${elementInfo.rect.x}px, ${elementInfo.rect.y}px)`,
  width: `${Math.max(elementInfo.rect.width, 1)}px`,
  height: `${Math.max(elementInfo.rect.height, 1)}px`,
})

const handleVisualEditPointerMove = (event: MouseEvent) => {
  visualIframeEditorRef.value?.handlePointerMove(event)
}

const handleVisualEditPointerLeave = () => {
  visualIframeEditorRef.value?.handlePointerLeave()
}

const handleVisualEditClick = (event: MouseEvent) => {
  visualIframeEditorRef.value?.handleClick(event)
}

const loadChatHistory = async (loadMore = false) => {
  const currentAppId = appId.value
  if (!currentAppId) {
    return
  }

  if (loadMore) {
    if (!hasMoreHistory.value || !historyCursor.value) {
      return
    }
    loadingMoreHistory.value = true
  } else {
    loadingHistory.value = true
  }

  const box = messageContainerRef.value
  const previousScrollHeight = box?.scrollHeight ?? 0
  const previousScrollTop = box?.scrollTop ?? 0

  try {
    const res = await listAppChatHistory(
      withStringAppId<API.listAppChatHistoryParams>({
        appId: currentAppId,
        pageSize: HISTORY_PAGE_SIZE,
        lastCreateTime: loadMore ? historyCursor.value : undefined,
      }),
    )

    if (res.data.code === 0 && res.data.data) {
      const records = res.data.data.records ?? []
      const incomingMessages = mapHistoryMessages(records)
      const nonHistoryMessages = messages.value.filter((item) => !isHistoryMessage(item))

      if (loadMore) {
        messages.value = mergeChatMessages(incomingMessages, messages.value)
        await nextTick()
        if (box) {
          const nextScrollHeight = box.scrollHeight
          box.scrollTop = previousScrollTop + (nextScrollHeight - previousScrollHeight)
        }
      } else {
        messages.value = mergeChatMessages(incomingMessages, nonHistoryMessages)
      }

      updateHistoryLoadedCount()
      historyCursor.value = getOldestCreateTime(records)
      updateHasMoreHistory(records.length, res.data.data.totalRow)
      return
    }

    message.error(res.data.message || '加载历史消息失败')
  } catch {
    message.error(loadMore ? '加载更多历史消息失败，请稍后重试' : '加载历史消息失败，请稍后重试')
  } finally {
    if (loadMore) {
      loadingMoreHistory.value = false
    } else {
      loadingHistory.value = false
    }
  }
}

const loadMoreHistoryMessages = () => {
  void loadChatHistory(true)
}

const buildPreviewUrl = (codeGenType?: string) => {
  const finalCodeGenType = codeGenType || appInfo.value?.codeGenType
  if (!finalCodeGenType || !appId.value) {
    return ''
  }
  return getStaticPreviewUrl(finalCodeGenType, appId.value)
}

const loadPreviewSrcDoc = async (url: string) => {
  const currentToken = ++previewSrcDocLoadToken
  previewSrcDoc.value = ''

  if (!url) {
    return
  }

  try {
    const res = await fetch(url, { credentials: 'include' })
    if (!res.ok) {
      throw new Error(`Preview HTML request failed: ${res.status}`)
    }

    const html = await res.text()
    if (currentToken !== previewSrcDocLoadToken) {
      return
    }

    const baseUrl = new URL(url, window.location.href).href
    previewSrcDoc.value = buildPreviewSrcDoc(html, baseUrl)
  } catch {
    if (currentToken === previewSrcDocLoadToken) {
      previewSrcDoc.value = ''
    }
  }
}

const refreshPreview = (codeGenType?: string) => {
  const nextUrl = buildPreviewUrl(codeGenType)
  previewUrl.value = nextUrl
  previewReady.value = Boolean(nextUrl)
  void loadPreviewSrcDoc(nextUrl)
}

const ensureAdminMode = async () => {
  if (!adminQueryEnabled.value) {
    return false
  }

  if (!loginUserStore.loginUser?.userRole) {
    await loginUserStore.fetchLoginUser()
  }

  return loginUserStore.loginUser?.userRole === 'admin'
}

const loadAppInfo = async () => {
  if (!appId.value) {
    message.error('应用 ID 不合法')
    await router.replace('/')
    return
  }

  loadingApp.value = true
  try {
    const useAdminApi = await ensureAdminMode()
    adminModeEnabled.value = useAdminApi
    const res = useAdminApi
      ? await getAppByIdForAdmin(withStringId<API.getAppByIdForAdminParams>({ id: appId.value }))
      : await getMyAppById(withStringId<API.getMyAppByIdParams>({ id: appId.value }))
    if (res.data.code === 0 && res.data.data) {
      appInfo.value = res.data.data
      if (res.data.data.codeGenType) {
        refreshPreview(res.data.data.codeGenType)
      }
      return
    }
    message.error(res.data.message || '获取应用信息失败')
  } catch {
    message.error('获取应用信息失败，请稍后重试')
  } finally {
    loadingApp.value = false
  }
}

const extractTextFromPayload = (payload: unknown): string => {
  if (!payload) {
    return ''
  }

  if (typeof payload === 'string') {
    if (payload.trim().startsWith('{')) {
      try {
        const parsed = JSON.parse(payload)
        if (parsed && typeof parsed === 'object') {
          if (parsed.type === 'thinking' && typeof parsed.data === 'string') {
            return parsed.data
          }
          if (parsed.type === 'tool_call') {
            return ''
          }
        }
      } catch {
        // ignore
      }
    }
    return payload
  }

  if (Array.isArray(payload)) {
    return payload.map((item) => extractTextFromPayload(item)).join('')
  }

  if (typeof payload === 'object') {
    const record = payload as Record<string, unknown>

    // 兼容后端包装格式：{"d":"..."}
    for (const key of ['d', 'content', 'delta', 'text', 'answer', 'message']) {
      if (typeof record[key] === 'string') {
        const val = record[key] as string
        if (val.trim().startsWith('{')) {
          try {
            const parsed = JSON.parse(val)
            if (parsed && typeof parsed === 'object') {
              if (parsed.type === 'thinking' && typeof parsed.data === 'string') {
                return parsed.data
              }
              if (parsed.type === 'tool_call') {
                return ''
              }
            }
          } catch {
            // ignore
          }
        }
        return val
      }
    }

    if (Array.isArray(record.choices)) {
      return record.choices
        .map((choice) => {
          if (typeof choice !== 'object' || !choice) {
            return ''
          }
          const choiceRecord = choice as Record<string, unknown>
          if (typeof choiceRecord.text === 'string') {
            return choiceRecord.text
          }
          if (choiceRecord.delta) {
            return extractTextFromPayload(choiceRecord.delta)
          }
          if (choiceRecord.message) {
            return extractTextFromPayload(choiceRecord.message)
          }
          return ''
        })
        .join('')
    }

    for (const key of ['data', 'result', 'output']) {
      if (record[key]) {
        return extractTextFromPayload(record[key])
      }
    }
  }

  return ''
}

const isDonePayload = (rawData: string, payload: unknown): boolean => {
  const normalized = rawData.trim().toLowerCase()
  if (['[done]', 'done', 'eof', '<eof>'].includes(normalized)) {
    return true
  }

  if (typeof payload === 'object' && payload) {
    const record = payload as Record<string, unknown>
    if (record.done === true || record.isEnd === true || record.finished === true) {
      return true
    }
    const status = typeof record.status === 'string' ? record.status.toLowerCase() : ''
    if (['done', 'finish', 'finished', 'complete'].includes(status)) {
      return true
    }
    if (record.event === 'done') {
      return true
    }
  }

  return false
}

const escapeHtml = (text: string) => {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

const formatPlainTextToHtml = (text: string) => {
  return escapeHtml(text).replace(/\n/g, '<br />')
}

const applyCodeHighlight = (code: string, language?: string) => {
  try {
    if (language && hljs.getLanguage(language)) {
      return hljs.highlight(code, { language, ignoreIllegals: true }).value
    }
  } catch {
    // ignore
  }
  return escapeHtml(code)
}

const parseMessageSegments = (content: string): RenderSegment[] => {
  if (!content) {
    return [{ type: 'text', html: '', markdown: '' }]
  }

  // 性能优化：如果不包含代码块标识符，直接返回文本片段，避免正则匹配消耗 CPU
  if (!content.includes('```')) {
    return [{ type: 'text', html: '', markdown: content }]
  }

  const segments: RenderSegment[] = []
  const codeBlockPattern = /```([a-zA-Z0-9_-]+)?\n?([\s\S]*?)```/g
  let lastIndex = 0
  let match: RegExpExecArray | null

  while ((match = codeBlockPattern.exec(content)) !== null) {
    if (match.index > lastIndex) {
      const textPart = content.slice(lastIndex, match.index)
      segments.push({
        type: 'text',
        html: '',
        markdown: textPart,
      })
    }

    segments.push({
      type: 'code',
      language: match[1]?.trim() || undefined,
      html: '', // 代码文件预览以 stub 呈现，无需在此进行高亮渲染以节省 CPU 性能
    })
    lastIndex = match.index + match[0].length
  }

  if (lastIndex < content.length) {
    const textPart = content.slice(lastIndex)
    segments.push({
      type: 'text',
      html: '',
      markdown: textPart,
    })
  }

  return segments.length ? segments : [{ type: 'text', html: '', markdown: content }]
}

const looksLikeCode = (content: string) => {
  const trimmed = content.trim()
  if (!trimmed) {
    return false
  }

  if (/[{};]/.test(trimmed) && /\n/.test(trimmed)) {
    return true
  }

  if (/<[a-zA-Z][\s\S]*?>/.test(trimmed)) {
    return true
  }

  return /(^|\n)\s*(const|let|var|function|class|import|export)\s+/m.test(trimmed)
}

const getMessageSegments = (messageItem: ChatMessage): RenderSegment[] => {
  if (messageItem.role === 'assistant') {
    const parsedSegments = parseMessageSegments(messageItem.content)
    // 性能优化：如果在流式传输中，直接返回解析片段，无需进行 looksLikeCode 检测
    if (messageItem.streaming) {
      return parsedSegments
    }
    const hasCodeSegment = parsedSegments.some((segment) => segment.type === 'code')
    if (!hasCodeSegment && looksLikeCode(messageItem.content)) {
      return [
        {
          type: 'code',
          html: '', // 无需在此高亮渲染
          language: 'code',
        },
      ]
    }
    return parsedSegments
  }
  return [{ type: 'text', html: formatPlainTextToHtml(messageItem.content), markdown: messageItem.content }]
}

const sendPrompt = async (promptInput?: string) => {
  const prompt = (promptInput ?? inputPrompt.value).trim()
  if (!prompt) {
    message.warning('请输入消息后再发送')
    return
  }

  if (!appId.value) {
    message.error('应用 ID 不合法')
    return
  }

  if (sending.value) {
    message.info('上一条消息还在生成中，请稍候')
    return
  }

  closeStream()
  const selectedElementSnapshot = selectedElementInfo.value
  const finalPrompt = buildVisualEditPrompt(prompt, selectedElementSnapshot)

  const userMessage: ChatMessage = {
    id: `user-${Date.now()}`,
    role: 'user',
    content: prompt,
  }
  const assistantMessage: ChatMessage = {
    id: `assistant-${Date.now()}`,
    role: 'assistant',
    content: '',
    streaming: true,
  }

  messages.value.push(userMessage, assistantMessage)
  inputPrompt.value = ''
  resetVisualEditState()
  sending.value = true
  previewReady.value = false
  previewUrl.value = ''
  previewSrcDoc.value = ''
  previewSrcDocLoadToken += 1
  destroyVisualIframeEditor()
  activeToolCallId.value = null
  activeToolName.value = null
  accumulatedArgs.value = ''
  await scrollToBottom()

  const assistantIndex = messages.value.length - 1
  const params = new URLSearchParams({
    appId: appId.value,
    userPrompt: finalPrompt,
    mode: 'workflow',
  })
  const streamUrl = `${baseApiUrl.value}${CHAT_STREAM_PATH}?${params.toString()}`
  const source = new EventSource(streamUrl, { withCredentials: true })
  eventSourceRef.value = source

  let finished = false
  let hasAnyChunk = false
  let hasStreamEvent = false
  let hasDoneEvent = false

  const finalize = async (failed = false, customError?: string) => {
    if (finished) {
      return
    }
    finished = true
    closeStream()
    sending.value = false
    const target = messages.value[assistantIndex]
    if (target) {
      target.streaming = false
      target.statusText = undefined
      if (customError) {
        target.content = target.content.trim()
          ? `${target.content}\n\n[错误] ${customError}`
          : customError
      } else {
        target.content = target.content.trim() || (failed ? '生成失败，请稍后重试。' : '已完成。')
      }
    }
    await loadAppInfo()
    refreshPreview()
    await loadProjectFiles()
    activeTab.value = 'preview'
    await scrollToBottom()

    // 延迟 500ms 重新加载干净的历史记录，以用数据库中的干净对话替换本地包含工具调用和思考过程的临时消息
    setTimeout(async () => {
      messages.value = messages.value.filter((item) => isHistoryMessage(item))
      await loadChatHistory()
      await scrollToBottom()
    }, 500)
  }

  source.onmessage = (event) => {
    hasStreamEvent = true
    const rawData = `${event.data ?? ''}`
    if (!rawData.trim()) {
      return
    }

    let payload: unknown = rawData
    if (rawData.startsWith('{') || rawData.startsWith('[')) {
      try {
        payload = JSON.parse(rawData)
      } catch {
        payload = rawData
      }
    }

    if (isDonePayload(rawData, payload)) {
      void finalize(false)
      return
    }

    let innerStr = ''
    if (payload && typeof payload === 'object') {
      const record = payload as Record<string, unknown>
      if (typeof record.d === 'string') {
        innerStr = record.d
      }
    } else if (typeof payload === 'string') {
      innerStr = payload
    }

    let shouldAppend = true
    const target = messages.value[assistantIndex]

    if (innerStr.trim().startsWith('{')) {
      try {
        const parsed = JSON.parse(innerStr)
        if (parsed && typeof parsed === 'object') {
          if (parsed.type === 'tool_call') {
            if (target) {
              handleToolCallStream(parsed, target)
            }
            shouldAppend = false
          } else if (parsed.type === 'thinking') {
            if (target) {
              target.statusText = '正在思考...'
            }
            shouldAppend = false
          }
        }
      } catch {
        // ignore
      }
    }

    if (shouldAppend) {
      if (innerStr.includes('[选择工具]')) {
        const match = innerStr.match(/\[选择工具\]\s*(.+)/)
        const toolName = (match && match[1]) ? match[1].trim() : '工具'
        if (target) {
          target.statusText = `正在准备工具: ${toolName}`
        }
        shouldAppend = false
      } else if (innerStr.includes('[工具调用]')) {
        const match = innerStr.match(/\[工具调用\]\s*(\S+)\s*(\S+)/)
        if (target) {
          if (match && match[1] && match[2]) {
            const action = match[1].trim()
            const path = match[2].trim()
            let status = `${action}完成: ${path}`
            if (action === '写入文件') {
              status = `文件生成完成: ${path}`
            } else if (action === '修改文件') {
              status = `文件修改完成: ${path}`
            } else if (action === '删除文件') {
              status = `文件删除完成: ${path}`
            } else if (action === '读取目录') {
              status = `目录读取完成: ${path}`
            } else if (action === '读取文件') {
              status = `文件读取完成: ${path}`
            }
            target.statusText = status
          } else {
            target.statusText = innerStr.trim()
          }
        }
        shouldAppend = false
      }
    }

    if (shouldAppend) {
      const chunkText = extractTextFromPayload(payload)
      if (chunkText) {
        hasAnyChunk = true
        if (target) {
          target.content += chunkText
          void scrollToBottom()
        }
      }
    }
  }

  source.addEventListener('done', () => {
    hasStreamEvent = true
    hasDoneEvent = true
    void finalize(false)
  })

  source.addEventListener('business-error', function (event: MessageEvent) {
    if (finished) {
      return
    }
    let errorMessage = '生成失败'
    try {
      const errorData = JSON.parse(event.data)
      errorMessage = errorData.message || (event.data ? `生成失败：${event.data}` : '生成失败')
    } catch (parseError) {
      console.error('Failed to parse business error event data:', event.data, parseError)
      errorMessage = event.data ? `生成失败：${event.data}` : '生成失败'
    }
    message.error(errorMessage)
    void finalize(true, errorMessage)
  })

  source.onerror = () => {
    if (finished) {
      return
    }

    // 服务端流式正常结束时，浏览器通常也会触发 onerror（连接被关闭），这里不应误判失败。
    if (hasDoneEvent || hasStreamEvent || hasAnyChunk || source.readyState === EventSource.CLOSED) {
      void finalize(false)
      return
    }

    message.error('AI 响应失败，请稍后重试')
    void finalize(true)
  }
}

const handleDeploy = async () => {
  if (!appId.value) {
    message.error('应用 ID 不合法')
    return
  }

  deploying.value = true
  try {
    const res = await deployApp(withStringAppId<API.AppDeployRequest>({ appId: appId.value }))
    if (res.data.code === 0 && res.data.data) {
      deployedUrl.value = res.data.data
      deployModalOpen.value = true
      activeTab.value = 'preview'
      message.success('部署成功')
      return
    }
    message.error(res.data.message || '部署失败')
  } catch {
    message.error('部署失败，请稍后重试')
  } finally {
    deploying.value = false
  }
}

const copyDeployUrl = async () => {
  if (!deployedUrl.value) {
    return
  }
  try {
    await navigator.clipboard.writeText(deployedUrl.value)
    message.success('部署地址已复制')
  } catch {
    message.error('复制失败，请手动复制')
  }
}

const openDeployUrl = () => {
  if (!deployedUrl.value) {
    return
  }
  window.open(deployedUrl.value, '_blank')
}

const decodeFileName = (fileName: string) => {
  try {
    return decodeURIComponent(fileName)
  } catch {
    return fileName
  }
}

const parseDownloadFileName = (contentDisposition?: string) => {
  if (!contentDisposition) {
    return ''
  }

  const utf8FileNameMatch = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8FileNameMatch?.[1]) {
    return decodeFileName(utf8FileNameMatch[1])
  }

  const fileNameMatch = contentDisposition.match(/filename="?([^"]+)"?/i)
  return fileNameMatch?.[1] ? decodeFileName(fileNameMatch[1]) : ''
}

const saveBlobAsFile = (blob: Blob, fileName: string) => {
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(objectUrl)
}

const handleDownloadCode = async () => {
  if (!appId.value) {
    message.error('应用 ID 不合法')
    return
  }

  downloadingCode.value = true
  try {
    const res = await downloadAppCode(
      withStringAppId<API.downloadAppCodeParams>({ appId: appId.value }),
      { responseType: 'blob' },
    )
    const contentDisposition = res.headers['content-disposition']
    const fileName = parseDownloadFileName(contentDisposition) || `${appName.value}.zip`
    const blob = new Blob([res.data], { type: res.headers['content-type'] || 'application/zip' })
    saveBlobAsFile(blob, fileName)
    message.success('代码下载已开始')
  } catch {
    message.error('下载代码失败，请稍后重试')
  } finally {
    downloadingCode.value = false
  }
}

const openAppDetail = () => {
  appDetailOpen.value = true
}

const goToAppEdit = () => {
  if (!appId.value) {
    message.error('应用 ID 不合法')
    return
  }

  void router.push({
    path: `/app/edit/${appId.value}`,
    query: adminModeEnabled.value ? { admin: '1' } : undefined,
  })
}

const handleDeleteApp = async () => {
  const currentId = appInfo.value?.id
  if (!currentId) {
    message.error('应用 ID 不合法')
    return
  }

  deletingApp.value = true
  try {
    const useAdminDelete = adminModeEnabled.value || (isAdmin.value && !isOwner.value)
    const res = useAdminDelete
      ? await deleteAppByAdmin({ id: currentId })
      : await deleteMyApp({ id: currentId })
    if (res.data.code === 0) {
      message.success('删除应用成功')
      appDetailOpen.value = false
      await router.replace('/')
      return
    }
    message.error(res.data.message || '删除应用失败')
  } catch {
    message.error('删除应用失败，请稍后重试')
  } finally {
    deletingApp.value = false
  }
}

const sendCurrentPrompt = () => {
  void sendPrompt()
}

const removeInitPromptFromUrl = async () => {
  if (!route.query.initPrompt) {
    return
  }
  const nextQuery = { ...route.query }
  delete nextQuery.initPrompt
  await router.replace({ query: nextQuery })
}

onMounted(async () => {
  if (!loginUserStore.loginUser?.id) {
    await loginUserStore.fetchLoginUser()
  }
  await loadAppInfo()
  await loadProjectFiles()
  await loadChatHistory()
  const initPromptQuery = Array.isArray(route.query.initPrompt)
    ? route.query.initPrompt[0]
    : route.query.initPrompt
  const initPrompt = typeof initPromptQuery === 'string' ? initPromptQuery.trim() : ''
  if (initPrompt) {
    await sendPrompt(initPrompt)
    await removeInitPromptFromUrl()
  }
})

onBeforeUnmount(() => {
  closeStream()
  destroyVisualIframeEditor()
})
</script>

<template>
  <section class="app-chat-view">
    <header class="app-chat-view__topbar">
      <div>
        <h1 class="app-chat-view__title">{{ appName }}</h1>
        <p class="app-chat-view__meta">
          <span>应用 ID：{{ appId }}</span>
          <span class="app-chat-view__meta-separator">·</span>
          <span>
            生成类型：<strong class="app-chat-view__code-type">{{
              appInfo?.codeGenType || '-'
            }}</strong>
          </span>
        </p>
      </div>
      <a-space>
        <a-button @click="router.push('/')">返回首页</a-button>
        <a-button @click="openAppDetail">应用详情</a-button>
        <a-button :loading="downloadingCode" @click="handleDownloadCode">下载代码</a-button>
        <a-button type="primary" :loading="deploying" @click="handleDeploy">部署应用</a-button>
      </a-space>
    </header>

    <main class="app-chat-view__workspace">
      <section class="chat-panel">
        <div ref="messageContainerRef" class="chat-panel__messages">
          <div v-if="hasMoreHistory || historyLoadedCount > 0" class="chat-panel__history-toolbar">
            <a-button
              v-if="hasMoreHistory"
              type="link"
              class="chat-panel__history-button"
              :loading="loadingMoreHistory"
              @click="loadMoreHistoryMessages"
            >
              加载更多
            </a-button>
            <span v-else class="chat-panel__history-tip">
              已展示全部 {{ totalHistory || historyLoadedCount }} 条历史消息
            </span>
          </div>

          <div
            v-for="item in messages"
            :key="item.id"
            :class="['chat-message', `chat-message--${item.role}`]"
          >
            <a-avatar :src="getMessageAvatar(item.role)" class="chat-message__avatar">
              {{ getMessageAvatarFallback(item.role) }}
            </a-avatar>
            <div class="chat-message__bubble">
              <template
                v-for="(segment, index) in getMessageSegments(item)"
                :key="`${item.id}-${index}`"
              >
                <div
                  v-if="segment.type === 'text' && item.role === 'user'"
                  class="chat-message__text"
                  v-html="segment.html"
                />
                <MarkdownRenderer
                  v-else-if="segment.type === 'text' && item.role === 'assistant'"
                  class="chat-message__text"
                  :content="segment.markdown || ''"
                />
                <div v-else class="chat-message__code-stub">
                  <span class="chat-message__code-stub-icon">📁</span>
                  <span class="chat-message__code-stub-text">代码文件已生成 ({{ segment.language || 'code' }})</span>
                  <a-button type="link" size="small" class="chat-message__code-stub-btn" @click="viewCode(segment)">
                    查看代码
                  </a-button>
                </div>
              </template>
              <div
                v-if="item.role === 'assistant' && item.streaming && item.statusText"
                class="chat-message__status"
              >
                <span class="chat-message__status-spinner"></span>
                <span class="chat-message__status-text">{{ item.statusText }}</span>
              </div>
              <span v-if="item.streaming" class="chat-message__cursor">|</span>
            </div>
          </div>

          <a-empty
            v-if="!loadingHistory && messages.length === 0"
            description="输入你的需求后，AI 会在这里实时输出生成过程"
          />
          <div v-if="loadingHistory && messages.length === 0" class="chat-panel__history-loading">
            <a-spin />
          </div>
        </div>

        <div class="chat-panel__editor">
          <a-alert
            v-if="selectedElementInfo"
            class="chat-panel__selected-element"
            type="info"
            show-icon
            closable
            :message="selectedElementTitle"
            :description="selectedElementDescription"
            @close="clearSelectedElement"
          />
          <a-textarea
            v-model:value="inputPrompt"
            :auto-size="{ minRows: 3, maxRows: 6 }"
            placeholder="继续输入新的修改指令，例如：把按钮换成圆角并补充 FAQ 区域"
            @keydown.enter.exact.prevent="sendCurrentPrompt"
          />
          <div class="chat-panel__editor-footer">
            <span>按 Enter 发送，Shift + Enter 换行</span>
            <a-button type="primary" :loading="sending" @click="sendCurrentPrompt">发送</a-button>
          </div>
        </div>
      </section>

      <aside class="workspace-panel">
        <div class="workspace-panel__header">
          <div class="workspace-tabs">
            <div 
              class="workspace-tab-item" 
              :class="{ 'active': activeTab === 'preview' }" 
              @click="activeTab = 'preview'"
            >
              预览
            </div>
            <div 
              class="workspace-tab-item" 
              :class="{ 'active': activeTab === 'code' }" 
              @click="activeTab = 'code'"
            >
              代码
            </div>
          </div>
          
          <div v-if="activeTab === 'preview' && previewReady && previewUrl" class="preview-panel__actions">
            <a-button
              size="small"
              :type="visualEditMode ? 'primary' : 'default'"
              :disabled="sending"
              @click="toggleVisualEditMode"
            >
              {{ visualEditMode ? '退出编辑' : '编辑模式' }}
            </a-button>
            <a :href="previewUrl" target="_blank" rel="noopener noreferrer"> 新窗口打开 </a>
          </div>
        </div>
        
        <div class="workspace-panel__body">
          <!-- Preview Tab Content -->
          <div v-show="activeTab === 'preview'" class="preview-content-wrapper">
            <div
              class="preview-panel__body"
              :class="{ 'preview-panel__body--visual-editing': visualEditMode }"
            >
              <iframe
                v-if="previewReady && previewUrl"
                ref="previewIframeRef"
                :src="previewUrl"
                :srcdoc="previewSrcDoc || undefined"
                title="preview"
                @load="handlePreviewLoad"
              />
              <div
                v-if="previewReady && previewUrl && visualEditMode"
                class="preview-panel__visual-edit-layer"
                @pointermove="handleVisualEditPointerMove"
                @pointerleave="handleVisualEditPointerLeave"
                @pointerdown.prevent.stop="handleVisualEditClick"
                @click.prevent.stop
              >
                <div
                  v-if="activeHoverElementInfo"
                  class="preview-panel__visual-edit-box preview-panel__visual-edit-box--hover"
                  :style="getVisualEditBoxStyle(activeHoverElementInfo)"
                ></div>
                <div
                  v-if="selectedElementInfo"
                  class="preview-panel__visual-edit-box preview-panel__visual-edit-box--selected"
                  :style="getVisualEditBoxStyle(selectedElementInfo)"
                ></div>
              </div>
              <div v-if="!previewReady || !previewUrl" class="preview-placeholder">
                <p v-if="sending">代码生成中，完成后自动展示预览...</p>
                <p v-else>发送消息后，生成完成会在这里展示网站效果。</p>
              </div>
            </div>
          </div>
          
          <!-- Code Tab Content -->
          <div v-if="activeTab === 'code'" class="code-viewer-panel">
            <aside class="code-viewer-sidebar">
              <div class="code-viewer-sidebar__title">文件树</div>
              <div class="code-viewer-sidebar__tree">
                <a-tree
                  v-model:selectedKeys="selectedKeys"
                  v-model:expandedKeys="expandedKeys"
                  :tree-data="fileTreeData"
                  @select="onFileSelect"
                  class="custom-antd-tree"
                >
                  <template #title="{ title, isLeaf }">
                    <span class="tree-node-title">
                      <span class="tree-node-icon" v-html="getFileIcon(title, isLeaf)"></span>
                      <span class="tree-node-text">{{ title }}</span>
                    </span>
                  </template>
                </a-tree>
              </div>
            </aside>
            <main class="code-viewer-main">
              <header class="code-viewer-header">
                <span class="code-viewer-path">{{ selectedKeys[0] || '未选择文件' }}</span>
              </header>
              <div ref="codeViewerContainerRef" class="code-viewer-container">
                <div v-if="loadingContent" class="code-viewer-loading">
                  <a-spin />
                </div>
                <div v-else-if="selectedFileContent" class="code-viewer-body">
                  <div class="code-viewer-line-numbers">
                    <span v-for="n in lineCount" :key="n">{{ n }}</span>
                  </div>
                  <pre class="code-viewer-pre"><code v-html="highlightedCode"></code></pre>
                </div>
                <div v-else class="code-viewer-empty">
                  <p>暂无代码内容</p>
                </div>
              </div>
            </main>
          </div>
        </div>
      </aside>
    </main>

    <div v-if="loadingApp" class="app-chat-view__loading-mask">
      <a-spin />
    </div>

    <a-modal
      v-model:open="deployModalOpen"
      title="部署成功"
      :footer="null"
      :mask-closable="true"
      width="620px"
    >
      <p>访问地址</p>
      <a-input :value="deployedUrl" readonly />
      <div class="deploy-modal__actions">
        <a-space>
          <a-button @click="copyDeployUrl">复制地址</a-button>
          <a-button type="primary" @click="openDeployUrl">打开站点</a-button>
        </a-space>
      </div>
    </a-modal>

    <a-modal
      v-model:open="appDetailOpen"
      title="应用详情"
      :footer="null"
      :mask-closable="true"
      width="560px"
    >
      <section class="app-detail">
        <div class="app-detail__block">
          <h3>应用基础信息</h3>
          <div class="app-detail__creator">
            <a-avatar :size="44" :src="appCreatorAvatar">{{
              appCreatorName.slice(0, 1).toUpperCase()
            }}</a-avatar>
            <div>
              <p class="app-detail__label">创建者</p>
              <p class="app-detail__value">{{ appCreatorName }}</p>
            </div>
          </div>
          <p class="app-detail__time">创建时间：{{ appInfo?.createTime || '-' }}</p>
          <p class="app-detail__meta">
            生成类型：<strong class="app-chat-view__code-type">{{
              appInfo?.codeGenType || '-'
            }}</strong>
          </p>
        </div>

        <div v-if="canManageApp" class="app-detail__block">
          <h3>操作栏</h3>
          <a-space>
            <a-button @click="goToAppEdit">修改</a-button>
            <a-popconfirm
              title="确认删除该应用吗？"
              ok-text="确认"
              cancel-text="取消"
              @confirm="handleDeleteApp"
            >
              <a-button danger :loading="deletingApp">删除</a-button>
            </a-popconfirm>
          </a-space>
        </div>
      </section>
    </a-modal>
  </section>
</template>

<style scoped>
.app-chat-view {
  width: 100%;
  flex: 1;
  height: 100%;
  min-height: 0;
  position: relative;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: linear-gradient(180deg, #f8fbff 0%, #eef6ff 100%);
  padding: 18px 22px 22px;
}

.app-chat-view__topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.92);
  padding: 16px 18px;
  flex-shrink: 0;
}

.app-chat-view__title {
  margin: 0;
  color: #0f172a;
  font-size: 24px;
}

.app-chat-view__meta {
  margin: 6px 0 0;
  color: rgba(15, 23, 42, 0.56);
  font-size: 13px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.app-chat-view__meta-separator {
  color: rgba(15, 23, 42, 0.32);
}

.app-chat-view__code-type {
  display: inline-flex;
  align-items: center;
  border: 1px solid rgba(22, 119, 255, 0.22);
  border-radius: 999px;
  background: rgba(22, 119, 255, 0.1);
  color: #1668dc;
  padding: 2px 8px;
  line-height: 1.45;
  font-weight: 700;
}

.app-chat-view__workspace {
  width: 100%;
  flex: 1;
  margin-top: 14px;
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(0, 560px) minmax(0, 1fr);
  min-height: 0;
  height: auto;
}

.chat-panel {
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.chat-panel__messages {
  flex: 1;
  min-height: 0;
  overflow: auto;
  overscroll-behavior: contain;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chat-panel__history-toolbar {
  display: flex;
  justify-content: center;
  margin-bottom: 4px;
}

.chat-panel__history-button {
  padding-inline: 0;
}

.chat-panel__history-tip {
  color: rgba(15, 23, 42, 0.45);
  font-size: 12px;
}

.chat-panel__history-loading {
  flex: 1;
  display: grid;
  place-items: center;
  min-height: 180px;
}

.chat-message {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.chat-message--assistant {
  justify-content: flex-start;
}

.chat-message--user {
  justify-content: flex-end;
}

.chat-message__avatar {
  flex-shrink: 0;
  margin-top: 2px;
  background: linear-gradient(135deg, #22d3ee, #2563eb);
  font-weight: 600;
}

.chat-message--assistant .chat-message__avatar {
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: #fff;
}

.chat-message--user .chat-message__avatar {
  order: 2;
}

.chat-message__bubble {
  max-width: min(82%, 620px);
  border-radius: 14px;
  padding: 11px 14px;
  line-height: 1.62;
  word-break: break-word;
}

.chat-message__text {
  white-space: normal;
}

.chat-message__text + .chat-message__text {
  margin-top: 8px;
}

.chat-message__text :deep(p) {
  margin: 0 0 8px 0;
}

.chat-message__text :deep(p:last-child) {
  margin-bottom: 0;
}

.chat-message__text :deep(ul),
.chat-message__text :deep(ol) {
  margin: 0 0 8px 0;
  padding-left: 20px;
}

.chat-message__text :deep(ul) {
  list-style-type: disc;
}

.chat-message__text :deep(ol) {
  list-style-type: decimal;
}

.chat-message__text :deep(li) {
  margin-bottom: 4px;
}

.chat-message__text :deep(strong) {
  font-weight: 600;
}

.chat-message__text :deep(code) {
  font-family: SFMono-Regular, Consolas, Liberation Mono, Menlo, monospace;
  background-color: rgba(0, 0, 0, 0.06);
  padding: 2px 4px;
  border-radius: 4px;
  font-size: 90%;
}

.chat-message--user .chat-message__text :deep(code) {
  background-color: rgba(255, 255, 255, 0.2);
}

.chat-message__text :deep(h1),
.chat-message__text :deep(h2),
.chat-message__text :deep(h3),
.chat-message__text :deep(h4),
.chat-message__text :deep(h5),
.chat-message__text :deep(h6) {
  margin: 12px 0 6px 0;
  font-weight: 600;
}

.chat-message__text :deep(h1) { font-size: 1.25em; }
.chat-message__text :deep(h2) { font-size: 1.15em; }
.chat-message__text :deep(h3) { font-size: 1.05em; }
.chat-message__text :deep(h4),
.chat-message__text :deep(h5),
.chat-message__text :deep(h6) { font-size: 1em; }

.chat-code-block {
  margin-top: 8px;
  border-radius: 10px;
  border: 1px solid rgba(148, 163, 184, 0.45);
  overflow: hidden;
  background: #0f172a;
  color: #e2e8f0;
}

.chat-code-block__header {
  padding: 6px 10px;
  background: rgba(148, 163, 184, 0.2);
  color: #bfdbfe;
  font-size: 12px;
  text-transform: lowercase;
  letter-spacing: 0.2px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.3);
}

.chat-code-block__pre {
  margin: 0;
  padding: 10px 12px;
  max-width: 100%;
  overflow: auto;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 13px;
  line-height: 1.58;
}

.chat-code-block :deep(.code-token-keyword) {
  color: #93c5fd;
  font-weight: 600;
}

.chat-code-block :deep(.code-token-string) {
  color: #86efac;
}

.chat-code-block :deep(.code-token-number) {
  color: #fca5a5;
}

.chat-code-block :deep(.code-token-comment) {
  color: #94a3b8;
  font-style: italic;
}

.chat-code-block :deep(.code-token-tag) {
  color: #f9a8d4;
}

.chat-message--assistant .chat-message__bubble {
  background: #f2f8ff;
  color: #0f172a;
}

.chat-message--user .chat-message__bubble {
  background: linear-gradient(132deg, #0ea5e9 0%, #2563eb 100%);
  color: #fff;
}

.chat-message__cursor {
  display: inline-block;
  margin-left: 2px;
  animation: blink 1s infinite;
}

.chat-message__status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 13px;
  color: #1e40af;
  background: rgba(37, 99, 235, 0.05);
  padding: 6px 12px;
  border-radius: 6px;
  border: 1px solid rgba(37, 99, 235, 0.1);
  width: fit-content;
}

.chat-message__status-spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid rgba(30, 64, 175, 0.2);
  border-top-color: #1e40af;
  border-radius: 50%;
  animation: status-spin 0.8s linear infinite;
  flex-shrink: 0;
}

@keyframes status-spin {
  to {
    transform: rotate(360deg);
  }
}

.chat-panel__editor {
  flex-shrink: 0;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
  padding: 14px;
}

.chat-panel__selected-element {
  margin-bottom: 12px;
}

.chat-panel__editor-footer {
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: rgba(15, 23, 42, 0.55);
  font-size: 12px;
}

.preview-panel {
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.preview-panel__header {
  padding: 16px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.preview-panel__header h2 {
  margin: 0;
  font-size: 18px;
  color: #0f172a;
}

.preview-panel__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.preview-panel__body {
  flex: 1;
  min-height: 0;
  position: relative;
  overflow: hidden;
}

.preview-panel__body iframe {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
}

.preview-panel__body--visual-editing iframe {
  cursor: crosshair;
  pointer-events: none;
}

.preview-panel__visual-edit-layer {
  position: absolute;
  inset: 0;
  z-index: 2;
  cursor: crosshair;
  background: rgba(22, 119, 255, 0.03);
  box-shadow: inset 0 0 0 1px rgba(22, 119, 255, 0.35);
  pointer-events: auto;
  touch-action: none;
}

.preview-panel__visual-edit-box {
  position: absolute;
  top: 0;
  left: 0;
  box-sizing: border-box;
  pointer-events: none;
}

.preview-panel__visual-edit-box--hover {
  border: 2px dashed rgba(22, 119, 255, 0.88);
  background: rgba(22, 119, 255, 0.08);
}

.preview-panel__visual-edit-box--selected {
  border: 3px solid rgba(9, 88, 217, 0.98);
  background: rgba(9, 88, 217, 0.1);
}

.preview-placeholder {
  height: 100%;
  display: grid;
  place-items: center;
  color: rgba(15, 23, 42, 0.56);
  font-size: 14px;
  text-align: center;
  padding: 20px;
}

.deploy-modal__actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.app-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.app-detail__block {
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 12px;
  padding: 14px;
}

.app-detail__block h3 {
  margin: 0 0 12px;
  font-size: 16px;
  color: #0f172a;
}

.app-detail__creator {
  display: flex;
  align-items: center;
  gap: 10px;
}

.app-detail__label {
  margin: 0;
  font-size: 12px;
  color: rgba(15, 23, 42, 0.56);
}

.app-detail__value {
  margin: 2px 0 0;
  color: #0f172a;
}

.app-detail__time {
  margin: 12px 0 0;
  color: rgba(15, 23, 42, 0.66);
  font-size: 13px;
}

.app-detail__meta {
  margin: 8px 0 0;
  color: rgba(15, 23, 42, 0.66);
  font-size: 13px;
}

.app-chat-view__loading-mask {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(248, 251, 255, 0.55);
  pointer-events: none;
}

@keyframes blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0;
  }
}

@media (max-width: 1120px) {
  .app-chat-view {
    padding: 12px;
    overflow: auto;
  }

  .app-chat-view__workspace {
    grid-template-columns: minmax(0, 1fr);
    min-height: 580px;
    height: auto;
  }

  .chat-panel {
    min-height: 520px;
  }

  .preview-panel,
  .workspace-panel {
    min-height: 440px;
  }

  .app-chat-view__topbar {
    flex-direction: column;
    align-items: flex-start;
  }
}

/* --- Segmented Tab Control Styles --- */
.workspace-tabs {
  display: inline-flex;
  background: #f1f5f9;
  padding: 3px;
  border-radius: 9px;
  user-select: none;
  border: 1px solid rgba(15, 23, 42, 0.05);
}

.workspace-tab-item {
  padding: 6px 16px;
  font-size: 13px;
  font-weight: 500;
  color: #475569;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.workspace-tab-item.active {
  background: #fff;
  color: #0f172a;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1), 0 1px 2px rgba(0, 0, 0, 0.06);
}

.workspace-tab-item:hover:not(.active) {
  color: #0f172a;
}

/* --- Workspace Layout Styles --- */
.workspace-panel {
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.workspace-panel__header {
  padding: 12px 16px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 57px;
  box-sizing: border-box;
}

.workspace-panel__body {
  flex: 1;
  min-height: 0;
  position: relative;
  overflow: hidden;
}

.preview-content-wrapper {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.preview-content-wrapper .preview-panel__body {
  flex: 1;
}

/* --- Chat Code Stub Styles --- */
.chat-message__code-stub {
  margin-top: 8px;
  padding: 8px 12px;
  background: #f8fafc;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.chat-message--user .chat-message__code-stub {
  background: rgba(255, 255, 255, 0.15);
  border-color: rgba(255, 255, 255, 0.2);
}

.chat-message__code-stub-icon {
  font-size: 16px;
}

.chat-message__code-stub-text {
  color: #334155;
  font-weight: 500;
  flex: 1;
}

.chat-message--user .chat-message__code-stub-text {
  color: #fff;
}

.chat-message__code-stub-btn {
  padding: 0;
  height: auto;
  font-size: 13px;
  font-weight: 600;
  color: #2563eb;
}

.chat-message--user .chat-message__code-stub-btn {
  color: #fff;
  text-decoration: underline;
}

/* --- IDE Code Viewer Styles --- */
.code-viewer-panel {
  display: flex;
  height: 100%;
  min-height: 0;
  background: #fff;
}

.code-viewer-sidebar {
  width: 280px;
  border-right: 1px solid rgba(15, 23, 42, 0.08);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  background: #f8fafc;
}

.code-viewer-sidebar__title {
  padding: 12px 16px;
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  border-bottom: 1px solid rgba(15, 23, 42, 0.05);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.code-viewer-sidebar__tree {
  flex: 1;
  overflow-y: auto;
  padding: 8px 4px;
}

.custom-antd-tree {
  background: transparent !important;
}

.custom-antd-tree :deep(.ant-tree-treenode) {
  padding: 4px 0 !important;
  display: flex !important;
  align-items: center !important;
  width: 100% !important;
}

.custom-antd-tree :deep(.ant-tree-node-content-wrapper) {
  display: flex !important;
  align-items: center !important;
  padding: 6px 8px !important;
  border-radius: 6px !important;
  transition: all 0.15s ease !important;
  flex: 1 !important;
  font-size: 13px !important;
  font-family: SFMono-Regular, Consolas, Liberation Mono, Menlo, monospace !important;
  background-color: transparent !important;
}

.custom-antd-tree :deep(.ant-tree-node-content-wrapper:hover) {
  background-color: #f1f5f9 !important;
}

.custom-antd-tree :deep(.ant-tree-node-selected) {
  background-color: #dbeafe !important;
  color: #1d4ed8 !important;
  font-weight: 500 !important;
}

.tree-node-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tree-node-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.tree-node-text {
  font-size: 13px;
  font-weight: 500;
  line-height: 1.5;
}

.code-viewer-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: #1e1e1e;
}

.code-viewer-header {
  height: 40px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  border-bottom: 1px solid #2d2d2d;
  background: #252526;
}

.code-viewer-path {
  font-family: SFMono-Regular, Consolas, Liberation Mono, Menlo, monospace;
  font-size: 13px;
  color: #cccccc;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.code-viewer-container {
  flex: 1;
  min-height: 0;
  position: relative;
  overflow: auto;
  background: #1e1e1e;
}

.code-viewer-loading {
  height: 100%;
  display: grid;
  place-items: center;
}

.code-viewer-empty {
  height: 100%;
  display: grid;
  place-items: center;
  color: #858585;
  font-size: 14px;
}

.code-viewer-body {
  display: flex;
  padding: 16px;
  min-height: 100%;
  box-sizing: border-box;
}

.code-viewer-line-numbers {
  display: flex;
  flex-direction: column;
  text-align: right;
  padding-right: 16px;
  margin-right: 16px;
  border-right: 1px solid #3c3c3c;
  color: #858585;
  user-select: none;
  font-family: SFMono-Regular, Consolas, Liberation Mono, Menlo, monospace;
  font-size: 13px;
  line-height: 20px;
}

.code-viewer-pre {
  margin: 0;
  flex: 1;
  overflow-x: auto;
  white-space: pre;
  font-family: SFMono-Regular, Consolas, Liberation Mono, Menlo, monospace;
  font-size: 13px;
  line-height: 20px;
  color: #d4d4d4;
}

/* IDE Dark Theme Highlight Tokens */
:deep(.code-token-keyword) {
  color: #569cd6 !important;
  font-weight: 600 !important;
}

:deep(.code-token-string) {
  color: #ce9178 !important;
}

:deep(.code-token-number) {
  color: #b5cea8 !important;
}

:deep(.code-token-comment) {
  color: #6a9955 !important;
  font-style: italic !important;
}

:deep(.code-token-tag) {
  color: #569cd6 !important;
}
</style>
