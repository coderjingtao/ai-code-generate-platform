import { CodeGenTypeEnum } from '@/utils/codeGenTypes'

// API 基础地址
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

// 静态资源基础地址
export const STATIC_BASE_URL = import.meta.env.DEV ? '/api/static' : `${API_BASE_URL}/static`

// 作品部署访问地址（与后端 code.deploy-host 对应）
export const DEPLOY_BASE_URL = import.meta.env.VITE_DEPLOY_DOMAIN || 'http://localhost:8080'

// 获取静态资源预览地址
export const getStaticPreviewUrl = (codeGenType: string, appId: string) => {
  const baseUrl = `${STATIC_BASE_URL}/${codeGenType}_${appId}`
  //如果是Vue项目模式，浏览地址需要添加dist后缀
  if (codeGenType === CodeGenTypeEnum.VUE_PROJECT) {
    return `${baseUrl}/dist/index.html`
  }
  //末尾补斜杠：直接命中目录的 index.html，避免后端 301 跳转到 http 触发 Mixed Content
  return `${baseUrl}/`
}
