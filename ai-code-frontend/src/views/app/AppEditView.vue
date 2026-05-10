<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'

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

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

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

const pageTitle = computed(() => (isAdminEdit.value ? '编辑应用（管理员）' : '编辑我的作品'))

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
    message.error('应用 ID 不合法')
    await router.replace('/')
    return
  }

  loading.value = true
  try {
    const useAdminApi = await ensureAdminEditMode()
    adminEditMode.value = useAdminApi
    const res = useAdminApi
      ? await getAppByIdForAdmin({ id: appId.value })
      : await getMyAppById({ id: appId.value })

    if (res.data.code === 0 && res.data.data) {
      appInfo.value = res.data.data
      formState.appName = res.data.data.appName || ''
      formState.cover = res.data.data.cover || ''
      formState.priority = res.data.data.priority
      return
    }

    message.error(res.data.message || '获取应用详情失败')
  } catch {
    message.error('获取应用详情失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const submitForm = async () => {
  if (!appId.value) {
    message.error('应用 ID 不合法')
    return
  }

  const trimmedName = formState.appName.trim()
  if (!trimmedName) {
    message.warning('请输入应用名称')
    return
  }

  submitting.value = true
  try {
    if (isAdminEdit.value) {
      const res = await updateAppByAdmin({
        id: appId.value,
        appName: trimmedName,
        cover: formState.cover.trim() || undefined,
        priority: formState.priority,
      })

      if (res.data.code === 0) {
        message.success('更新应用成功')
        await loadAppDetail()
        return
      }

      message.error(res.data.message || '更新应用失败')
      return
    }

    const res = await updateMyApp({
      id: appId.value,
      appName: trimmedName,
    })

    if (res.data.code === 0) {
      message.success('更新应用成功')
      await loadAppDetail()
      return
    }

    message.error(res.data.message || '更新应用失败')
  } catch {
    message.error('更新应用失败，请稍后重试')
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
          <p>普通用户仅可修改应用名称；管理员可额外修改应用封面和优先级。</p>
        </div>
        <a-space>
          <a-button @click="goBack">返回</a-button>
          <a-button @click="goToChat">查看详情</a-button>
        </a-space>
      </div>

      <a-form layout="vertical" :model="formState" class="edit-form">
        <a-form-item label="应用名称" required>
          <a-input v-model:value="formState.appName" placeholder="请输入应用名称" />
        </a-form-item>

        <a-form-item v-if="isAdminEdit" label="应用封面">
          <a-input v-model:value="formState.cover" placeholder="请输入应用封面 URL" />
        </a-form-item>

        <a-form-item v-if="isAdminEdit" label="优先级">
          <a-input-number v-model:value="formState.priority" :min="0" style="width: 220px" />
        </a-form-item>
      </a-form>

      <a-descriptions title="应用信息" bordered :column="2" size="small">
        <a-descriptions-item label="应用 ID">{{ appInfo?.id ?? '-' }}</a-descriptions-item>
        <a-descriptions-item label="创建者">{{
          appInfo?.user?.userName || '-'
        }}</a-descriptions-item>
        <a-descriptions-item label="生成类型">{{
          appInfo?.codeGenType || '-'
        }}</a-descriptions-item>
        <a-descriptions-item label="优先级">{{ appInfo?.priority ?? '-' }}</a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ appInfo?.createTime || '-' }}</a-descriptions-item>
        <a-descriptions-item label="更新时间">{{ appInfo?.updateTime || '-' }}</a-descriptions-item>
      </a-descriptions>

      <div class="edit-card__footer">
        <a-space>
          <a-button @click="goBack">取消</a-button>
          <a-button type="primary" :loading="submitting" @click="submitForm">保存</a-button>
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
