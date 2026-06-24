import { ref } from 'vue'
import { defineStore } from 'pinia'
import i18n, { detectLocale, type Lang } from '@/locales'

/**
 * 全局语言状态：负责切换 vue-i18n 当前语言、持久化到 localStorage，
 * 并同步 <html lang> 属性。初始值按浏览器语言判定，回退英文。
 */
export const useLocaleStore = defineStore('locale', () => {
  const locale = ref<Lang>(detectLocale())

  function applyLocale(lang: Lang) {
    locale.value = lang
    ;(i18n.global.locale as unknown as { value: Lang }).value = lang
    localStorage.setItem('locale', lang)
    document.documentElement.lang = lang === 'zh' ? 'zh-CN' : 'en'
  }

  function setLocale(lang: Lang) {
    if (lang !== locale.value) {
      applyLocale(lang)
    }
  }

  function toggleLocale() {
    setLocale(locale.value === 'zh' ? 'en' : 'zh')
  }

  // 初始化：把检测到的语言同步到 i18n 与 <html lang>
  applyLocale(locale.value)

  return { locale, setLocale, toggleLocale }
})
