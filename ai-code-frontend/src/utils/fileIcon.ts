/**
 * 文件树图标解析。
 *
 * 匹配优先级：精确文件名 → 目录名 → 扩展名 → 默认文档。
 * 返回内联 SVG 字符串（供 v-html 渲染），颜色统一使用 theme.css 中的 --ac-icon-* 变量。
 */

const svg = (body: string, color: string, options: { stroke?: boolean } = {}): string => {
  const paint = options.stroke
    ? `fill="none" stroke="${color}" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"`
    : `fill="${color}"`
  return `<svg width="16" height="16" viewBox="0 0 24 24" ${paint} style="display:block;">${body}</svg>`
}

// --- 各类图标的 SVG 路径 ---
const FOLDER = `<path d="M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"/>`
const HTML = `<path d="M12 2L2 5l1.8 13.5L12 22l8.2-3.5L22 5z M18 9h-3.4l-0.3 3h3.4l-0.4 4.5L12 18.2l-5.3-1.7L6.3 12h3.3l-0.2-2.1H6.1l0.3-3h11.9z"/>`
const PACKAGE = `<path d="M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zM4 9h5v3H7v2H4V9zm11 5H11V9h4v5zm5-2h-3v2h-2V9h5v3z"/>`
const VITE = `<path d="M12 2L2 14h9v8l11-12h-9z"/>`
const ATOM = `<ellipse cx="12" cy="12" rx="10" ry="4" transform="rotate(30 12 12)" /><ellipse cx="12" cy="12" rx="10" ry="4" transform="rotate(90 12 12)" /><ellipse cx="12" cy="12" rx="10" ry="4" transform="rotate(150 12 12)" /><circle cx="12" cy="12" r="1.5" />`
const DOC = `<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline>`
const GEAR = `<circle cx="12" cy="12" r="3"></circle><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"></path>`
const MARKDOWN = `<path d="M3 5h18a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zm2 4v6h2v-3l2 2 2-2v3h2V9h-2l-2 2-2-2H5zm12 0v3h-2l3 3 3-3h-2V9h-2z"/>`
const STYLE = `<path d="M4 3h16l-1.5 16.5L12 22l-6.5-2.5L4 3zm13.5 4H7l.25 2.5h9.5l-.5 6L12 17l-4.25-1.5-.25-3H9.5l.1 1.4L12 14.5l2.4-.6.25-3H6.9"/>`
const IMAGE = `<rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><circle cx="8.5" cy="8.5" r="1.5"></circle><polyline points="21 15 16 10 5 21"></polyline>`

const FILENAME_ICONS: Record<string, string> = {
  'index.html': svg(HTML, 'var(--ac-icon-html)'),
  'package.json': svg(PACKAGE, 'var(--ac-icon-pkg)'),
  'package-lock.json': svg(PACKAGE, 'var(--ac-icon-pkg)'),
  'vite.config.js': svg(VITE, 'var(--ac-icon-vite)'),
  'vite.config.ts': svg(VITE, 'var(--ac-icon-vite)'),
  'tsconfig.json': svg(GEAR, 'var(--ac-icon-config)', { stroke: true }),
  'readme.md': svg(MARKDOWN, 'var(--ac-icon-markdown)'),
  '.gitignore': svg(GEAR, 'var(--ac-icon-default)', { stroke: true }),
}

const DIRNAME_ICONS: Record<string, string> = {
  src: svg(FOLDER, 'var(--ac-icon-script)'),
  components: svg(FOLDER, 'var(--ac-icon-script)'),
  assets: svg(FOLDER, 'var(--ac-icon-image)'),
  public: svg(FOLDER, 'var(--ac-icon-folder)'),
  dist: svg(FOLDER, 'var(--ac-icon-default)'),
  node_modules: svg(FOLDER, 'var(--ac-icon-default)'),
}

const DEFAULT_FOLDER = svg(FOLDER, 'var(--ac-icon-folder)')

const matchByExtension = (name: string): string | undefined => {
  const ext = name.includes('.') ? name.slice(name.lastIndexOf('.') + 1) : ''
  switch (ext) {
    case 'vue':
    case 'js':
    case 'jsx':
    case 'ts':
    case 'tsx':
    case 'mjs':
    case 'cjs':
      return svg(ATOM, 'var(--ac-icon-script)', { stroke: true })
    case 'css':
    case 'scss':
    case 'sass':
    case 'less':
      return svg(STYLE, 'var(--ac-icon-style)')
    case 'html':
    case 'htm':
      return svg(HTML, 'var(--ac-icon-html)')
    case 'json':
      return svg(PACKAGE, 'var(--ac-icon-config)')
    case 'md':
    case 'markdown':
      return svg(MARKDOWN, 'var(--ac-icon-markdown)')
    case 'svg':
    case 'png':
    case 'jpg':
    case 'jpeg':
    case 'gif':
    case 'webp':
    case 'ico':
      return svg(IMAGE, 'var(--ac-icon-image)', { stroke: true })
    default:
      return undefined
  }
}

/**
 * 根据文件 / 目录名返回内联 SVG 图标字符串。
 * @param title  文件名或目录名（节点标题，非完整路径）
 * @param isLeaf 是否为叶子节点（文件）
 */
export const getFileIcon = (title: string, isLeaf: boolean): string => {
  const name = (title || '').toLowerCase()

  if (!isLeaf) {
    return DIRNAME_ICONS[name] || DEFAULT_FOLDER
  }

  return (
    FILENAME_ICONS[name] ||
    matchByExtension(name) ||
    svg(DOC, 'var(--ac-icon-default)', { stroke: true })
  )
}
