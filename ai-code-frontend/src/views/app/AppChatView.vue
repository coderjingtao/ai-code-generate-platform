<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
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
import AppFileTree from '@/components/AppFileTree.vue'
import StateView from '@/components/StateView.vue'
import { useAsyncState } from '@/composables/useAsyncState'
import { useAppGenerationStream } from '@/composables/useAppGenerationStream'

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  streaming?: boolean
  createTime?: string
  // 执行过程仅用单行状态实时展示（带动画），不进入聊天历史
  statusText?: string
}

type WithStringAppId<T> = Omit<T, 'appId'> & { appId: string }
type WithStringId<T> = Omit<T, 'id'> & { id: string }

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
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
let previewSrcDocLoadToken = 0

// --- Workspace Tab & Code Viewer State ---
const activeTab = ref<'preview' | 'code'>('preview')
const files = ref<string[]>([])
const selectedPath = ref<string>('')
const activePath = ref<string>('')
const selectedFileContent = ref<string>('')
const loadingFiles = ref(false)

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
  const lang = getLanguageFromPath(selectedPath.value)
  return applyCodeHighlight(selectedFileContent.value, lang)
})

const fileContentState = useAsyncState(async (filePath: string) => {
  const codeGenType = appInfo.value?.codeGenType
  const url = `/api/static/${codeGenType}_${appId.value}/${filePath}`
  const res = await fetch(`${url}?t=${Date.now()}`)
  if (!res.ok) {
    throw new Error(t('appChat.errors.loadFileFailed', { status: res.statusText }))
  }
  return res.text()
}, t('appChat.errors.loadFileFailedRetry'))
const loadingContent = fileContentState.loading
const fileContentError = fileContentState.error

const fetchFileContent = async (filePath: string) => {
  if (!appId.value || !appInfo.value?.codeGenType) return
  const text = await fileContentState.run(filePath)
  selectedFileContent.value = text ?? ''
}

const loadProjectFiles = async () => {
  if (!appId.value) return
  loadingFiles.value = true
  try {
    const res = await listAppFiles(withStringAppId<API.listAppFilesParams>({ appId: appId.value }))
    if (res.data?.code === 0 && res.data?.data) {
      files.value = res.data.data
      if (files.value.length > 0) {
        const currentFile = selectedPath.value
        if (!currentFile || !files.value.includes(currentFile)) {
          const defaultFile =
            files.value.find((f) => f.endsWith('index.html')) || files.value[0] || ''
          selectedPath.value = defaultFile
          void fetchFileContent(defaultFile)
        } else {
          void fetchFileContent(currentFile)
        }
      } else {
        selectedPath.value = ''
        selectedFileContent.value = ''
      }
    }
  } catch (err) {
    console.error('Failed to load project files:', err)
  } finally {
    loadingFiles.value = false
  }
}

const onFileSelect = (path: string) => {
  selectedPath.value = path
  void fetchFileContent(path)
}

const scrollCodeViewerToBottom = async () => {
  await nextTick()
  const box = codeViewerContainerRef.value
  if (box) {
    box.scrollTop = box.scrollHeight
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

const appName = computed(
  () => appInfo.value?.appName || t('appChat.title.defaultAppName', { id: appId.value }),
)
const baseApiUrl = computed(() => String(request.defaults.baseURL || '/api').replace(/\/$/, ''))
const loginUserName = computed(() => loginUserStore.loginUser?.userName || t('appChat.title.me'))
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
  return t('appChat.title.anonymousUser')
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
  return t('appChat.visualEdit.selectedElement', { name: elementName })
})
const selectedElementDescription = computed(() => {
  const elementInfo = selectedElementInfo.value
  if (!elementInfo) {
    return ''
  }

  return elementInfo.text
    ? t('appChat.visualEdit.selectorWithText', {
        selector: elementInfo.selector,
        text: elementInfo.text,
      })
    : t('appChat.visualEdit.selector', { selector: elementInfo.selector })
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
  stream.close()
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
    message.warning(t('appChat.visualEdit.previewCannotEnter'))
    visualEditMode.value = false
  }
}

