<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'

import request from '@/request'
import aiAvatarUrl from '@/assets/aiAvatar.png'
import {
  deleteAppByAdmin,
  deleteMyApp,
  deployApp,
  getAppByIdForAdmin,
  getMyAppById,
} from '@/api/appController'
import { listAppChatHistory } from '@/api/chatHistoryController'
import { useLoginUserStore } from '@/stores/loginUserStore'
import { getStaticPreviewUrl } from '@/config/env'

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  streaming?: boolean
  createTime?: string
}

interface RenderSegment {
  type: 'text' | 'code'
  html: string
  language?: string
}

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const HISTORY_PAGE_SIZE = 10

const appInfo = ref<API.AppVO>()
const loadingApp = ref(false)
const sending = ref(false)
const deploying = ref(false)
const previewReady = ref(false)
const previewUrl = ref('')
const inputPrompt = ref('')
const messages = ref<ChatMessage[]>([])
const messageContainerRef = ref<HTMLElement>()
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
const CHAT_STREAM_PATH = '/app/chat/gen/code'

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

const getMessageAvatar = (role: ChatMessage['role']) => {
  return role === 'assistant' ? aiAvatarUrl : loginUserAvatar.value
}

const getMessageAvatarFallback = (role: ChatMessage['role']) => {
  return role === 'assistant' ? 'AI' : loginUserName.value.slice(0, 1).toUpperCase()
}

