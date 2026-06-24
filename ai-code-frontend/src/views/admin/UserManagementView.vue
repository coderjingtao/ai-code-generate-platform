<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { TableColumnsType, TablePaginationConfig } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'

import { addUser, deleteUser, listUserVoByPage, updateUser } from '@/api/usersController'

const { t } = useI18n()

interface UserDrawerForm {
  id?: number
  userEmail: string
  userName: string
  userAvatar: string
  userProfile: string
  userRole: string
}

const loading = ref(false)
const total = ref(0)
const userList = ref<API.UserVO[]>([])
const deletingId = ref<number>()

const drawerOpen = ref(false)
const drawerSubmitting = ref(false)
const drawerMode = ref<'create' | 'edit'>('create')

const searchParams = reactive<API.UserQueryRequest>({
  pageNum: 1,
  pageSize: 10,
  userName: '',
  userEmail: '',
  userRole: undefined,
})

const roleOptions = computed(() => [
  { label: t('adminUser.role.user'), value: 'user' },
  { label: t('adminUser.role.admin'), value: 'admin' },
])

const drawerForm = reactive<UserDrawerForm>({
  id: undefined,
  userEmail: '',
  userName: '',
  userAvatar: '',
  userProfile: '',
  userRole: 'user',
})

const drawerTitle = computed(() =>
  drawerMode.value === 'create' ? t('adminUser.title.create') : t('adminUser.title.edit'),
)

const columns = computed<TableColumnsType<API.UserVO>>(() => [
  {
    title: t('adminUser.columns.id'),
    dataIndex: 'id',
    width: 90,
  },
  {
    title: t('adminUser.columns.userAvatar'),
    dataIndex: 'userAvatar',
    width: 90,
  },
  {
    title: t('adminUser.columns.userName'),
    dataIndex: 'userName',
    width: 180,
  },
  {
    title: t('adminUser.columns.userEmail'),
    dataIndex: 'userEmail',
    width: 180,
  },
  {
    title: t('adminUser.columns.userProfile'),
    dataIndex: 'userProfile',
  },
  {
    title: t('adminUser.columns.userRole'),
    dataIndex: 'userRole',
    width: 120,
  },
  {
    title: t('adminUser.columns.createTime'),
    dataIndex: 'createTime',
    width: 220,
  },
  {
    title: t('adminUser.columns.action'),
    key: 'action',
    width: 170,
  },
])

const resetDrawerForm = () => {
  drawerForm.id = undefined
  drawerForm.userEmail = ''
  drawerForm.userName = ''
  drawerForm.userAvatar = ''
  drawerForm.userProfile = ''
  drawerForm.userRole = 'user'
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await listUserVoByPage({
      ...searchParams,
      userName: searchParams.userName?.trim() || undefined,
      userEmail: searchParams.userEmail?.trim() || undefined,
      userRole: searchParams.userRole || undefined,
    })

    if (res.data.code === 0 && res.data.data) {
      userList.value = res.data.data.records ?? []
      total.value = res.data.data.totalRow ?? 0
      return
    }

    message.error(res.data.message || t('adminUser.messages.loadFailed'))
  } catch {
    message.error(t('adminUser.messages.loadFailedRetry'))
  } finally {
    loading.value = false
  }
}

const doSearch = () => {
  searchParams.pageNum = 1
  void loadData()
}

const doReset = () => {
  searchParams.pageNum = 1
  searchParams.pageSize = 10
  searchParams.userName = ''
  searchParams.userEmail = ''
  searchParams.userRole = undefined
  void loadData()
}

const onTableChange = (pagination: TablePaginationConfig) => {
  searchParams.pageNum = pagination.current ?? 1
  searchParams.pageSize = pagination.pageSize ?? 10
  void loadData()
}

const openCreateDrawer = () => {
  drawerMode.value = 'create'
  resetDrawerForm()
  drawerOpen.value = true
}

const openEditDrawer = (record: API.UserVO) => {
  if (!record.id) {
    message.warning(t('adminUser.messages.noIdEdit'))
    return
  }

  drawerMode.value = 'edit'
  drawerForm.id = record.id
  drawerForm.userEmail = record.userEmail || ''
  drawerForm.userName = record.userName || ''
  drawerForm.userAvatar = record.userAvatar || ''
  drawerForm.userProfile = record.userProfile || ''
  drawerForm.userRole = record.userRole || 'user'
  drawerOpen.value = true
}

