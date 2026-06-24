import i18n from '@/locales'

export const VISUAL_EDIT_SELECTED_MESSAGE = 'visual-edit:element-selected'

export interface VisualEditElementInfo {
  tagName: string
  selector: string
  id?: string
  className?: string
  text?: string
  attributes: Record<string, string>
  rect: {
    x: number
    y: number
    width: number
    height: number
  }
}

interface CreateVisualEditorOptions {
  iframe: HTMLIFrameElement
  targetOrigin?: string
  onHover?: (elementInfo: VisualEditElementInfo | null) => void
  onSelect: (elementInfo: VisualEditElementInfo) => void
}

export interface VisualIframeEditor {
  enable: () => boolean
  disable: () => void
  clearSelected: () => void
  handlePointerMove: (event: MouseEvent) => void
  handlePointerLeave: () => void
  handleClick: (event: MouseEvent) => void
  destroy: () => void
}

const HOVER_CLASS = '__visual_edit_hover__'
const SELECTED_CLASS = '__visual_edit_selected__'
const EDITING_CLASS = '__visual_edit_enabled__'
const STYLE_ID = '__visual_edit_style__'
const MAX_TEXT_LENGTH = 160

export const buildPreviewSrcDoc = (html: string, baseUrl: string) => {
  const baseTag = `<base href="${baseUrl.replace(/"/g, '&quot;')}">`

  if (/<head[^>]*>/i.test(html)) {
    return html.replace(/<head([^>]*)>/i, `<head$1>${baseTag}`)
  }

  return `${baseTag}${html}`
}

const getSameOriginDocument = (iframe: HTMLIFrameElement) => {
  try {
    const iframeDocument = iframe.contentDocument || iframe.contentWindow?.document || null
    if (!iframeDocument || iframeDocument.location.href === 'about:blank') {
      return null
    }
    return iframeDocument
  } catch {
    return null
  }
}

const normalizeClassName = (className: Element['className']) => {
  if (typeof className === 'string') {
    return className.trim()
  }
  return ''
}

const normalizeText = (text: string) => {
  return text.replace(/\s+/g, ' ').trim().slice(0, MAX_TEXT_LENGTH)
}

const escapeCssIdentifier = (value: string) => {
  if (window.CSS?.escape) {
    return window.CSS.escape(value)
  }
  return value.replace(/[^a-zA-Z0-9_-]/g, '\\$&')
}

const getElementName = (element: Element) => {
  return element.tagName.toLowerCase()
}

const isDomElement = (value: unknown): value is Element => {
  if (!value || typeof value !== 'object') {
    return false
  }

  const node = value as { nodeType?: number; ownerDocument?: Document | null }
  return node.nodeType === Node.ELEMENT_NODE
}

const isElementSelectable = (element: Element) => {
  const tagName = getElementName(element)
  return tagName !== 'html' && tagName !== 'script' && tagName !== 'style'
}

const getElementIndex = (element: Element) => {
  const parent = element.parentElement
  if (!parent) {
    return 1
  }

  const sameTagSiblings = Array.from(parent.children).filter(
    (child) => getElementName(child) === getElementName(element),
  )
  return sameTagSiblings.indexOf(element) + 1
}

const isUniqueSelector = (documentRef: Document, selector: string) => {
  try {
    return documentRef.querySelectorAll(selector).length === 1
  } catch {
    return false
  }
}

const buildElementSelector = (element: Element, documentRef: Document) => {
  const id = element.getAttribute('id')
  if (id) {
    const selector = `#${escapeCssIdentifier(id)}`
    if (isUniqueSelector(documentRef, selector)) {
      return selector
    }
  }

  const selectorParts: string[] = []
  let current: Element | null = element

  while (current && current !== documentRef.documentElement) {
    const tagName = getElementName(current)
    const currentId = current.getAttribute('id')
    const className = normalizeClassName(current.className)
    const classSelector = className
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((item) => `.${escapeCssIdentifier(item)}`)
      .join('')
    const idSelector = currentId ? `#${escapeCssIdentifier(currentId)}` : ''
    const nthSelector = `:nth-of-type(${getElementIndex(current)})`
    const selectorPart = `${tagName}${idSelector || classSelector || nthSelector}`

    selectorParts.unshift(selectorPart)
    const selector = selectorParts.join(' > ')
    if (isUniqueSelector(documentRef, selector)) {
      return selector
    }

    current = current.parentElement
  }

  return selectorParts.join(' > ') || getElementName(element)
}

