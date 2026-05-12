<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { TableColumnsType, TablePaginationConfig } from 'ant-design-vue'

import { listChatHistoryByPageForAdmin } from '@/api/chatHistoryController'

const router = useRouter()

const loading = ref(false)
const total = ref(0)
const chatHistoryList = ref<API.ChatHistory[]>([])

const searchParams = reactive<API.ChatHistoryQueryRequest>({
  pageNum: 1,
  pageSize: 20,
  message: '',
  messageType: '',
  appId: undefined,
  userId: undefined,
  lastCreateTime: '',
  sortField: 'createTime',
  sortOrder: 'descend',
})

const messageTypeOptions = [
  { label: '用户', value: 'user' },
  { label: 'AI', value: 'assistant' },
  { label: '系统', value: 'system' },
]

const sortFieldOptions = [
  { label: '记录 ID', value: 'id' },
  { label: '应用 ID', value: 'appId' },
  { label: '用户 ID', value: 'userId' },
  { label: '创建时间', value: 'createTime' },
  { label: '更新时间', value: 'updateTime' },
]

const sortOrderOptions = [
  { label: '升序', value: 'ascend' },
  { label: '降序', value: 'descend' },
]

const columns: TableColumnsType<API.ChatHistory> = [
  {
    title: 'ID',
    dataIndex: 'id',
    width: 96,
    fixed: 'left',
  },
  {
    title: '应用 ID',
    dataIndex: 'appId',
    width: 110,
  },
  {
    title: '用户 ID',
    dataIndex: 'userId',
    width: 110,
  },
  {
    title: '消息类型',
    dataIndex: 'messageType',
    width: 120,
  },
  {
    title: '消息内容',
    dataIndex: 'message',
    width: 520,
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    width: 186,
  },
  {
    title: '更新时间',
    dataIndex: 'updateTime',
    width: 186,
  },
  {
    title: '操作',
    key: 'action',
    fixed: 'right',
    width: 120,
  },
]

const getMessageTypeMeta = (messageType?: string) => {
  const normalized = (messageType || '').trim().toLowerCase()
  if (['user', 'users', 'human', 'question', 'prompt'].includes(normalized)) {
    return { label: '用户', color: 'blue' }
  }
  if (['assistant', 'ai', 'bot', 'answer', 'reply'].includes(normalized)) {
    return { label: 'AI', color: 'geekblue' }
  }
  if (normalized === 'system') {
    return { label: '系统', color: 'purple' }
  }
  return { label: messageType || '-', color: 'default' }
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await listChatHistoryByPageForAdmin({
      ...searchParams,
      message: searchParams.message?.trim() || undefined,
      messageType: searchParams.messageType?.trim() || undefined,
      appId: searchParams.appId ?? undefined,
      userId: searchParams.userId ?? undefined,
      lastCreateTime: searchParams.lastCreateTime?.trim() || undefined,
      sortField: searchParams.sortField?.trim() || undefined,
      sortOrder: searchParams.sortOrder?.trim() || undefined,
    })

    if (res.data.code === 0 && res.data.data) {
      chatHistoryList.value = res.data.data.records ?? []
      total.value = res.data.data.totalRow ?? 0
      return
    }

    message.error(res.data.message || '获取对话记录失败')
  } catch {
    message.error('获取对话记录失败，请稍后重试')
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
  searchParams.message = ''
  searchParams.messageType = ''
  searchParams.appId = undefined
  searchParams.userId = undefined
  searchParams.lastCreateTime = ''
  searchParams.sortField = 'createTime'
  searchParams.sortOrder = 'descend'
  void loadData()
}

const onTableChange = (pagination: TablePaginationConfig) => {
  searchParams.pageNum = pagination.current ?? 1
  searchParams.pageSize = pagination.pageSize ?? 20
  void loadData()
}

const openAppChat = (record: API.ChatHistory) => {
  if (!record.appId) {
    message.warning('应用 ID 不存在')
    return
  }
  void router.push({ path: `/app/chat/${record.appId}`, query: { admin: '1' } })
}

onMounted(() => {
  void loadData()
})
</script>

<template>
  <section class="chat-management">
    <a-card class="query-section" :bordered="false">
      <h2 class="section-title">查询区</h2>
      <a-form layout="inline" class="query-form" :model="searchParams" @finish="doSearch">
        <a-form-item label="应用 ID">
          <a-input
            v-model:value="searchParams.appId"
            allow-clear
            placeholder="appId"
            style="width: 140px"
          />
        </a-form-item>

        <a-form-item label="用户 ID">
          <a-input-number
            v-model:value="searchParams.userId"
            :min="1"
            placeholder="userId"
            style="width: 140px"
          />
        </a-form-item>

        <a-form-item label="消息类型">
          <a-select
            v-model:value="searchParams.messageType"
            allow-clear
            :options="messageTypeOptions"
            placeholder="请选择消息类型"
            style="width: 180px"
          />
        </a-form-item>

        <a-form-item label="消息内容">
          <a-input
            v-model:value="searchParams.message"
            allow-clear
            placeholder="按消息内容搜索"
            style="width: 260px"
          />
        </a-form-item>

        <a-form-item label="游标时间">
          <a-date-picker
            v-model:value="searchParams.lastCreateTime"
            allow-clear
            show-time
            value-format="YYYY-MM-DD HH:mm:ss"
            format="YYYY-MM-DD HH:mm:ss"
            placeholder="请选择游标时间"
            style="width: 220px"
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
        <h2 class="section-title">对话记录列表</h2>
      </div>

      <a-table
        row-key="id"
        :columns="columns"
        :data-source="chatHistoryList"
        :loading="loading"
        :pagination="{
          current: searchParams.pageNum,
          pageSize: searchParams.pageSize,
          total,
          showSizeChanger: true,
          showTotal: (count: number) => `共 ${count} 条`,
          pageSizeOptions: ['20', '50', '100', '200'],
        }"
        :scroll="{ x: 1600 }"
        @change="onTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'messageType'">
            <a-tag :color="getMessageTypeMeta(record.messageType).color">
              {{ getMessageTypeMeta(record.messageType).label }}
            </a-tag>
          </template>

          <template v-else-if="column.dataIndex === 'message'">
            <span class="message-preview" :title="record.message || '-'">
              {{ record.message || '-' }}
            </span>
          </template>

          <template v-else-if="column.key === 'action'">
            <a-button type="link" @click="openAppChat(record)">查看应用</a-button>
          </template>
        </template>
      </a-table>
    </a-card>
  </section>
</template>

<style scoped>
.chat-management {
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

.message-preview {
  display: inline-block;
  max-width: 460px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