const toggleVisualEditMode = () => {
  if (!previewReady.value || !previewUrl.value) {
    message.warning(t('appChat.visualEdit.enterAfterPreview'))
    return
  }

  if (!visualEditMode.value) {
    const enabled = visualIframeEditorRef.value?.enable() || setupVisualIframeEditor()
    if (!enabled) {
      message.warning(t('appChat.visualEdit.previewCannotEnter'))
      return
    }
    visualEditMode.value = true
    message.info(t('appChat.visualEdit.entered'))
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

    message.error(res.data.message || t('appChat.messages.loadHistoryFailed'))
  } catch {
    message.error(
      loadMore
        ? t('appChat.messages.loadMoreHistoryFailedRetry')
        : t('appChat.messages.loadHistoryFailedRetry'),
    )
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
    message.error(t('appChat.messages.invalidAppId'))
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
    message.error(res.data.message || t('appChat.messages.loadAppFailed'))
  } catch {
    message.error(t('appChat.messages.loadAppFailedRetry'))
  } finally {
    loadingApp.value = false
  }
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

// 当前正在流式生成的助手消息 id，事件回调据此定位目标气泡
let currentAssistantId = ''

const currentAssistant = (): ChatMessage | undefined =>
  messages.value.find((item) => item.id === currentAssistantId && item.streaming)

// 执行过程统一写入单行状态文案（带动画），不累积、不入库
const setStatus = (text: string) => {
  const target = currentAssistant()
  if (target) {
    target.statusText = text
  }
}

const finalizeGeneration = async (failed = false) => {
  if (!sending.value) {
    return
  }
  sending.value = false
  const target = messages.value.find((item) => item.id === currentAssistantId)
  if (target) {
    target.streaming = false
    target.statusText = undefined
    if (!target.content.trim()) {
      target.content = failed ? t('appChat.status.generationFailed') : t('appChat.status.done')
    }
  }
  activePath.value = ''
  await loadAppInfo()
  refreshPreview()
  await loadProjectFiles()
  if (!failed) {
    activeTab.value = 'preview'
  }
  await scrollToBottom()

  // 延迟 500ms 重新加载干净的历史记录，用数据库中的对话替换本地包含执行过程的临时消息
  setTimeout(async () => {
    messages.value = messages.value.filter((item) => isHistoryMessage(item))
    await loadChatHistory()
    await scrollToBottom()
  }, 500)
}

// V2 流式事件：每个具名事件直接驱动聊天区域 / 代码内容 / 文件树 / 预览状态
const stream = useAppGenerationStream({
  onAssistantText: (text) => {
    const target = currentAssistant()
    if (target) {
      target.content += text
      void scrollToBottom()
    }
  },
  onToolCall: (text) => {
    setStatus(text)
  },
  onFileStart: (path) => {
    if (!files.value.includes(path)) {
      files.value.push(path)
    }
    selectedPath.value = path
    // 重置当前查看缓冲，便于后续 file_delta 增量追加（真正的流式展示）
    selectedFileContent.value = ''
    activePath.value = path
    activeTab.value = 'code'
    setStatus(t('appChat.status.generatingFile', { path }))
  },
  onFileDelta: (path, content, overwrite) => {
    selectedPath.value = path
    selectedFileContent.value = overwrite ? content : selectedFileContent.value + content
    void scrollCodeViewerToBottom()
  },
  onFileDone: (path) => {
    if (activePath.value === path) {
      activePath.value = ''
    }
    setStatus(t('appChat.status.generatedFile', { path }))
  },
  onFileDelete: (path) => {
    files.value = files.value.filter((file) => file !== path)
    if (selectedPath.value === path) {
      selectedPath.value = ''
      selectedFileContent.value = ''
    }
    setStatus(t('appChat.status.deletedFile', { path }))
  },
  onBuildStatus: (_status, statusMessage) => {
    setStatus(statusMessage)
  },
  onPreviewReady: () => {
    activePath.value = ''
    refreshPreview()
    activeTab.value = 'preview'
  },
  onError: (errorMessage) => {
    const target = currentAssistant()
    if (target && !target.content.trim()) {
      target.content = errorMessage
    }
    message.error(errorMessage)
  },
  onFinalize: (failed) => {
    void finalizeGeneration(failed)
  },
})

const sendPrompt = async (promptInput?: string) => {
  const prompt = (promptInput ?? inputPrompt.value).trim()
  if (!prompt) {
    message.warning(t('appChat.messages.enterMessage'))
    return
  }

  if (!appId.value) {
    message.error(t('appChat.messages.invalidAppId'))
    return
  }

  if (sending.value) {
    message.info(t('appChat.messages.generatingPrevious'))
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
    statusText: t('appChat.status.generating'),
  }
  currentAssistantId = assistantMessage.id

  messages.value.push(userMessage, assistantMessage)
  inputPrompt.value = ''
  resetVisualEditState()
  sending.value = true
  previewReady.value = false
  previewUrl.value = ''
  previewSrcDoc.value = ''
  previewSrcDocLoadToken += 1
  destroyVisualIframeEditor()
  activePath.value = ''
  await scrollToBottom()

  stream.start({
    baseApiUrl: baseApiUrl.value,
    appId: appId.value,
    userPrompt: finalPrompt,
  })
}

const handleDeploy = async () => {
  if (!appId.value) {
    message.error(t('appChat.messages.invalidAppId'))
    return
  }

  deploying.value = true
  try {
    const res = await deployApp(withStringAppId<API.AppDeployRequest>({ appId: appId.value }))
    if (res.data.code === 0 && res.data.data) {
      deployedUrl.value = res.data.data
      deployModalOpen.value = true
      activeTab.value = 'preview'
      message.success(t('appChat.deploy.success'))
      return
    }
    message.error(res.data.message || t('appChat.deploy.failed'))
  } catch {
    message.error(t('appChat.deploy.failedRetry'))
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
    message.success(t('appChat.deploy.urlCopied'))
  } catch {
    message.error(t('appChat.deploy.copyFailed'))
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
    message.error(t('appChat.messages.invalidAppId'))
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
    message.success(t('appChat.messages.downloadStarted'))
  } catch {
    message.error(t('appChat.messages.downloadFailedRetry'))
  } finally {
    downloadingCode.value = false
  }
}

const openAppDetail = () => {
  appDetailOpen.value = true
}

const goToAppEdit = () => {
  if (!appId.value) {
    message.error(t('appChat.messages.invalidAppId'))
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
    message.error(t('appChat.messages.invalidAppId'))
    return
  }

  deletingApp.value = true
  try {
    const useAdminDelete = adminModeEnabled.value || (isAdmin.value && !isOwner.value)
    const res = useAdminDelete
      ? await deleteAppByAdmin({ id: currentId })
      : await deleteMyApp({ id: currentId })
    if (res.data.code === 0) {
      message.success(t('appChat.messages.deleteSuccess'))
      appDetailOpen.value = false
      await router.replace('/')
      return
    }
    message.error(res.data.message || t('appChat.messages.deleteFailed'))
  } catch {
    message.error(t('appChat.messages.deleteFailedRetry'))
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
          <span>{{ $t('appChat.meta.appId', { id: appId }) }}</span>
          <span class="app-chat-view__meta-separator">·</span>
          <span>
            {{ $t('appChat.meta.codeGenType')
            }}<strong class="app-chat-view__code-type">{{ appInfo?.codeGenType || '-' }}</strong>
          </span>
        </p>
      </div>
      <a-space>
        <a-button @click="router.push('/')">{{ $t('appChat.buttons.backHome') }}</a-button>
        <a-button @click="openAppDetail">{{ $t('appChat.buttons.appDetail') }}</a-button>
        <a-button :loading="downloadingCode" @click="handleDownloadCode">{{
          $t('appChat.buttons.downloadCode')
        }}</a-button>
        <a-button type="primary" :loading="deploying" @click="handleDeploy">{{
          $t('appChat.buttons.deploy')
        }}</a-button>
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
              {{ $t('appChat.buttons.loadMore') }}
            </a-button>
            <span v-else class="chat-panel__history-tip">
              {{ $t('appChat.history.allLoaded', { count: totalHistory || historyLoadedCount }) }}
            </span>
          </div>

          <div
            v-for="item in messages"
            :key="item.id"
            :class="['chat-message', `chat-message--${item.role}`]"
          >
            <a-avatar :size="36" :src="getMessageAvatar(item.role)" class="chat-message__avatar">
              {{ getMessageAvatarFallback(item.role) }}
            </a-avatar>
            <div class="chat-message__bubble">
              <!-- 用户消息：纯文本；助手消息：仅渲染计划与简短说明（代码不进聊天区） -->
              <div
                v-if="item.role === 'user'"
                class="chat-message__text"
                v-html="formatPlainTextToHtml(item.content)"
              />
              <MarkdownRenderer
                v-else-if="item.content"
                class="chat-message__text"
                :content="item.content"
              />

              <!-- 执行过程：仅单行实时状态（带动画），不累积、不入库 -->
              <div
                v-if="item.role === 'assistant' && item.streaming && item.statusText"
                class="chat-message__status"
              >
                <div class="chat-message__status-ai-loader">
                  <span class="ai-dot"></span>
                  <span class="ai-dot"></span>
                  <span class="ai-dot"></span>
                </div>
                <span class="chat-message__status-text">{{ item.statusText }}</span>
              </div>
              <span v-if="item.streaming" class="chat-message__cursor">|</span>
            </div>
          </div>

          <StateView
            :loading="loadingHistory && messages.length === 0"
            :empty="!loadingHistory && messages.length === 0"
            :empty-text="$t('appChat.empty.chat')"
            :retryable="false"
          />
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
            :placeholder="$t('appChat.placeholders.input')"
            @keydown.enter.exact.prevent="sendCurrentPrompt"
          />
          <div class="chat-panel__editor-footer">
            <span>{{ $t('appChat.editor.hint') }}</span>
            <a-button type="primary" :loading="sending" @click="sendCurrentPrompt">{{
              $t('appChat.buttons.send')
            }}</a-button>
          </div>
        </div>
      </section>

      <aside class="workspace-panel">
        <div class="workspace-panel__header">
          <div class="workspace-tabs">
            <div
              class="workspace-tab-item"
              :class="{ active: activeTab === 'preview' }"
              @click="activeTab = 'preview'"
            >
              {{ $t('appChat.tabs.preview') }}
            </div>
            <div
              class="workspace-tab-item"
              :class="{ active: activeTab === 'code' }"
              @click="activeTab = 'code'"
            >
              {{ $t('appChat.tabs.code') }}
            </div>
          </div>

          <div
            v-if="activeTab === 'preview' && previewReady && previewUrl"
            class="preview-panel__actions"
          >
            <a-button
              size="small"
              :type="visualEditMode ? 'primary' : 'default'"
              :disabled="sending"
              @click="toggleVisualEditMode"
            >
              {{ visualEditMode ? $t('appChat.buttons.exitEdit') : $t('appChat.buttons.editMode') }}
            </a-button>
            <a :href="previewUrl" target="_blank" rel="noopener noreferrer">
              {{ $t('appChat.buttons.openInNewWindow') }}
            </a>
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
                <p v-if="sending">{{ $t('appChat.preview.generating') }}</p>
                <p v-else>{{ $t('appChat.preview.idle') }}</p>
              </div>
            </div>
          </div>

          <!-- Code Tab Content -->
          <div v-if="activeTab === 'code'" class="code-viewer-panel">
            <aside class="code-viewer-sidebar">
              <div class="code-viewer-sidebar__title">{{ $t('appChat.code.fileTree') }}</div>
              <AppFileTree
                :files="files"
                :selected-path="selectedPath"
                :active-path="activePath"
                @select="onFileSelect"
              />
            </aside>
            <main class="code-viewer-main">
              <header class="code-viewer-header">
                <span class="code-viewer-path">{{
                  selectedPath || $t('appChat.empty.noFileSelected')
                }}</span>
              </header>
              <div ref="codeViewerContainerRef" class="code-viewer-container">
                <StateView :loading="loadingContent" :error="fileContentError" :retryable="false">
                  <div v-if="selectedFileContent" class="code-viewer-body">
                    <div class="code-viewer-line-numbers">
                      <span v-for="n in lineCount" :key="n">{{ n }}</span>
                    </div>
                    <pre class="code-viewer-pre"><code v-html="highlightedCode"></code></pre>
                  </div>
                  <div v-else class="code-viewer-empty">
                    <p>{{ $t('appChat.empty.noCode') }}</p>
                  </div>
                </StateView>
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
      :title="$t('appChat.deploy.modalTitle')"
      :footer="null"
      :mask-closable="true"
      width="620px"
    >
      <p>{{ $t('appChat.deploy.accessUrl') }}</p>
      <a-input :value="deployedUrl" readonly />
      <div class="deploy-modal__actions">
        <a-space>
          <a-button @click="copyDeployUrl">{{ $t('appChat.deploy.copyUrl') }}</a-button>
          <a-button type="primary" @click="openDeployUrl">{{
            $t('appChat.deploy.openSite')
          }}</a-button>
        </a-space>
      </div>
    </a-modal>

    <a-modal
      v-model:open="appDetailOpen"
      :title="$t('appChat.detail.title')"
      :footer="null"
      :mask-closable="true"
      width="560px"
    >
      <section class="app-detail">
        <div class="app-detail__block">
          <h3>{{ $t('appChat.detail.basicInfo') }}</h3>
          <div class="app-detail__creator">
            <a-avatar :size="44" :src="appCreatorAvatar">{{
              appCreatorName.slice(0, 1).toUpperCase()
            }}</a-avatar>
            <div>
              <p class="app-detail__label">{{ $t('appChat.detail.creator') }}</p>
              <p class="app-detail__value">{{ appCreatorName }}</p>
            </div>
          </div>
          <p class="app-detail__time">
            {{ $t('appChat.detail.createTime', { time: appInfo?.createTime || '-' }) }}
          </p>
          <p class="app-detail__meta">
            {{ $t('appChat.detail.codeGenType')
            }}<strong class="app-chat-view__code-type">{{ appInfo?.codeGenType || '-' }}</strong>
          </p>
        </div>

        <div v-if="canManageApp" class="app-detail__block">
          <h3>{{ $t('appChat.detail.operations') }}</h3>
          <a-space>
            <a-button @click="goToAppEdit">{{ $t('appChat.detail.modify') }}</a-button>
            <a-popconfirm
              :title="$t('appChat.detail.confirmDelete')"
              :ok-text="$t('common.actions.confirm')"
              :cancel-text="$t('common.actions.cancel')"
              @confirm="handleDeleteApp"
            >
              <a-button danger :loading="deletingApp">{{ $t('common.actions.delete') }}</a-button>
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
  background: var(--ac-bg-page);
  padding: 18px 22px 22px;
}

.app-chat-view__topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  background: var(--ac-surface-elevated);
  padding: 16px 18px;
  flex-shrink: 0;
}

.app-chat-view__title {
  margin: 0;
  color: var(--ac-text);
  font-size: 24px;
}

.app-chat-view__meta {
  margin: 6px 0 0;
  color: var(--ac-text-muted);
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
  border: 1px solid var(--ac-primary-border);
  border-radius: var(--ac-radius-pill);
  background: var(--ac-primary-soft);
  color: var(--ac-primary-strong);
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
  background: var(--ac-surface);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
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
  position: relative;
  padding-left: 46px;
  padding-right: 46px;
  min-height: 36px;
  /* 防止 flex 列布局在内容超出时压缩消息项导致气泡相互覆盖（多行消息尤为明显） */
  flex-shrink: 0;
}

.chat-message__avatar {
  position: absolute;
  top: 2px;
  width: 36px;
  height: 36px;
  min-width: 36px;
  flex-shrink: 0;
  background: linear-gradient(135deg, #22d3ee, #2563eb);
  font-weight: 600;
}

.chat-message--assistant .chat-message__avatar {
  left: 0;
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: #fff;
}

.chat-message--user .chat-message__avatar {
  right: 0;
}

.chat-message__bubble {
  width: 100%;
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
  font-family:
    SFMono-Regular,
    Consolas,
    Liberation Mono,
    Menlo,
    monospace;
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

.chat-message__text :deep(h1) {
  font-size: 1.25em;
}
.chat-message__text :deep(h2) {
  font-size: 1.15em;
}
.chat-message__text :deep(h3) {
  font-size: 1.05em;
}
.chat-message__text :deep(h4),
.chat-message__text :deep(h5),
.chat-message__text :deep(h6) {
  font-size: 1em;
}

.chat-message--assistant .chat-message__bubble {
  background: var(--ac-surface-muted);
  color: var(--ac-text);
}

.chat-message--user .chat-message__bubble {
  background: linear-gradient(132deg, #0ea5e9 0%, var(--ac-primary) 100%);
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
  margin-top: 10px;
  font-size: 13px;
  color: var(--ac-text-muted);
  font-weight: 500;
  width: fit-content;
}

.chat-message__status-ai-loader {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 14px;
}

.chat-message__status-ai-loader .ai-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  animation: ai-pulse 1.4s ease-in-out infinite both;
}

.chat-message__status-ai-loader .ai-dot:nth-child(1) {
  animation-delay: -0.32s;
  background-color: var(--ac-primary);
}

.chat-message__status-ai-loader .ai-dot:nth-child(2) {
  animation-delay: -0.16s;
  background-color: var(--ac-gold);
}

.chat-message__status-ai-loader .ai-dot:nth-child(3) {
  background-color: var(--ac-accent);
}

@keyframes ai-pulse {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.4;
  }
  40% {
    transform: scale(1.2);
    opacity: 1;
    filter: drop-shadow(0 0 4px currentColor);
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
  background: var(--ac-surface);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
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
  background: var(--ac-surface-muted);
  padding: 3px;
  border-radius: 9px;
  user-select: none;
  border: 1px solid rgba(15, 23, 42, 0.05);
}

.workspace-tab-item {
  padding: 6px 16px;
  font-size: 13px;
  font-weight: 500;
  color: var(--ac-text-muted);
  border-radius: var(--ac-radius-sm);
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.workspace-tab-item.active {
  background: var(--ac-surface);
  color: var(--ac-primary);
  font-weight: 600;
  box-shadow: var(--ac-shadow-sm);
}

.workspace-tab-item:hover:not(.active) {
  color: var(--ac-text);
}

/* --- Workspace Layout Styles --- */
.workspace-panel {
  background: var(--ac-surface);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.workspace-panel__header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--ac-border);
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

/* --- IDE Code Viewer Styles --- */
.code-viewer-panel {
  display: flex;
  height: 100%;
  min-height: 0;
  background: var(--ac-surface);
}

.code-viewer-sidebar {
  width: 280px;
  border-right: 1px solid var(--ac-border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  background: var(--ac-surface-muted);
  min-height: 0;
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

.code-viewer-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--ac-code-bg);
}

.code-viewer-header {
  height: 40px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  border-bottom: 1px solid var(--ac-code-header-border);
  background: var(--ac-code-header-bg);
}

.code-viewer-path {
  font-family:
    SFMono-Regular,
    Consolas,
    Liberation Mono,
    Menlo,
    monospace;
  font-size: 13px;
  color: var(--ac-code-path);
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
  background: var(--ac-code-bg);
}

.code-viewer-empty {
  height: 100%;
  display: grid;
  place-items: center;
  color: var(--ac-code-gutter);
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
  border-right: 1px solid var(--ac-code-gutter-border);
  color: var(--ac-code-gutter);
  user-select: none;
  font-family:
    SFMono-Regular,
    Consolas,
    Liberation Mono,
    Menlo,
    monospace;
  font-size: 13px;
  line-height: 20px;
}

.code-viewer-pre {
  margin: 0;
  flex: 1;
  overflow-x: auto;
  white-space: pre;
  font-family:
    SFMono-Regular,
    Consolas,
    Liberation Mono,
    Menlo,
    monospace;
  font-size: 13px;
  line-height: 20px;
  color: var(--ac-code-text);
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