const isHistoryMessage = (messageItem: ChatMessage) => {
  return messageItem.id.startsWith('history-')
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
    const res = await listAppChatHistory({
      appId: currentAppId,
      pageSize: HISTORY_PAGE_SIZE,
      lastCreateTime: loadMore ? historyCursor.value : undefined,
    })

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

const refreshPreview = (codeGenType?: string) => {
  const nextUrl = buildPreviewUrl(codeGenType)
  previewUrl.value = nextUrl
  previewReady.value = Boolean(nextUrl)
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
      ? await getAppByIdForAdmin({ id: appId.value })
      : await getMyAppById({ id: appId.value })
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
        return record[key] as string
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

const applyCodeHighlight = (code: string) => {
  let html = escapeHtml(code)
  const tokens: string[] = []

  const token = (value: string, className: string) => {
    const key = `___TOKEN_${tokens.length}___`
    tokens.push(`<span class="${className}">${value}</span>`)
    return key
  }

  html = html.replace(/(&lt;!--[\s\S]*?--&gt;)/g, (match) => token(match, 'code-token-comment'))
  html = html.replace(/(\/\*[\s\S]*?\*\/)/g, (match) => token(match, 'code-token-comment'))
  html = html.replace(/(\/\/[^\n]*)/g, (match) => token(match, 'code-token-comment'))
  html = html.replace(/(`[^`]*`|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')/g, (match) =>
    token(match, 'code-token-string'),
  )
  html = html.replace(/\b(\d+(?:\.\d+)?)\b/g, (match) => token(match, 'code-token-number'))
  html = html.replace(
    /\b(const|let|var|function|return|if|else|for|while|switch|case|break|continue|import|from|export|default|class|new|try|catch|finally|async|await|public|private|protected|interface|type|extends|implements|true|false|null|undefined|throw)\b/g,
    (match) => token(match, 'code-token-keyword'),
  )
  html = html.replace(/(&lt;\/?[a-zA-Z][^&]*?&gt;)/g, (match) => token(match, 'code-token-tag'))

  return html.replace(/___TOKEN_(\d+)___/g, (_match, index: string) => {
    return tokens[Number(index)] || ''
  })
}

const parseMessageSegments = (content: string): RenderSegment[] => {
  if (!content) {
    return [{ type: 'text', html: '' }]
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
        html: formatPlainTextToHtml(textPart),
      })
    }

    segments.push({
      type: 'code',
      language: match[1]?.trim() || undefined,
      html: applyCodeHighlight(match[2] || ''),
    })
    lastIndex = match.index + match[0].length
  }

  if (lastIndex < content.length) {
    segments.push({
      type: 'text',
      html: formatPlainTextToHtml(content.slice(lastIndex)),
    })
  }

  return segments.length ? segments : [{ type: 'text', html: formatPlainTextToHtml(content) }]
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
    const hasCodeSegment = parsedSegments.some((segment) => segment.type === 'code')
    if (!hasCodeSegment && looksLikeCode(messageItem.content)) {
      return [
        {
          type: 'code',
          html: applyCodeHighlight(messageItem.content),
          language: 'code',
        },
      ]
    }
    return parsedSegments
  }
  return [{ type: 'text', html: formatPlainTextToHtml(messageItem.content) }]
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
  sending.value = true
  previewReady.value = false
  previewUrl.value = ''
  await scrollToBottom()

  const assistantIndex = messages.value.length - 1
  const params = new URLSearchParams({
    appId: appId.value,
    userPrompt: prompt,
  })
  const streamUrl = `${baseApiUrl.value}${CHAT_STREAM_PATH}?${params.toString()}`
  const source = new EventSource(streamUrl, { withCredentials: true })
  eventSourceRef.value = source

  let finished = false
  let hasAnyChunk = false
  let hasStreamEvent = false
  let hasDoneEvent = false

  const finalize = async (failed = false) => {
    if (finished) {
      return
    }
    finished = true
    closeStream()
    sending.value = false
    const target = messages.value[assistantIndex]
    if (target) {
      target.streaming = false
      target.content = target.content.trim() || (failed ? '生成失败，请稍后重试。' : '已完成。')
    }
    await loadAppInfo()
    refreshPreview()
    await scrollToBottom()
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

    const chunkText = extractTextFromPayload(payload)
    if (chunkText) {
      hasAnyChunk = true
      const target = messages.value[assistantIndex]
      if (target) {
        target.content += chunkText
        void scrollToBottom()
      }
    }
  }

  source.addEventListener('done', () => {
    hasStreamEvent = true
    hasDoneEvent = true
    void finalize(false)
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
    const res = await deployApp({ appId: appId.value })
    if (res.data.code === 0 && res.data.data) {
      deployedUrl.value = res.data.data
      deployModalOpen.value = true
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
})
</script>

<template>
  <section class="app-chat-view">
    <header class="app-chat-view__topbar">
      <div>
        <h1 class="app-chat-view__title">{{ appName }}</h1>
        <p class="app-chat-view__meta">
          应用 ID：{{ appId }} · 生成类型：{{ appInfo?.codeGenType || '-' }}
        </p>
      </div>
      <a-space>
        <a-button @click="router.push('/')">返回首页</a-button>
        <a-button @click="openAppDetail">应用详情</a-button>
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
                  v-if="segment.type === 'text'"
                  class="chat-message__text"
                  v-html="segment.html"
                />
                <div v-else class="chat-code-block">
                  <div class="chat-code-block__header">
                    {{ segment.language || 'code' }}
                  </div>
                  <pre class="chat-code-block__pre"><code v-html="segment.html"></code></pre>
                </div>
              </template>
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

      <aside class="preview-panel">
        <div class="preview-panel__header">
          <h2>网页预览</h2>
          <a
            :href="previewUrl"
            target="_blank"
            rel="noopener noreferrer"
            v-if="previewReady && previewUrl"
          >
            新窗口打开
          </a>
        </div>
        <div class="preview-panel__body">
          <iframe v-if="previewReady && previewUrl" :src="previewUrl" title="preview" />
          <div v-else class="preview-placeholder">
            <p v-if="sending">代码生成中，完成后自动展示预览...</p>
            <p v-else>发送消息后，生成完成会在这里展示网站效果。</p>
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
}

.app-chat-view__workspace {
  width: 100%;
  flex: 1;
  margin-top: 14px;
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(0, 1.18fr) minmax(0, 1fr);
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

.chat-panel__editor {
  flex-shrink: 0;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
  padding: 14px;
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

.preview-panel__body {
  flex: 1;
  min-height: 0;
}

.preview-panel__body iframe {
  width: 100%;
  height: 100%;
  border: 0;
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

  .preview-panel {
    min-height: 440px;
  }

  .app-chat-view__topbar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
