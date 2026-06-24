<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
import type { TableColumnsType, TablePaginationConfig } from 'ant-design-vue'

import { deleteAppByAdmin, listAppByPageForAdmin, updateAppByAdmin } from '@/api/appController'
import { CODE_GEN_TYPE_CONFIG } from '@/utils/codeGenTypes'

const router = useRouter()
const { t } = useI18n()

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

const sortFieldOptions = computed(() => [
  { label: t('adminApp.sortField.id'), value: 'id' },
  { label: t('adminApp.sortField.appName'), value: 'appName' },
  { label: t('adminApp.sortField.priority'), value: 'priority' },
  { label: t('adminApp.sortField.userId'), value: 'userId' },
  { label: t('adminApp.sortField.createTime'), value: 'createTime' },
  { label: t('adminApp.sortField.updateTime'), value: 'updateTime' },
])

const sortOrderOptions = computed(() => [
  { label: t('adminApp.sortOrder.ascend'), value: 'ascend' },
  { label: t('adminApp.sortOrder.descend'), value: 'descend' },
])

const columns = computed<TableColumnsType<API.AppVO>>(() => [
  {
    title: t('adminApp.columns.id'),
    dataIndex: 'id',
    width: 86,
    fixed: 'left',
  },
  {
    title: t('adminApp.columns.cover'),
    dataIndex: 'cover',
    width: 96,
  },
  {
    title: t('adminApp.columns.appName'),
    dataIndex: 'appName',
    width: 180,
  },
  {
    title: t('adminApp.columns.deployKey'),
    dataIndex: 'deployKey',
    width: 100,
  },
  {
    title: t('adminApp.columns.priority'),
    dataIndex: 'priority',
    width: 108,
  },
  {
    title: t('adminApp.columns.user'),
    key: 'user',
    width: 140,
  },
  {
    title: t('adminApp.columns.createTime'),
    dataIndex: 'createTime',
    width: 186,
  },
  {
    title: t('adminApp.columns.action'),
    key: 'action',
    fixed: 'right',
    width: 160,
  },
])

const tableScrollX = computed(() =>
  columns.value.reduce(
    (totalWidth, column) => totalWidth + (typeof column.width === 'number' ? column.width : 0),
    0,
  ),
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

    message.error(res.data.message || t('adminApp.messages.loadFailed'))
  } catch {
    message.error(t('adminApp.messages.loadFailedRetry'))
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
    message.warning(t('adminApp.messages.appIdMissing'))
    return
  }
  void router.push({ path: `/app/edit/${record.id}`, query: { admin: '1' } })
}

const openDetailPage = (record: API.AppVO) => {
  if (!record.id) {
    message.warning(t('adminApp.messages.appIdMissing'))
    return
  }
  void router.push({ path: `/app/chat/${record.id}`, query: { admin: '1' } })
}

const deleteById = async (record: API.AppVO) => {
  if (!record.id) {
    message.warning(t('adminApp.messages.appIdMissing'))
    return
  }

  deletingId.value = record.id
  try {
    const res = await deleteAppByAdmin({ id: record.id })
    if (res.data.code === 0) {
      message.success(t('adminApp.messages.deleteSuccess'))
      if ((searchParams.pageNum ?? 1) > 1 && appList.value.length === 1) {
        searchParams.pageNum = (searchParams.pageNum ?? 1) - 1
      }
      await loadData()
      return
    }
    message.error(res.data.message || t('adminApp.messages.deleteFailed'))
  } catch {
    message.error(t('adminApp.messages.deleteFailedRetry'))
  } finally {
    deletingId.value = undefined
  }
}