const getElementAttributes = (element: Element) => {
  const usefulAttributes = [
    'id',
    'class',
    'href',
    'src',
    'alt',
    'title',
    'role',
    'aria-label',
    'name',
    'type',
  ]
  return usefulAttributes.reduce<Record<string, string>>((attributes, attributeName) => {
    const value = element.getAttribute(attributeName)
    if (value) {
      attributes[attributeName] = value
    }
    return attributes
  }, {})
}

const getElementInfo = (element: Element, documentRef: Document): VisualEditElementInfo => {
  const rect = element.getBoundingClientRect()
  const id = element.getAttribute('id')?.trim() || undefined
  const className = normalizeClassName(element.className) || undefined

  return {
    tagName: getElementName(element),
    selector: buildElementSelector(element, documentRef),
    id,
    className,
    text: normalizeText(element.textContent || ''),
    attributes: getElementAttributes(element),
    rect: {
      x: Math.round(rect.x),
      y: Math.round(rect.y),
      width: Math.round(rect.width),
      height: Math.round(rect.height),
    },
  }
}

const ensureStyle = (documentRef: Document) => {
  if (documentRef.getElementById(STYLE_ID)) {
    return
  }

  const style = documentRef.createElement('style')
  style.id = STYLE_ID
  style.textContent = `
    html.${EDITING_CLASS},
    html.${EDITING_CLASS} * {
      cursor: crosshair !important;
    }

    .${HOVER_CLASS} {
      outline: 2px dashed rgba(22, 119, 255, 0.85) !important;
      outline-offset: 2px !important;
      cursor: crosshair !important;
    }

    .${SELECTED_CLASS} {
      outline: 3px solid rgba(9, 88, 217, 0.98) !important;
      outline-offset: 2px !important;
      cursor: crosshair !important;
    }
  `
  documentRef.head.appendChild(style)
}

const getElementFromIframePoint = (
  iframe: HTMLIFrameElement,
  documentRef: Document,
  event: MouseEvent,
) => {
  const iframeRect = iframe.getBoundingClientRect()
  const x = event.clientX - iframeRect.left
  const y = event.clientY - iframeRect.top
  const targets =
    typeof documentRef.elementsFromPoint === 'function'
      ? documentRef.elementsFromPoint(x, y)
      : [documentRef.elementFromPoint(x, y)]
  return targets.find((item): item is Element => isDomElement(item) && isElementSelectable(item)) || null
}

