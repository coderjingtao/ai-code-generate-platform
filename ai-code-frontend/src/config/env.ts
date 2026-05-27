import { CodeGenTypeEnum } from '@/utils/codeGenTypes'

// API 基础地址
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

// 静态资源基础地址
export const STATIC_BASE_URL = import.meta.env.DEV ? '/api/static' : `${API_BASE_URL}/static`

// 获取静态资源预览地址
export const getStaticPreviewUrl = (codeGenType: string, appId: string) => {
  const baseUrl = `${STATIC_BASE_URL}/${codeGenType}_${appId}`
  //如果是Vue项目模式，浏览地址需要添加dist后缀
  if (codeGenType === CodeGenTypeEnum.VUE_PROJECT) {
    return `${baseUrl}/dist/index.html`
  }
  return baseUrl
}
