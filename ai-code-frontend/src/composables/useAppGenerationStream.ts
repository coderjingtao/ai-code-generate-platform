/**
 * 应用生成 V2 流式事件（/app/chat/gen/code/v2）。
 *
 * 后端通过具名 SSE 事件（ServerSentEvent.event(type)）推送，每个事件的 data 是
 * 序列化后的 AppGenerationMessage：{ type, appId, path, content, overwrite, status, message, timestamp }。
 * 本组合式函数负责 EventSource 生命周期、按事件类型分发，以及结束 / 出错的幂等收口，
 * 把视图更新交给调用方传入的 handlers，避免在视图里手写脆弱的文本解析。
 */

import i18n, { currentLang } from '@/locales'

/** 单条生成事件的数据结构（与后端 AppGenerationMessage 对应） */
export interface AppGenerationEvent {
  type?: string
  appId?: number
  path?: string
  content?: string
  overwrite?: boolean
  status?: string
  message?: string
  timestamp?: number
}

export interface AppGenerationStreamHandlers {
  /** AI 文本回复分片（计划 / 简短说明），需累加 */
  onAssistantText?: (text: string) => void
  /** 工具调用提示（执行过程） */
  onToolCall?: (text: string) => void
  /** 开始生成某个文件 */
  onFileStart?: (path: string) => void
  /**
   * 文件内容更新。overwrite=true 时 content 为完整内容（替换）；
   * overwrite=false 时 content 为增量片段（追加），用于真正的流式展示。
   */
  onFileDelta?: (path: string, content: string, overwrite: boolean) => void
  /** 文件生成完成 */
  onFileDone?: (path: string) => void
  /** 文件删除 */
  onFileDelete?: (path: string) => void
  /** 构建状态：building / success / error */
  onBuildStatus?: (status: string, message: string) => void
  /** 预览已可用 */
  onPreviewReady?: (message: string) => void
  /** 生成失败 */
  onError?: (message: string) => void
  /** 全部完成或流结束（无论成功失败，最终都会触发一次，failed 标记是否失败） */
  onFinalize?: (failed: boolean) => void
}

export interface StartStreamOptions {
  baseApiUrl: string
  appId: string
  userPrompt: string
}

const STREAM_PATH = '/app/chat/gen/code/v2'

export function useAppGenerationStream(handlers: AppGenerationStreamHandlers) {
  let source: EventSource | null = null
  let finished = false
  // 是否收到过任何事件 / done，用于区分「正常结束触发的 onerror」与「真正的连接失败」
  let hasAnyEvent = false
  let hasDoneEvent = false

  const close = () => {
    if (source) {
      source.close()
      source = null
    }
  }

  const finalize = (failed = false) => {
    if (finished) {
      return
    }
    finished = true
    close()
    handlers.onFinalize?.(failed)
  }

  const parseEvent = (event: MessageEvent): AppGenerationEvent | null => {
    const raw = `${event.data ?? ''}`.trim()
    if (!raw) {
      return null
    }
    try {
      return JSON.parse(raw) as AppGenerationEvent
    } catch {
      // 非 JSON（极少数兜底）：当作纯文本内容
      return { content: raw }
    }
  }

  const start = ({ baseApiUrl, appId, userPrompt }: StartStreamOptions) => {
    close()
    finished = false
    hasAnyEvent = false
    hasDoneEvent = false

    // EventSource 无法设置自定义请求头，语言通过查询参数传递给后端
    const params = new URLSearchParams({ appId, userPrompt, lang: currentLang() })
    const url = `${baseApiUrl}${STREAM_PATH}?${params.toString()}`
    source = new EventSource(url, { withCredentials: true })

    const on = (eventName: string, fn: (payload: AppGenerationEvent) => void) => {
      source?.addEventListener(eventName, (event: MessageEvent) => {
        hasAnyEvent = true
        const payload = parseEvent(event)
        if (payload) {
          fn(payload)
        }
      })
    }

    on('assistant_message', (p) => {
      if (p.content) {
        handlers.onAssistantText?.(p.content)
      }
    })
    on('tool_call', (p) => {
      const text = p.message || p.content || ''
      if (text) {
        handlers.onToolCall?.(text)
      }
    })
    on('file_start', (p) => {
      if (p.path) {
        handlers.onFileStart?.(p.path)
      }
    })
    on('file_delta', (p) => {
      if (p.path) {
        handlers.onFileDelta?.(p.path, p.content ?? '', p.overwrite !== false)
      }
    })
    on('file_done', (p) => {
      if (p.path) {
        handlers.onFileDone?.(p.path)
      }
    })
    on('file_delete', (p) => {
      if (p.path) {
        handlers.onFileDelete?.(p.path)
      }
    })
    on('build_status', (p) => {
      handlers.onBuildStatus?.(p.status || '', p.message || p.content || '')
    })
    on('preview_ready', (p) => {
      handlers.onPreviewReady?.(p.message || p.content || i18n.global.t('common.gen.previewUpdated'))
    })
    on('generation_error', (p) => {
      handlers.onError?.(p.message || p.content || i18n.global.t('common.gen.failed'))
      finalize(true)
    })

    source.addEventListener('done', () => {
      hasAnyEvent = true
      hasDoneEvent = true
      finalize(false)
    })

    source.onerror = () => {
      if (finished) {
        return
      }
      // 服务端流正常结束时浏览器也会触发 onerror（连接关闭），此处不应误判失败。
      if (hasDoneEvent || hasAnyEvent || source?.readyState === EventSource.CLOSED) {
        finalize(false)
        return
      }
      handlers.onError?.('AI 响应失败，请稍后重试')
      finalize(true)
    }
  }

  return { start, close }
}
