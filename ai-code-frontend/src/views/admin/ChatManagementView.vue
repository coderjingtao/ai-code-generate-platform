<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
import type { TableColumnsType, TablePaginationConfig } from 'ant-design-vue'

import { listChatHistoryByPageForAdmin } from '@/api/chatHistoryController'

const router = useRouter()
const { t } = useI18n()

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

const messageTypeOptions = computed(() => [
  { label: t('adminChat.type.user'), value: 'user' },
  { label: t('adminChat.type.assistant'), value: 'assistant' },
  { label: t('adminChat.type.system'), value: 'system' },
])

const sortFieldOptions = computed(() => [
  { label: t('adminChat.sortField.id'), value: 'id' },
  { label: t('adminChat.sortField.appId'), value: 'appId' },
  { label: t('adminChat.sortField.userId'), value: 'userId' },
  { label: t('adminChat.sortField.createTime'), value: 'createTime' },
  { label: t('adminChat.sortField.updateTime'), value: 'updateTime' },
])

const sortOrderOptions = computed(() => [
  { label: t('adminChat.sortOrder.ascend'), value: 'ascend' },
  { label: t('adminChat.sortOrder.descend'), value: 'descend' },
])

const columns = computed<TableColumnsType<API.ChatHistory>>(() => [
  {
    title: t('adminChat.columns.id'),
    dataIndex: 'id',
    width: 96,
    fixed: 'left',
  },
  {
    title: t('adminChat.columns.appId'),
    dataIndex: 'appId',
    width: 110,
  },
  {
    title: t('adminChat.columns.userId'),
    dataIndex: 'userId',
    width: 110,
  },
  {
    title: t('adminChat.columns.messageType'),
    dataIndex: 'messageType',
    width: 120,
  },
  {
    title: t('adminChat.columns.message'),
    dataIndex: 'message',
    width: 520,
  },
  {
    title: t('adminChat.columns.createTime'),
    dataIndex: 'createTime',
    width: 186,
  },
  {
    title: t('adminChat.columns.updateTime'),
    dataIndex: 'updateTime',
    width: 186,
  },
  {
    title: t('adminChat.columns.action'),
    key: 'action',
    fixed: 'right',
    width: 120,
  },
])

const getMessageTypeMeta = (messageType?: string) => {
  const normalized = (messageType || '').trim().toLowerCase()
  if (['user', 'users', 'human', 'question', 'prompt'].includes(normalized)) {
    return { label: t('adminChat.type.user'), color: 'blue' }
  }
  if (['assistant', 'ai', 'bot', 'answer', 'reply'].includes(normalized)) {
    return { label: t('adminChat.type.assistant'), color: 'geekblue' }
  }
  if (normalized === 'system') {
    return { label: t('adminChat.type.system'), color: 'purple' }
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

    message.error(res.data.message || t('adminChat.messages.loadFailed'))
  } catch {
    message.error(t('adminChat.messages.loadFailedRetry'))
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
    message.warning(t('adminChat.messages.appIdMissing'))
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
      <h2 class="section-title">{{ $t('adminChat.title.query') }}</h2>
      <a-form layout="inline" class="query-form" :model="searchParams" @finish="doSearch">
        <a-form-item :label="$t('adminChat.search.appIdLabel')">
          <a-input
            v-model:value="searchParams.appId"
            allow-clear
            placeholder="appId"
            style="width: 140px"
          />
        </a-form-item>

        <a-form-item :label="$t('adminChat.search.userIdLabel')">
          <a-input-number
            v-model:value="searchParams.userId"
            :min="1"
            placeholder="userId"
            style="width: 140px"
          />
        </a-form-item>

        <a-form-item :label="$t('adminChat.search.messageTypeLabel')">
          <a-select
            v-model:value="searchParams.messageType"
            allow-clear
            :options="messageTypeOptions"
            :placeholder="$t('adminChat.search.messageTypePlaceholder')"
            style="width: 180px"
          />
        </a-form-item>

        <a-form-item :label="$t('adminChat.search.messageLabel')">
          <a-input
            v-model:value="searchParams.message"
            allow-clear
            :placeholder="$t('adminChat.search.messagePlaceholder')"
            style="width: 260px"
          />
        </a-form-item>

        <a-form-item :label="$t('adminChat.search.cursorTimeLabel')">
          <a-date-picker
            v-model:value="searchParams.lastCreateTime"
            allow-clear
            show-time
            value-format="YYYY-MM-DD HH:mm:ss"
            format="YYYY-MM-DD HH:mm:ss"
            :placeholder="$t('adminChat.search.cursorTimePlaceholder')"
            style="width: 220px"
          />
        </a-form-item>

        <a-form-item :label="$t('adminChat.search.sortFieldLabel')">
          <a-select
            v-model:value="searchParams.sortField"
            allow-clear
            style="width: 140px"
            :options="sortFieldOptions"
            :placeholder="$t('adminChat.search.sortFieldPlaceholder')"
          />
        </a-form-item>

        <a-form-item :label="$t('adminChat.search.sortOrderLabel')">
          <a-select
            v-model:value="searchParams.sortOrder"
            allow-clear
            style="width: 120px"
            :options="sortOrderOptions"
            :placeholder="$t('adminChat.search.sortOrderPlaceholder')"
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
        <h2 class="section-title">{{ $t('adminChat.title.list') }}</h2>
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
          showTotal: (count: number) => t('adminChat.messages.total', { count }),
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
            <a-button type="link" @click="openAppChat(record)">{{ $t('adminChat.buttons.viewApp') }}</a-button>
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
