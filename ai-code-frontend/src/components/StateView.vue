<script setup lang="ts">
/**
 * 通用的加载 / 空 / 错误状态容器。
 * 优先级：loading > error > empty > 默认插槽内容。
 * 错误态提供「重试」按钮，通过 retry 事件向外抛出。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const props = withDefaults(
  defineProps<{
    loading?: boolean
    error?: string
    empty?: boolean
    loadingText?: string
    emptyText?: string
    /** 是否在错误态显示重试按钮 */
    retryable?: boolean
  }>(),
  {
    loading: false,
    error: '',
    empty: false,
    loadingText: '',
    emptyText: '',
    retryable: true,
  },
)

defineEmits<{
  (e: 'retry'): void
}>()

const { t } = useI18n()
// 未显式传入时回退到通用「暂无数据」文案
const resolvedEmptyText = computed(() => props.emptyText || t('common.state.empty'))
</script>

<template>
  <div v-if="loading" class="state-view state-view--loading">
    <a-spin />
    <p v-if="loadingText" class="state-view__text">{{ loadingText }}</p>
  </div>

  <div v-else-if="error" class="state-view state-view--error">
    <p class="state-view__text">{{ error }}</p>
    <a-button v-if="retryable" size="small" @click="$emit('retry')">{{
      $t('common.actions.retry')
    }}</a-button>
  </div>

  <a-empty v-else-if="empty" :description="resolvedEmptyText" />

  <slot v-else />
</template>

<style scoped>
.state-view {
  height: 100%;
  min-height: 120px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 20px;
  text-align: center;
}

.state-view__text {
  margin: 0;
  color: var(--ac-text-muted);
  font-size: 13px;
}

.state-view--error .state-view__text {
  color: var(--ac-error);
}
</style>