const setFeatured = async (record: API.AppVO) => {
  if (!record.id) {
    message.warning(t('adminApp.messages.appIdMissing'))
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
      message.success(t('adminApp.messages.featureSuccess'))
      await loadData()
      return
    }
    message.error(res.data.message || t('adminApp.messages.featureFailed'))
  } catch {
    message.error(t('adminApp.messages.featureFailedRetry'))
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
      <h2 class="section-title">{{ $t('adminApp.title.query') }}</h2>
      <a-form layout="inline" class="query-form" :model="searchParams" @finish="doSearch">
        <a-form-item :label="$t('adminApp.search.idLabel')">
          <a-input
            v-model:value="searchParams.id"
            allow-clear
            :placeholder="$t('adminApp.search.idPlaceholder')"
            style="width: 140px"
          />
        </a-form-item>

        <a-form-item :label="$t('adminApp.search.nameLabel')">
          <a-input
            v-model:value="searchParams.appName"
            allow-clear
            :placeholder="$t('adminApp.search.namePlaceholder')"
            style="width: 180px"
          />
        </a-form-item>

        <a-form-item :label="$t('adminApp.search.codeGenTypeLabel')">
          <a-select
            v-model:value="searchParams.codeGenType"
            allow-clear
            :options="codeGenTypeOptions"
            :placeholder="$t('adminApp.search.codeGenTypePlaceholder')"
            style="width: 160px"
          />
        </a-form-item>

        <a-form-item :label="$t('adminApp.search.priorityLabel')">
          <a-input-number
            v-model:value="searchParams.priority"
            :placeholder="$t('adminApp.search.priorityPlaceholder')"
            style="width: 120px"
          />
        </a-form-item>

        <a-form-item :label="$t('adminApp.search.userIdLabel')">
          <a-input-number
            v-model:value="searchParams.userId"
            :min="1"
            :placeholder="$t('adminApp.search.userIdPlaceholder')"
            style="width: 120px"
          />
        </a-form-item>

        <a-form-item :label="$t('adminApp.search.sortFieldLabel')">
          <a-select
            v-model:value="searchParams.sortField"
            allow-clear
            style="width: 140px"
            :options="sortFieldOptions"
            :placeholder="$t('adminApp.search.sortFieldPlaceholder')"
          />
        </a-form-item>

        <a-form-item :label="$t('adminApp.search.sortOrderLabel')">
          <a-select
            v-model:value="searchParams.sortOrder"
            allow-clear
            style="width: 120px"
            :options="sortOrderOptions"
            :placeholder="$t('adminApp.search.sortOrderPlaceholder')"
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
        <h2 class="section-title">{{ $t('adminApp.title.appList') }}</h2>
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
          showTotal: (count: number) => $t('adminApp.pagination.total', { count }),
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
            <a-tag v-if="record.deployKey?.trim()" color="success">{{ $t('adminApp.tags.deployed') }}</a-tag>
            <span v-else>{{ $t('adminApp.tags.notDeployed') }}</span>
          </template>

          <template v-else-if="column.dataIndex === 'priority'">
            <a-tag v-if="record.priority === 99" color="gold">{{ $t('adminApp.tags.featured') }}</a-tag>
            <span v-else>{{ record.priority ?? '-' }}</span>
          </template>

          <template v-else-if="column.key === 'action'">
            <div class="action-group">
              <a-button class="action-button" type="link" @click="openDetailPage(record)">
                {{ $t('adminApp.buttons.detail') }}
              </a-button>
              <a-button class="action-button" type="link" @click="openEditPage(record)">
                {{ $t('common.actions.edit') }}
              </a-button>
              <a-button
                class="action-button"
                type="link"
                :loading="featuringId === record.id"
                @click="setFeatured(record)"
              >
                {{ $t('adminApp.buttons.feature') }}
              </a-button>
              <a-popconfirm
                :title="$t('adminApp.confirm.deleteTitle')"
                :ok-text="$t('common.actions.confirm')"
                :cancel-text="$t('common.actions.cancel')"
                @confirm="deleteById(record)"
              >
                <a-button
                  class="action-button"
                  type="link"
                  danger
                  :loading="deletingId === record.id"
                >
                  {{ $t('common.actions.delete') }}
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
