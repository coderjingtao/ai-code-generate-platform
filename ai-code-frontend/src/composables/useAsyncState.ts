import { ref, type Ref } from 'vue'

/**
 * 通用的异步请求状态包装：统一管理 loading / error，并把异常收敛成可展示的错误文案。
 * 与 StateView 组件配合，用于替换散落各处的手写 loading / error 处理。
 *
 * @param fn          实际执行的异步函数
 * @param errorText   兜底错误文案（fn 抛出的异常没有 message 时使用）
 */
export interface UseAsyncStateReturn<TArgs extends unknown[], TResult> {
  loading: Ref<boolean>
  error: Ref<string>
  run: (...args: TArgs) => Promise<TResult | undefined>
}

export function useAsyncState<TArgs extends unknown[], TResult>(
  fn: (...args: TArgs) => Promise<TResult>,
  errorText = '加载失败，请稍后重试',
): UseAsyncStateReturn<TArgs, TResult> {
  const loading = ref(false)
  const error = ref('')

  const run = async (...args: TArgs): Promise<TResult | undefined> => {
    loading.value = true
    error.value = ''
    try {
      return await fn(...args)
    } catch (err: unknown) {
      const messageText = err instanceof Error && err.message ? err.message : errorText
      error.value = messageText
      return undefined
    } finally {
      loading.value = false
    }
  }

  return { loading, error, run }
}