export const createVisualIframeEditor = ({
  iframe,
  targetOrigin = window.location.origin,
  onHover,
  onSelect,
}: CreateVisualEditorOptions): VisualIframeEditor => {
  let iframeDocument = getSameOriginDocument(iframe)
  let hoverElement: Element | null = null
  let selectedElement: Element | null = null
  let enabled = false

  const clearHover = () => {
    hoverElement?.classList.remove(HOVER_CLASS)
    hoverElement = null
    onHover?.(null)
  }

  const clearSelected = () => {
    selectedElement?.classList.remove(SELECTED_CLASS)
    selectedElement = null
  }

  const setHoverElement = (element: Element) => {
    if (
      !enabled ||
      element === hoverElement ||
      element === selectedElement ||
      !isElementSelectable(element)
    ) {
      return
    }
    clearHover()
    hoverElement = element
    hoverElement.classList.add(HOVER_CLASS)
    if (iframeDocument) {
      onHover?.(getElementInfo(element, iframeDocument))
    }
  }

  const handlePointerMove = (event: MouseEvent) => {
    iframeDocument = getSameOriginDocument(iframe) || iframeDocument
    if (!iframeDocument) {
      return
    }

    const target = getElementFromIframePoint(iframe, iframeDocument, event)
    if (target) {
      setHoverElement(target)
      return
    }

    clearHover()
  }

  const handlePointerLeave = () => {
    clearHover()
  }

  const handleIframeMouseOver = (event: MouseEvent) => {
    const target = event.target
    if (isDomElement(target)) {
      setHoverElement(target)
    }
  }

  const handleIframeMouseOut = (event: MouseEvent) => {
    if (event.target === hoverElement) {
      clearHover()
    }
  }

  const selectElement = (target: Element) => {
    if (!enabled || !iframeDocument) {
      return
    }

    if (!isElementSelectable(target)) {
      return
    }

    clearHover()
    clearSelected()
    selectedElement = target
    selectedElement.classList.add(SELECTED_CLASS)

    const elementInfo = getElementInfo(target, iframeDocument)
    onSelect(elementInfo)
    iframe.contentWindow?.parent.postMessage(
      {
        type: VISUAL_EDIT_SELECTED_MESSAGE,
        payload: elementInfo,
      },
      targetOrigin,
    )
  }

  const handleClick = (event: MouseEvent) => {
    event.preventDefault()
    event.stopPropagation()

    iframeDocument = getSameOriginDocument(iframe) || iframeDocument
    if (!iframeDocument) {
      return
    }

    const target = getElementFromIframePoint(iframe, iframeDocument, event)
    if (target) {
      selectElement(target)
    }
  }

  const handleIframeClick = (event: MouseEvent) => {
    const target = event.target
    if (!isDomElement(target)) {
      return
    }

    event.preventDefault()
    event.stopPropagation()
    selectElement(target)
  }

  const attachListeners = () => {
    iframeDocument = getSameOriginDocument(iframe)
    if (!iframeDocument) {
      return false
    }

    ensureStyle(iframeDocument)
    iframeDocument.documentElement.classList.add(EDITING_CLASS)
    iframeDocument.addEventListener('mouseover', handleIframeMouseOver, true)
    iframeDocument.addEventListener('mouseout', handleIframeMouseOut, true)
    iframeDocument.addEventListener('click', handleIframeClick, true)
    return true
  }

  const detachListeners = () => {
    if (!iframeDocument) {
      return
    }

    iframeDocument.removeEventListener('mouseover', handleIframeMouseOver, true)
    iframeDocument.removeEventListener('mouseout', handleIframeMouseOut, true)
    iframeDocument.removeEventListener('click', handleIframeClick, true)
    iframeDocument.documentElement.classList.remove(EDITING_CLASS)
  }

  const handleMessage = (event: MessageEvent) => {
    if (event.source !== iframe.contentWindow || event.origin !== window.location.origin) {
      return
    }

    const data = event.data as { type?: string; payload?: VisualEditElementInfo }
    if (data?.type === VISUAL_EDIT_SELECTED_MESSAGE && data.payload) {
      onSelect(data.payload)
    }
  }

  window.addEventListener('message', handleMessage)

  return {
    enable() {
      if (enabled) {
        return true
      }

      const attached = attachListeners()
      enabled = attached
      return attached
    },
    disable() {
      enabled = false
      clearHover()
      detachListeners()
    },
    clearSelected,
    handlePointerMove,
    handlePointerLeave,
    handleClick,
    destroy() {
      enabled = false
      clearHover()
      clearSelected()
      detachListeners()
      window.removeEventListener('message', handleMessage)
    },
  }
}

export const buildVisualEditPrompt = (
  prompt: string,
  elementInfo?: VisualEditElementInfo | null,
) => {
  if (!elementInfo) {
    return prompt
  }

  const t = i18n.global.t
  const elementDescription = [
    t('common.visualEdit.tagName', { value: elementInfo.tagName }),
    t('common.visualEdit.selector', { value: elementInfo.selector }),
    elementInfo.id ? t('common.visualEdit.id', { value: elementInfo.id }) : '',
    elementInfo.className ? t('common.visualEdit.className', { value: elementInfo.className }) : '',
    elementInfo.text ? t('common.visualEdit.text', { value: elementInfo.text }) : '',
    t('common.visualEdit.rect', {
      x: elementInfo.rect.x,
      y: elementInfo.rect.y,
      w: elementInfo.rect.width,
      h: elementInfo.rect.height,
    }),
  ].filter(Boolean)

  return [
    t('common.visualEdit.instruction'),
    t('common.visualEdit.selectedInfo'),
    ...elementDescription.map((item) => `- ${item}`),
    '',
    t('common.visualEdit.userRequirement'),
    prompt,
  ].join('\n')
}
