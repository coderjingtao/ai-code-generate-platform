<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'

import {
  getAppByIdForAdmin,
  getMyAppById,
  updateAppByAdmin,
  updateMyApp,
} from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUserStore'

interface EditForm {
  appName: string
  cover: string
  priority: number | undefined
}

type WithStringId<T> = Omit<T, 'id'> & { id: string }

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const { t } = useI18n()

const loading = ref(false)
const submitting = ref(false)
const appInfo = ref<API.AppVO>()
const adminEditMode = ref(false)

const formState = reactive<EditForm>({
  appName: '',
  cover: '',
  priority: undefined,
})

const appId = computed(() => {
  const rawId = Array.isArray(route.params.id) ? route.params.id[0] : route.params.id
  return typeof rawId === 'string' ? rawId.trim() : ''
})

const adminQueryEnabled = computed(() => {
  const adminQuery = Array.isArray(route.query.admin) ? route.query.admin[0] : route.query.admin
  return adminQuery === '1'
})
const isAdminEdit = computed(() => adminEditMode.value)

const pageTitle = computed(() =>
  isAdminEdit.value ? t('appEdit.title.admin') : t('appEdit.title.user'),
)

const withStringId = <T extends { id?: unknown }>(params: WithStringId<T>) => {
  return params as unknown as T
}

const ensureAdminEditMode = async () => {
  if (!adminQueryEnabled.value) {
    return false
  }

  if (!loginUserStore.loginUser?.userRole) {
    await loginUserStore.fetchLoginUser()
  }

  return loginUserStore.loginUser?.userRole === 'admin'
}

const loadAppDetail = async () => {
  if (!appId.value) {
    message.error(t('appEdit.message.invalidId'))
    await router.replace('/')
    return
  }

  loading.value = true
  try {
    const useAdminApi = await ensureAdminEditMode()
    adminEditMode.value = useAdminApi
    const res = useAdminApi
      ? await getAppByIdForAdmin(withStringId<API.getAppByIdForAdminParams>({ id: appId.value }))
      : await getMyAppById(withStringId<API.getMyAppByIdParams>({ id: appId.value }))

    if (res.data.code === 0 && res.data.data) {
      appInfo.value = res.data.data
      formState.appName = res.data.data.appName || ''
      formState.cover = res.data.data.cover || ''
      formState.priority = res.data.data.priority
      return
    }

    message.error(res.data.message || t('appEdit.message.loadFailed'))
  } catch {
    message.error(t('appEdit.message.loadFailedRetry'))
  } finally {
    loading.value = false
  }
}

const submitForm = async () => {
  if (!appId.value) {
    message.error(t('appEdit.message.invalidId'))
    return
  }

  const trimmedName = formState.appName.trim()
  if (!trimmedName) {
    message.warning(t('appEdit.message.appNameRequired'))
    return
  }

  submitting.value = true
  try {
    if (isAdminEdit.value) {
      const res = await updateAppByAdmin(
        withStringId<API.AppAdminUpdateRequest>({
          id: appId.value,
          appName: trimmedName,
          cover: formState.cover.trim() || undefined,
          priority: formState.priority,
        }),
      )

      if (res.data.code === 0) {
        message.success(t('appEdit.message.updateSuccess'))
        await loadAppDetail()
        return
      }

      message.error(res.data.message || t('appEdit.message.updateFailed'))
      return
    }

    const res = await updateMyApp(
      withStringId<API.AppUpdateRequest>({
        id: appId.value,
        appName: trimmedName,
      }),
    )

    if (res.data.code === 0) {
      message.success(t('appEdit.message.updateSuccess'))
      await loadAppDetail()
      return
    }

    message.error(res.data.message || t('appEdit.message.updateFailed'))
  } catch {
    message.error(t('appEdit.message.updateFailedRetry'))
  } finally {
    submitting.value = false
  }
}

const goBack = () => {
  void router.back()
}

const goToChat = () => {
  if (!appId.value) {
    return
  }
  void router.push({
    path: `/app/chat/${appId.value}`,
    query: isAdminEdit.value ? { admin: '1' } : undefined,
  })
}

onMounted(() => {
  void loadAppDetail()
})
</script>

<template>
  <section class="app-edit-view">
    <a-card :bordered="false" class="edit-card" :loading="loading">
      <div class="edit-card__header">
        <div>
          <h1>{{ pageTitle }}</h1>
          <p>{{ $t('appEdit.description') }}</p>
        </div>
        <a-space>
          <a-button @click="goBack">{{ $t('common.actions.back') }}</a-button>
          <a-button @click="goToChat">{{ $t('appEdit.actions.viewDetail') }}</a-button>
        </a-space>
      </div>

      <a-form layout="vertical" :model="formState" class="edit-form">
        <a-form-item :label="$t('appEdit.form.appName')" required>
          <a-input v-model:value="formState.appName" :placeholder="$t('appEdit.form.appNamePlaceholder')" />
        </a-form-item>

        <a-form-item v-if="isAdminEdit" :label="$t('appEdit.form.cover')">
          <a-input v-model:value="formState.cover" :placeholder="$t('appEdit.form.coverPlaceholder')" />
        </a-form-item>

        <a-form-item v-if="isAdminEdit" :label="$t('appEdit.form.priority')">
          <a-input-number v-model:value="formState.priority" :min="0" style="width: 220px" />
        </a-form-item>
      </a-form>

      <a-descriptions :title="$t('appEdit.info.title')" bordered :column="2" size="small">
        <a-descriptions-item :label="$t('appEdit.info.id')">{{ appInfo?.id ?? '-' }}</a-descriptions-item>
        <a-descriptions-item :label="$t('appEdit.info.creator')">{{
          appInfo?.user?.userName || '-'
        }}</a-descriptions-item>
        <a-descriptions-item :label="$t('appEdit.info.genType')">{{
          appInfo?.codeGenType || '-'
        }}</a-descriptions-item>
        <a-descriptions-item :label="$t('appEdit.info.priority')">{{ appInfo?.priority ?? '-' }}</a-descriptions-item>
        <a-descriptions-item :label="$t('appEdit.info.createTime')">{{ appInfo?.createTime || '-' }}</a-descriptions-item>
        <a-descriptions-item :label="$t('appEdit.info.updateTime')">{{ appInfo?.updateTime || '-' }}</a-descriptions-item>
      </a-descriptions>

      <div class="edit-card__footer">
        <a-space>
          <a-button @click="goBack">{{ $t('common.actions.cancel') }}</a-button>
          <a-button type="primary" :loading="submitting" @click="submitForm">{{
            $t('common.actions.save')
          }}</a-button>
        </a-space>
      </div>
    </a-card>
  </section>
</template>

<style scoped>
.app-edit-view {
  padding: 18px;
}

.edit-card {
  width: min(980px, 100%);
  margin: 0 auto;
  border-radius: 16px;
}

.edit-card__header {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
  margin-bottom: 18px;
}

.edit-card__header h1 {
  margin: 0;
  font-size: 28px;
  color: #0f172a;
}

.edit-card__header p {
  margin: 8px 0 0;
  color: rgba(15, 23, 42, 0.58);
}

.edit-form {
  margin-bottom: 14px;
}

.edit-card__footer {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .app-edit-view {
    padding: 12px;
  }

  .edit-card__header {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
