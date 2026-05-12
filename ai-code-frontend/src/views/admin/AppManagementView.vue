<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { TableColumnsType, TablePaginationConfig } from 'ant-design-vue'

import { deleteAppByAdmin, listAppByPageForAdmin, updateAppByAdmin } from '@/api/appController'
import { CODE_GEN_TYPE_CONFIG } from '@/utils/codeGenTypes'

const router = useRouter()

const loading = ref(false)
const total = ref(0)
const appList = ref<API.AppVO[]>([])
const deletingId = ref<API.AppVO['id']>()
const featuringId = ref<API.AppVO['id']>()

const searchParams = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: 20,
  id: undefined,
  appName: '',
  codeGenType: '',
  priority: undefined,
  userId: undefined,
  sortField: '',
  sortOrder: '',
})

const codeGenTypeOptions = Object.values(CODE_GEN_TYPE_CONFIG).map(({ label, value }) => ({
  label,
  value,
}))

const sortFieldOptions = [
  { label: '应用 ID', value: 'id' },
  { label: '应用名称', value: 'appName' },
  { label: '优先级', value: 'priority' },
  { label: '用户 ID', value: 'userId' },
  { label: '创建时间', value: 'createTime' },
  { label: '更新时间', value: 'updateTime' },
]

const sortOrderOptions = [
  { label: '升序', value: 'ascend' },
  { label: '降序', value: 'descend' },
]

const columns: TableColumnsType<API.AppVO> = [
  {
    title: 'ID',
    dataIndex: 'id',
    width: 86,
    fixed: 'left',
  },
  {
    title: '封面',
    dataIndex: 'cover',
    width: 96,
  },
  {
    title: '应用名称',
    dataIndex: 'appName',
    width: 180,
  },
  {
    title: '部署',
    dataIndex: 'deployKey',
    width: 100,
  },
  {
    title: '优先级',
    dataIndex: 'priority',
    width: 108,
  },
  {
    title: '用户',
    key: 'user',
    width: 140,
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    width: 186,
  },
  {
    title: '操作',
    key: 'action',
    fixed: 'right',
    width: 160,
  },
]

const tableScrollX = columns.reduce(
  (totalWidth, column) => totalWidth + (typeof column.width === 'number' ? column.width : 0),
  0,
)