const closeDrawer = () => {
  drawerOpen.value = false
}

const submitDrawer = async () => {
  const trimmedUserName = drawerForm.userName.trim()
  const trimmedUserEmail = drawerForm.userEmail.trim()

  if (!trimmedUserName) {
    message.warning(t('adminUser.messages.enterUserName'))
    return
  }

  if (drawerMode.value === 'create' && !trimmedUserEmail) {
    message.warning(t('adminUser.messages.enterEmail'))
    return
  }

  if (drawerMode.value === 'edit' && !drawerForm.id) {
    message.warning(t('adminUser.messages.missingIdSubmit'))
    return
  }

  drawerSubmitting.value = true
  try {
    if (drawerMode.value === 'create') {
      const res = await addUser({
        userEmail: trimmedUserEmail,
        userName: trimmedUserName,
        userAvatar: drawerForm.userAvatar.trim() || undefined,
        userProfile: drawerForm.userProfile.trim() || undefined,
        userRole: drawerForm.userRole || 'user',
      })

      if (res.data.code === 0) {
        message.success(t('adminUser.messages.createSuccess'))
        drawerOpen.value = false
        searchParams.pageNum = 1
        await loadData()
        return
      }

      message.error(res.data.message || t('adminUser.messages.createFailed'))
      return
    }

    const res = await updateUser({
      id: drawerForm.id,
      userName: trimmedUserName,
      userAvatar: drawerForm.userAvatar.trim() || undefined,
      userProfile: drawerForm.userProfile.trim() || undefined,
      userRole: drawerForm.userRole || 'user',
    })

    if (res.data.code === 0) {
      message.success(t('adminUser.messages.updateSuccess'))
      drawerOpen.value = false
      await loadData()
      return
    }

    message.error(res.data.message || t('adminUser.messages.updateFailed'))
  } catch {
    message.error(
      drawerMode.value === 'create'
        ? t('adminUser.messages.createFailedRetry')
        : t('adminUser.messages.updateFailedRetry'),
    )
  } finally {
    drawerSubmitting.value = false
  }
}

const deleteUserById = async (record: API.UserVO) => {
  if (!record.id) {
    message.warning(t('adminUser.messages.noIdDelete'))
    return
  }

  deletingId.value = record.id
  try {
    const res = await deleteUser({ id: record.id })
    if (res.data.code === 0) {
      message.success(t('adminUser.messages.deleteSuccess'))
      if ((searchParams.pageNum ?? 1) > 1 && userList.value.length === 1) {
        searchParams.pageNum = (searchParams.pageNum ?? 1) - 1
      }
      await loadData()
      return
    }
    message.error(res.data.message || t('adminUser.messages.deleteFailed'))
  } catch {
    message.error(t('adminUser.messages.deleteFailedRetry'))
  } finally {
    deletingId.value = undefined
  }
}

onMounted(() => {
  void loadData()
})
</script>

