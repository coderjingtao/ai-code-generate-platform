import i18n from '@/locales'

/**
 * 代码生成类型枚举
 */
export enum CodeGenTypeEnum {
  HTML = 'html',
  MULTI_FILE = 'multi_file',
  VUE_PROJECT = 'vue_project',
  REACT_PROJECT = 'react_project',
}

/** 解析某个代码生成类型的本地化显示文案 */
const typeLabel = (type: CodeGenTypeEnum): string => i18n.global.t(`common.codeGenType.${type}`)

/**
 * 代码生成类型配置（label 为响应当前语言的 getter）
 */
export const CODE_GEN_TYPE_CONFIG = {
  [CodeGenTypeEnum.HTML]: {
    get label() {
      return typeLabel(CodeGenTypeEnum.HTML)
    },
    value: CodeGenTypeEnum.HTML,
  },
  [CodeGenTypeEnum.MULTI_FILE]: {
    get label() {
      return typeLabel(CodeGenTypeEnum.MULTI_FILE)
    },
    value: CodeGenTypeEnum.MULTI_FILE,
  },
  [CodeGenTypeEnum.VUE_PROJECT]: {
    get label() {
      return typeLabel(CodeGenTypeEnum.VUE_PROJECT)
    },
    value: CodeGenTypeEnum.VUE_PROJECT,
  },
  [CodeGenTypeEnum.REACT_PROJECT]: {
    get label() {
      return typeLabel(CodeGenTypeEnum.REACT_PROJECT)
    },
    value: CodeGenTypeEnum.REACT_PROJECT,
  },
}

/**
 * 代码生成类型选项（用于下拉选择）。
 * 用 getter 暴露 label，确保切换语言后取值即为当前语言。
 */
export const CODE_GEN_TYPE_OPTIONS = Object.values(CodeGenTypeEnum).map((value) => ({
  get label() {
    return typeLabel(value)
  },
  value,
}))

/**
 * 格式化代码生成类型
 * @param type 代码生成类型
 * @returns 格式化后的类型描述
 */
export const formatCodeGenType = (type: string | undefined): string => {
  if (!type) return i18n.global.t('common.codeGenType.unknown')

  if (Object.values(CodeGenTypeEnum).includes(type as CodeGenTypeEnum)) {
    return typeLabel(type as CodeGenTypeEnum)
  }
  return type
}

/**
 * 获取所有代码生成类型
 */
export const getAllCodeGenTypes = () => {
  return Object.values(CodeGenTypeEnum)
}

/**
 * 检查是否为有效的代码生成类型
 * @param type 待检查的类型
 */
export const isValidCodeGenType = (type: string): type is CodeGenTypeEnum => {
  return Object.values(CodeGenTypeEnum).includes(type as CodeGenTypeEnum)
}