const loadData = async () => {
  loading.value = true
  try {
    const res = await listAppByPageForAdmin({
      ...searchParams,
      id: searchParams.id ?? undefined,
      appName: searchParams.appName?.trim() || undefined,
      codeGenType: searchParams.codeGenType?.trim() || undefined,
      sortField: searchParams.sortField?.trim() || undefined,
      sortOrder: searchParams.sortOrder?.trim() || undefined,
    })

    if (res.data.code === 0 && res.data.data) {
      appList.value = res.data.data.records ?? []
      total.value = res.data.data.totalRow ?? 0
      return
    }

    message.error(res.data.message || '获取应用列表失败')
  } catch {
    message.error('获取应用列表失败，请稍后重试')
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
  searchParams.pageSize = 20
  searchParams.id = undefined
  searchParams.appName = ''
  searchParams.codeGenType = ''
  searchParams.priority = undefined
  searchParams.userId = undefined
  searchParams.sortField = ''
  searchParams.sortOrder = ''
  void loadData()
}

const onTableChange = (pagination: TablePaginationConfig) => {
  searchParams.pageNum = pagination.current ?? 1
  searchParams.pageSize = pagination.pageSize ?? 20
  void loadData()
}

const openEditPage = (record: API.AppVO) => {
  if (!record.id) {
    message.warning('应用 ID 不存在')
    return
  }
  void router.push({ path: `/app/edit/${record.id}`, query: { admin: '1' } })
}

const openDetailPage = (record: API.AppVO) => {
  if (!record.id) {
    message.warning('应用 ID 不存在')
    return
  }
  void router.push({ path: `/app/chat/${record.id}`, query: { admin: '1' } })
}

const deleteById = async (record: API.AppVO) => {
  if (!record.id) {
    message.warning('应用 ID 不存在')
    return
  }

  deletingId.value = record.id
  try {
    const res = await deleteAppByAdmin({ id: record.id })
    if (res.data.code === 0) {
      message.success('删除应用成功')
      if ((searchParams.pageNum ?? 1) > 1 && appList.value.length === 1) {
        searchParams.pageNum = (searchParams.pageNum ?? 1) - 1
      }
      await loadData()
      return
    }
    message.error(res.data.message || '删除应用失败')
  } catch {
    message.error('删除应用失败，请稍后重试')
  } finally {
    deletingId.value = undefined
  }
}

const setFeatured = async (record: API.AppVO) => {
  if (!record.id) {
    message.warning('应用 ID 不存在')
    return
  }

  featuringId.value = record.id
  try {
    const res = await updateAppByAdmin({
      id: record.id,
      appName: record.appName,
      cover: record.cover,
      priority: 99,
    })
    if (res.data.code === 0) {
      message.success('已设为精选（优先级 99）')
      await loadData()
      return
    }
    message.error(res.data.message || '设置精选失败')
  } catch {
    message.error('设置精选失败，请稍后重试')
  } finally {
    featuringId.value = undefined
  }
}

onMounted(() => {
  void loadData()
})
</script>

<template>
  <section class="app-management">
    <a-card class="query-section" :bordered="false">
      <h2 class="section-title">查询区</h2>
      <a-form layout="inline" class="query-form" :model="searchParams" @finish="doSearch">
        <a-form-item label="ID">
          <a-input
            v-model:value="searchParams.id"
            allow-clear
            placeholder="应用 ID"
            style="width: 140px"
          />
        </a-form-item>

        <a-form-item label="名称">
          <a-input
            v-model:value="searchParams.appName"
            allow-clear
            placeholder="应用名称"
            style="width: 180px"
          />
        </a-form-item>

        <a-form-item label="生成类型">
          <a-select
            v-model:value="searchParams.codeGenType"
            allow-clear
            :options="codeGenTypeOptions"
            placeholder="请选择生成类型"
            style="width: 160px"
          />
        </a-form-item>

        <a-form-item label="优先级">
          <a-input-number
            v-model:value="searchParams.priority"
            placeholder="priority"
            style="width: 120px"
          />
        </a-form-item>

        <a-form-item label="用户 ID">
          <a-input-number
            v-model:value="searchParams.userId"
            :min="1"
            placeholder="userId"
            style="width: 120px"
          />
        </a-form-item>

        <a-form-item label="排序字段">
          <a-select
            v-model:value="searchParams.sortField"
            allow-clear
            style="width: 140px"
            :options="sortFieldOptions"
            placeholder="字段"
          />
        </a-form-item>

        <a-form-item label="排序方式">
          <a-select
            v-model:value="searchParams.sortOrder"
            allow-clear
            style="width: 120px"
            :options="sortOrderOptions"
            placeholder="方式"
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
        <h2 class="section-title">应用列表</h2>
      </div>

      <a-table
        row-key="id"
        :columns="columns"
        :data-source="appList"
        :loading="loading"
        :pagination="{
          current: searchParams.pageNum,
          pageSize: searchParams.pageSize,
          total,
          showSizeChanger: true,
          showTotal: (count: number) => `共 ${count} 条`,
          pageSizeOptions: ['20', '50', '100', '200'],
        }"
        :scroll="{ x: tableScrollX }"
        @change="onTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'cover'">
            <div class="cover-cell">
              <img v-if="record.cover" :src="record.cover" alt="cover" />
              <span v-else>-</span>
            </div>
          </template>

          <template v-else-if="column.key === 'user'">
            {{ record.user?.userName || '-' }}
          </template>

          <template v-else-if="column.dataIndex === 'deployKey'">
            <a-tag v-if="record.deployKey?.trim()" color="success">已部署</a-tag>
            <span v-else>未部署</span>
          </template>

          <template v-else-if="column.dataIndex === 'priority'">
            <a-tag v-if="record.priority === 99" color="gold">精选</a-tag>
            <span v-else>{{ record.priority ?? '-' }}</span>
          </template>

          <template v-else-if="column.key === 'action'">
            <div class="action-group">
              <a-button class="action-button" type="link" @click="openDetailPage(record)">
                详情
              </a-button>
              <a-button class="action-button" type="link" @click="openEditPage(record)">
                编辑
              </a-button>
              <a-button
                class="action-button"
                type="link"
                :loading="featuringId === record.id"
                @click="setFeatured(record)"
              >
                精选
              </a-button>
              <a-popconfirm
                title="确认删除该应用吗？"
                ok-text="确认"
                cancel-text="取消"
                @confirm="deleteById(record)"
              >
                <a-button
                  class="action-button"
                  type="link"
                  danger
                  :loading="deletingId === record.id"
                >
                  删除
                </a-button>
              </a-popconfirm>
            </div>
          </template>
        </template>
      </a-table>
    </a-card>
  </section>
</template>

<style scoped>
.app-management {
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

.query-form {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
}

.query-form :deep(.ant-form-item) {
  margin-inline-end: 18px;
  margin-bottom: 14px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.cover-cell {
  width: 52px;
  height: 52px;
  border-radius: 8px;
  background: #f1f5f9;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover-cell img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.action-group {
  display: flex;
  align-items: center;
  gap: 2px;
  white-space: nowrap;
}

.action-button {
  padding-inline: 2px;
}

.truncate-text {
  display: inline-block;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