<template>
  <section class="user-management">
    <a-card class="query-section" :bordered="false">
      <h2 class="section-title">{{ $t('adminUser.title.query') }}</h2>
      <a-form layout="inline" :model="searchParams" @finish="doSearch">
        <a-form-item :label="$t('adminUser.search.userNameLabel')" name="userName">
          <a-input
            v-model:value="searchParams.userName"
            :placeholder="$t('adminUser.search.userNamePlaceholder')"
            allow-clear
            style="width: 180px"
          />
        </a-form-item>

        <a-form-item :label="$t('adminUser.search.userEmailLabel')" name="userEmail">
          <a-input
            v-model:value="searchParams.userEmail"
            :placeholder="$t('adminUser.search.userEmailPlaceholder')"
            allow-clear
            style="width: 180px"
          />
        </a-form-item>

        <a-form-item :label="$t('adminUser.search.userRoleLabel')" name="userRole">
          <a-select
            v-model:value="searchParams.userRole"
            :placeholder="$t('adminUser.search.userRolePlaceholder')"
            allow-clear
            style="width: 150px"
            :options="roleOptions"
          />
        </a-form-item>

        <a-form-item>
          <a-space>
            <a-button type="primary" html-type="submit">{{ $t('common.actions.search') }}</a-button>
            <a-button @click="doReset">{{ $t('common.actions.reset') }}</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card class="table-section" :bordered="false">
      <div class="section-header">
        <h2 class="section-title">{{ $t('adminUser.title.data') }}</h2>
        <a-button type="primary" @click="openCreateDrawer">{{ $t('adminUser.buttons.createUser') }}</a-button>
      </div>

      <a-table
        row-key="id"
        :columns="columns"
        :data-source="userList"
        :loading="loading"
        :pagination="{
          current: searchParams.pageNum,
          pageSize: searchParams.pageSize,
          total,
          showSizeChanger: true,
          showTotal: (count: number) => $t('adminUser.table.totalCount', { count }),
        }"
        :scroll="{ x: 1360 }"
        @change="onTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'userAvatar'">
            <a-avatar :src="record.userAvatar">
              {{ (record.userName || 'U').slice(0, 1).toUpperCase() }}
            </a-avatar>
          </template>

          <template v-else-if="column.dataIndex === 'userRole'">
            <a-tag v-if="record.userRole === 'admin'" color="gold">{{ $t('adminUser.role.admin') }}</a-tag>
            <a-tag v-else-if="record.userRole === 'user'" color="blue">{{ $t('adminUser.role.user') }}</a-tag>
            <span v-else>-</span>
          </template>

          <template v-else-if="column.dataIndex === 'userProfile'">
            {{ record.userProfile || '-' }}
          </template>

          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" @click="openEditDrawer(record)">{{ $t('common.actions.edit') }}</a-button>
              <a-popconfirm
                :title="$t('adminUser.confirm.deleteTitle')"
                :ok-text="$t('common.actions.confirm')"
                :cancel-text="$t('common.actions.cancel')"
                @confirm="deleteUserById(record)"
              >
                <a-button type="link" danger :loading="deletingId === record.id">{{ $t('common.actions.delete') }}</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-drawer
      v-model:open="drawerOpen"
      :title="drawerTitle"
      placement="right"
      :width="460"
      @close="closeDrawer"
    >
      <a-form layout="vertical" :model="drawerForm">
        <a-form-item :label="$t('adminUser.form.emailLabel')" :required="drawerMode === 'create'">
          <a-input
            v-model:value="drawerForm.userEmail"
            :placeholder="$t('adminUser.form.emailPlaceholder')"
            :disabled="drawerMode === 'edit'"
          />
        </a-form-item>

        <a-form-item :label="$t('adminUser.form.userNameLabel')" required>
          <a-input v-model:value="drawerForm.userName" :placeholder="$t('adminUser.form.userNamePlaceholder')" />
        </a-form-item>

        <a-form-item :label="$t('adminUser.form.avatarLabel')">
          <a-input v-model:value="drawerForm.userAvatar" :placeholder="$t('adminUser.form.avatarPlaceholder')" />
        </a-form-item>

        <a-form-item :label="$t('adminUser.form.profileLabel')">
          <a-textarea
            v-model:value="drawerForm.userProfile"
            :maxlength="200"
            :auto-size="{ minRows: 3, maxRows: 5 }"
            :placeholder="$t('adminUser.form.profilePlaceholder')"
            allow-clear
          />
        </a-form-item>

        <a-form-item :label="$t('adminUser.form.roleLabel')">
          <a-select v-model:value="drawerForm.userRole" :options="roleOptions" />
        </a-form-item>
      </a-form>

      <template #footer>
        <div class="drawer-footer">
          <a-space>
            <a-button @click="closeDrawer">{{ $t('common.actions.cancel') }}</a-button>
            <a-button type="primary" :loading="drawerSubmitting" @click="submitDrawer">{{ $t('common.actions.save') }}</a-button>
          </a-space>
        </div>
      </template>
    </a-drawer>
  </section>
</template>

<style scoped>
.user-management {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.query-section,
.table-section {
  border-radius: 12px;
}

.section-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.query-section .section-title {
  margin-bottom: 16px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
}
</style>
