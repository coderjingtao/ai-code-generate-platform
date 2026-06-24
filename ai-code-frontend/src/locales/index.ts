import { createI18n } from 'vue-i18n'

import common from './modules/common'
import components from './modules/components'
import home from './modules/home'
import workspace from './modules/workspace'
import history from './modules/history'
import appChat from './modules/appChat'
import appEdit from './modules/appEdit'
import adminApp from './modules/adminApp'
import adminChat from './modules/adminChat'
import adminUser from './modules/adminUser'
import login from './modules/login'
import register from './modules/register'

export type Lang = 'en' | 'zh'

export const SUPPORTED_LOCALES: Lang[] = ['en', 'zh']

// 每个 namespace 对应一个模块文件，模块内同时提供 en / zh 两套文案。
// 文案树是任意嵌套的字符串字典，这里用宽松类型以兼容 vue-i18n 的 LocaleMessage。
/* eslint-disable @typescript-eslint/no-explicit-any */
type MessageTree = Record<string, any>
type LocaleModule = { en: MessageTree; zh: MessageTree }

const modules: Record<string, LocaleModule> = {
  common,
  components,
  home,
  workspace,
  history,
  appChat,
  appEdit,
  adminApp,
  adminChat,
  adminUser,
  login,
  register,
}

function buildMessages(lang: Lang): MessageTree {
  const out: MessageTree = {}
  for (const [ns, mod] of Object.entries(modules)) {
    out[ns] = mod[lang]
  }
  return out
}

/**
 * 选择初始语言：优先用户上次手动选择（localStorage），否则按浏览器语言判定，
 * 仅当浏览器语言以 zh 开头时使用中文，其余一律回退英文。
 */
export function detectLocale(): Lang {
  const saved = localStorage.getItem('locale')
  if (saved === 'en' || saved === 'zh') {
    return saved
  }
  const nav = (navigator.language || navigator.languages?.[0] || '').toLowerCase()
  return nav.startsWith('zh') ? 'zh' : 'en'
}

// createI18n<false> 关闭 legacy 模式（使用 Composition API），
// 同时避免类型推断把 locale 锁死成默认的 'en-US' schema
const i18n = createI18n<false>({
  legacy: false,
  globalInjection: true,
  locale: detectLocale(),
  fallbackLocale: 'en',
  messages: {
    en: buildMessages('en'),
    zh: buildMessages('zh'),
  },
})

/** 当前语言，供非组件代码（如 axios 拦截器）读取 */
export function currentLang(): Lang {
  return (i18n.global.locale as unknown as { value: Lang }).value
}

export default i18n
