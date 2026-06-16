<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { getFileIcon } from '@/utils/fileIcon'

/**
 * 应用文件树。负责把扁平的文件路径列表构建成树形结构、自动展开全部目录、
 * 渲染按文件名/目录名/扩展名匹配的图标，并在选择文件时向外抛出 select 事件。
 */
const props = withDefaults(
  defineProps<{
    files: string[]
    /** 当前选中的文件路径 */
    selectedPath?: string
    /** 正在生成中的文件路径，用于高亮提示 */
    activePath?: string
  }>(),
  {
    selectedPath: '',
    activePath: '',
  },
)

const emit = defineEmits<{
  (e: 'select', path: string): void
}>()

interface FileTreeNode {
  title: string
  key: string
  isLeaf?: boolean
  children?: FileTreeNode[]
}

const getAllFolderPaths = (fileList: string[]): string[] => {
  const folders = new Set<string>()
  fileList.forEach((file) => {
    const parts = file.split('/')
    for (let i = 1; i < parts.length; i++) {
      folders.add(parts.slice(0, i).join('/'))
    }
  })
  return Array.from(folders)
}

const buildFileTree = (fileList: string[]): FileTreeNode[] => {
  const root: FileTreeNode[] = []

  fileList.forEach((file) => {
    const parts = file.split('/')
    let currentLevel = root

    parts.forEach((part, index) => {
      const isLeaf = index === parts.length - 1
      const key = parts.slice(0, index + 1).join('/')

      let existing = currentLevel.find((item) => item.title === part)
      if (!existing) {
        existing = { title: part, key, isLeaf }
        if (!isLeaf) {
          existing.children = []
        }
        currentLevel.push(existing)
      }
      if (!isLeaf && existing.children) {
        currentLevel = existing.children
      }
    })
  })

  const sortTree = (nodes: FileTreeNode[]) => {
    nodes.sort((a, b) => {
      if (a.isLeaf !== b.isLeaf) {
        return a.isLeaf ? 1 : -1
      }
      return a.title.localeCompare(b.title)
    })
    nodes.forEach((node) => {
      if (node.children) {
        sortTree(node.children)
      }
    })
  }

  sortTree(root)
  return root
}

const treeData = computed(() => buildFileTree(props.files))

const selectedKeys = computed<string[]>({
  get: () => (props.selectedPath ? [props.selectedPath] : []),
  set: () => {
    /* 选中态由父组件通过 selectedPath 单向控制，set 交给 onSelect 处理 */
  },
})

const expandedKeys = ref<string[]>([])
watch(
  () => props.files,
  (newFiles) => {
    expandedKeys.value = getAllFolderPaths(newFiles)
  },
  { deep: true, immediate: true },
)

const onSelect = (keys: (string | number)[], info: { node: { isLeaf?: boolean; key: string } }) => {
  if (info.node.isLeaf && keys.length > 0) {
    emit('select', String(keys[0]))
  }
}
</script>

<template>
  <div class="app-file-tree">
    <a-tree
      :selected-keys="selectedKeys"
      v-model:expandedKeys="expandedKeys"
      :tree-data="treeData"
      class="app-file-tree__tree"
      @select="onSelect"
    >
      <template #title="{ title, key, isLeaf }">
        <span
          class="app-file-tree__node"
          :class="{ 'app-file-tree__node--active': isLeaf && key === activePath }"
        >
          <span class="app-file-tree__icon" v-html="getFileIcon(title, isLeaf)"></span>
          <span class="app-file-tree__text">{{ title }}</span>
          <span v-if="isLeaf && key === activePath" class="app-file-tree__spinner"></span>
        </span>
      </template>
    </a-tree>
  </div>
</template>

<style scoped>
.app-file-tree {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 8px 4px;
  background: transparent !important;
}

.app-file-tree :deep(.ant-tree),
.app-file-tree :deep(.ant-tree-list) {
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
  border-radius: 0 !important;
}

.app-file-tree__tree :deep(.ant-tree-treenode) {
  padding: 4px 0 !important;
  display: flex !important;
  align-items: center !important;
  width: 100% !important;
}

.app-file-tree__tree :deep(.ant-tree-node-content-wrapper) {
  display: flex !important;
  align-items: center !important;
  padding: 6px 8px !important;
  border-radius: var(--ac-radius-sm) !important;
  transition: all 0.15s ease !important;
  flex: 1 !important;
  font-size: 13px !important;
  font-family:
    SFMono-Regular,
    Consolas,
    Liberation Mono,
    Menlo,
    monospace !important;
  background-color: transparent !important;
}

.app-file-tree__tree :deep(.ant-tree-node-content-wrapper:hover) {
  background-color: var(--ac-primary-soft) !important;
}

.app-file-tree__tree :deep(.ant-tree-node-selected) {
  background-color: var(--ac-primary-soft) !important;
  color: var(--ac-primary-strong) !important;
  font-weight: 500 !important;
}

.app-file-tree__node {
  display: flex;
  align-items: center;
  gap: 8px;
}

.app-file-tree__node--active .app-file-tree__text {
  color: var(--ac-primary);
  font-weight: 600;
}

.app-file-tree__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.app-file-tree__text {
  font-size: 13px;
  font-weight: 500;
  line-height: 1.5;
}

.app-file-tree__spinner {
  width: 10px;
  height: 10px;
  border: 2px solid var(--ac-primary-soft);
  border-top-color: var(--ac-primary);
  border-radius: 50%;
  animation: app-file-tree-spin 0.8s linear infinite;
  flex-shrink: 0;
}

@keyframes app-file-tree-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
