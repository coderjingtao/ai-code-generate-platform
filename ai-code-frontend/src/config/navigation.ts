export interface GlobalMenuItem {
  key: string
  label: string
  path: string
}

export const globalMenuItems: GlobalMenuItem[] = [
  { key: 'home', label: '首页', path: '/' },
  { key: 'workspace', label: '工作台', path: '/workspace' },
  { key: 'history', label: '生成历史', path: '/history' },
]
