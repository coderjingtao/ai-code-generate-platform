<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { TableColumnsType, TablePaginationConfig } from 'ant-design-vue'

import { addUser, deleteUser, listUserVoByPage, updateUser } from '@/api/usersController'

interface UserDrawerForm {
  id?: number
  userAccount: string
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
  userAccount: '',
  userRole: undefined,
})

const roleOptions = [
  { label: '普通用户', value: 'user' },
  { label: '管理员', value: 'admin' },
]

const drawerForm = reactive<UserDrawerForm>({
  id: undefined,
  userAccount: '',
  userName: '',
  userAvatar: '',
  userProfile: '',
  userRole: 'user',
})

const drawerTitle = computed(() => (drawerMode.value === 'create' ? '创建用户' : '编辑用户'))

const columns: TableColumnsType<API.UserVO> = [
  {
    title: 'ID',
    dataIndex: 'id',
    width: 90,
  },
  {
    title: '头像',
    dataIndex: 'userAvatar',
    width: 90,
  },
  {
    title: '用户名',
    dataIndex: 'userName',
    width: 180,
  },
  {
    title: '账号',
    dataIndex: 'userAccount',
    width: 180,
  },
  {
    title: '简介',
    dataIndex: 'userProfile',
  },
  {
    title: '角色',
    dataIndex: 'userRole',
    width: 120,
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    width: 220,
  },
  {
    title: '操作',
    key: 'action',
    width: 170,
  },
]

const resetDrawerForm = () => {
  drawerForm.id = undefined
  drawerForm.userAccount = ''
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
      userAccount: searchParams.userAccount?.trim() || undefined,
      userRole: searchParams.userRole || undefined,
    })

    if (res.data.code === 0 && res.data.data) {
      userList.value = res.data.data.records ?? []
      total.value = res.data.data.totalRow ?? 0
      return
    }

    message.error(res.data.message || '获取用户列表失败')
  } catch {
    message.error('获取用户列表失败，请稍后重试')
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
  searchParams.userAccount = ''
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
    message.warning('未找到用户 ID，无法编辑')
    return
  }

  drawerMode.value = 'edit'
  drawerForm.id = record.id
  drawerForm.userAccount = record.userAccount || ''
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
  const trimmedUserAccount = drawerForm.userAccount.trim()

  if (!trimmedUserName) {
    message.warning('请输入用户名')
    return
  }

  if (drawerMode.value === 'create' && !trimmedUserAccount) {
    message.warning('请输入账号')
    return
  }

  if (drawerMode.value === 'edit' && !drawerForm.id) {
    message.warning('用户 ID 不存在，无法提交')
    return
  }

  drawerSubmitting.value = true
  try {
    if (drawerMode.value === 'create') {
      const res = await addUser({
        userAccount: trimmedUserAccount,
        userName: trimmedUserName,
        userAvatar: drawerForm.userAvatar.trim() || undefined,
        userProfile: drawerForm.userProfile.trim() || undefined,
        userRole: drawerForm.userRole || 'user',
      })

      if (res.data.code === 0) {
        message.success('创建用户成功')
        drawerOpen.value = false
        searchParams.pageNum = 1
        await loadData()
        return
      }

      message.error(res.data.message || '创建用户失败')
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
      message.success('编辑用户成功')
      drawerOpen.value = false
      await loadData()
      return
    }

    message.error(res.data.message || '编辑用户失败')
  } catch {
    message.error(drawerMode.value === 'create' ? '创建用户失败，请稍后重试' : '编辑用户失败，请稍后重试')
  } finally {
    drawerSubmitting.value = false
  }
}

const deleteUserById = async (record: API.UserVO) => {
  if (!record.id) {
    message.warning('未找到用户 ID，无法删除')
    return
  }

  deletingId.value = record.id
  try {
    const res = await deleteUser({ id: record.id })
    if (res.data.code === 0) {
      message.success('删除用户成功')
      if ((searchParams.pageNum ?? 1) > 1 && userList.value.length === 1) {
        searchParams.pageNum = (searchParams.pageNum ?? 1) - 1
      }
      await loadData()
      return
    }
    message.error(res.data.message || '删除用户失败')
  } catch {
    message.error('删除用户失败，请稍后重试')
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
      <h2 class="section-title">查询区</h2>
      <a-form layout="inline" :model="searchParams" @finish="doSearch">
        <a-form-item label="用户名" name="userName">
          <a-input
            v-model:value="searchParams.userName"
            placeholder="请输入用户名"
            allow-clear
            style="width: 180px"
          />
        </a-form-item>

        <a-form-item label="账号" name="userAccount">
          <a-input
            v-model:value="searchParams.userAccount"
            placeholder="请输入账号"
            allow-clear
            style="width: 180px"
          />
        </a-form-item>

        <a-form-item label="角色" name="userRole">
          <a-select
            v-model:value="searchParams.userRole"
            placeholder="请选择角色"
            allow-clear
            style="width: 150px"
            :options="roleOptions"
          />
        </a-form-item>

        <a-form-item>
          <a-space>
            <a-button type="primary" html-type="submit">查询</a-button>
            <a-button @click="doReset">重置</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card class="table-section" :bordered="false">
      <div class="section-header">
        <h2 class="section-title">数据展示区</h2>
        <a-button type="primary" @click="openCreateDrawer">创建用户</a-button>
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
          showTotal: (count: number) => `共 ${count} 条`,
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
            <a-tag v-if="record.userRole === 'admin'" color="gold">管理员</a-tag>
            <a-tag v-else-if="record.userRole === 'user'" color="blue">普通用户</a-tag>
            <span v-else>-</span>
          </template>

          <template v-else-if="column.dataIndex === 'userProfile'">
            {{ record.userProfile || '-' }}
          </template>

          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" @click="openEditDrawer(record)">编辑</a-button>
              <a-popconfirm
                title="确认删除该用户吗？"
                ok-text="确认"
                cancel-text="取消"
                @confirm="deleteUserById(record)"
              >
                <a-button type="link" danger :loading="deletingId === record.id">删除</a-button>
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
        <a-form-item label="账号" :required="drawerMode === 'create'">
          <a-input
            v-model:value="drawerForm.userAccount"
            placeholder="请输入账号"
            :disabled="drawerMode === 'edit'"
          />
        </a-form-item>

        <a-form-item label="用户名" required>
          <a-input v-model:value="drawerForm.userName" placeholder="请输入用户名" />
        </a-form-item>

        <a-form-item label="头像链接">
          <a-input v-model:value="drawerForm.userAvatar" placeholder="请输入头像 URL" />
        </a-form-item>

        <a-form-item label="简介">
          <a-textarea
            v-model:value="drawerForm.userProfile"
            :maxlength="200"
            :auto-size="{ minRows: 3, maxRows: 5 }"
            placeholder="请输入用户简介"
            allow-clear
          />
        </a-form-item>

        <a-form-item label="角色">
          <a-select v-model:value="drawerForm.userRole" :options="roleOptions" />
        </a-form-item>
      </a-form>

      <template #footer>
        <div class="drawer-footer">
          <a-space>
            <a-button @click="closeDrawer">取消</a-button>
            <a-button type="primary" :loading="drawerSubmitting" @click="submitDrawer">保存</a-button>
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
