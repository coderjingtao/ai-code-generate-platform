<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = withDefaults(
  defineProps<{
    app: API.AppVO
    showManageActions?: boolean
    deleting?: boolean
  }>(),
  {
    showManageActions: false,
    deleting: false,
  },
)

const emit = defineEmits<{
  (e: 'view-chat', app: API.AppVO): void
  (e: 'view-work', app: API.AppVO): void
  (e: 'edit', app: API.AppVO): void
  (e: 'delete', app: API.AppVO): void
}>()

const creatorName = computed(
  () => props.app.user?.userName?.trim() || t('components.appCard.anonymousUser'),
)
const creatorAvatar = computed(() => props.app.user?.userAvatar || '')
const hasDeploy = computed(() => Boolean(props.app.deployKey))
const appTitle = computed(() => props.app.appName?.trim() || t('components.appCard.untitledApp'))
const coverUrl = computed(() => props.app.cover?.trim() || '')
const coverFallback = computed(() => appTitle.value.slice(0, 1).toUpperCase())
const avatarFallback = computed(() => creatorName.value.slice(0, 1).toUpperCase())

const onViewChat = () => emit('view-chat', props.app)
const onViewWork = () => emit('view-work', props.app)
const onEdit = () => emit('edit', props.app)
const onDelete = () => emit('delete', props.app)
</script>

<template>
  <article class="app-card">
    <div class="app-card__cover">
      <img v-if="coverUrl" :src="coverUrl" :alt="appTitle" class="app-card__cover-image" />
      <div v-else class="app-card__cover-fallback">{{ coverFallback }}</div>
      <div class="app-card__overlay">
        <div class="app-card__actions">
          <a-button type="primary" size="large" class="app-card__action" @click="onViewChat">
            {{ $t('components.appCard.viewChat') }}
          </a-button>
          <a-button
            v-if="hasDeploy"
            size="large"
            class="app-card__action app-card__action--light"
            @click="onViewWork"
          >
            {{ $t('components.appCard.viewWork') }}
          </a-button>
        </div>
      </div>
    </div>

    <div class="app-card__content">
      <a-avatar :size="38" :src="creatorAvatar" class="app-card__avatar">{{ avatarFallback }}</a-avatar>
      <div class="app-card__text">
        <h3 class="app-card__title">{{ appTitle }}</h3>
        <p class="app-card__creator">{{ creatorName }}</p>
      </div>
    </div>

    <div v-if="showManageActions" class="app-card__manage">
      <a-button type="link" @click="onEdit">{{ $t('common.actions.edit') }}</a-button>
      <a-popconfirm
        :title="$t('components.appCard.deleteConfirm')"
        :ok-text="$t('common.actions.confirm')"
        :cancel-text="$t('common.actions.cancel')"
        @confirm="onDelete"
      >
        <a-button type="link" danger :loading="deleting">{{ $t('common.actions.delete') }}</a-button>
      </a-popconfirm>
    </div>
  </article>
</template>

<style scoped>
.app-card {
  height: 100%;
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 28px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.app-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 16px 30px rgba(15, 23, 42, 0.1);
}

.app-card__cover {
  position: relative;
  aspect-ratio: 16 / 10;
  background: #0f172a;
  overflow: hidden;
}

.app-card__cover-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.app-card__cover-fallback {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  font-size: 52px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.9);
  background: linear-gradient(145deg, #2662d4 0%, #0ea5e9 100%);
}

.app-card__overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  opacity: 0;
  transition: opacity 0.2s ease;
  display: grid;
  place-items: center;
}

.app-card:hover .app-card__overlay {
  opacity: 1;
}

.app-card__content {
  min-width: 0;
  padding: 12px 16px 14px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.app-card__avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, #22d3ee, #2563eb);
  font-weight: 600;
}

.app-card__text {
  min-width: 0;
}

.app-card__title {
  margin: 0;
  font-size: 16px;
  color: #0f172a;
  line-height: 1.28;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-card__creator {
  margin: 4px 0 0;
  font-size: 13px;
  color: rgba(15, 23, 42, 0.56);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-card__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: center;
}

.app-card__action {
  min-width: 112px;
  height: 46px;
  border-radius: 12px;
  border: none;
  font-size: 17px;
  font-weight: 600;
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.22);
}

.app-card__action--light {
  background: rgba(255, 255, 255, 0.95);
  color: #111827;
}

.app-card__action--light:hover,
.app-card__action--light:focus {
  background: #fff;
  color: #111827;
}

.app-card__manage {
  border-top: 1px solid rgba(15, 23, 42, 0.08);
  padding: 8px 16px 10px;
  display: flex;
  align-items: center;
  gap: 2px;
}
</style>
