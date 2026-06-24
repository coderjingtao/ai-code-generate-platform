export interface GlobalMenuItem {
  key: string
  /** i18n 文案 key（在 GlobalHeader 中翻译为 label 后再传给 a-menu） */
  labelKey: string
  path: string
}

export const globalMenuItems: GlobalMenuItem[] = [
  { key: 'home', labelKey: 'common.header.menuHome', path: '/' },
]
