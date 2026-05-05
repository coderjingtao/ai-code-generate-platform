<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'

import AppCard from '@/components/AppCard.vue'
import { addApp, listGoodAppByPage, listMyAppByPage } from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUserStore'

interface AppListState {
  loading: boolean
  total: number
  list: API.AppVO[]
  pageNum: number
  pageSize: number
  keyword: string
}

const router = useRouter()
const route = useRoute()
const loginUserStore = useLoginUserStore()

const createPrompt = ref('')
const creating = ref(false)
const mySectionRef = ref<HTMLElement>()

const isLoggedIn = computed(() => Boolean(loginUserStore.loginUser?.id))

const inspirationPrompts = [
  '做一个电商落地页，主色青蓝，突出限时折扣',
  '做一个企业官网首页，包含产品介绍和联系我们',
  '做一个活动报名页，支持流程介绍和报名入口',
  '做一个个人作品集，强调项目案例和联系方式',
]

const myState = reactive<AppListState>({
  loading: false,
  total: 0,
  list: [],
  pageNum: 1,
  pageSize: 8,
  keyword: '',
})

const goodState = reactive<AppListState>({
  loading: false,
  total: 0,
  list: [],
  pageNum: 1,
  pageSize: 8,
  keyword: '',
})

const goToChat = (record: API.AppVO, admin = false) => {
  if (!record.id) {
    message.warning('应用 ID 不存在')
    return
  }

  void router.push({
    path: `/app/chat/${record.id}`,
    query: admin ? { admin: '1' } : undefined,
  })
}

const goToWork = (record: API.AppVO) => {
  const deployKey = record.deployKey?.trim()
  if (!deployKey) {
    message.info('该应用暂未部署作品')
    return
  }
  window.open(`http://localhost:8080/${encodeURIComponent(deployKey)}`, '_blank')
}

const goToLogin = () => {
  void router.push(`/user/login?redirect=${encodeURIComponent(window.location.href)}`)
}

const loadMyApps = async () => {
  if (!isLoggedIn.value) {
    myState.list = []
    myState.total = 0
    return
  }

  myState.loading = true
  try {
    const res = await listMyAppByPage({
      pageNum: myState.pageNum,
      pageSize: Math.min(myState.pageSize, 20),
      appName: myState.keyword.trim() || undefined,
    })

    if (res.data.code === 0 && res.data.data) {
      myState.list = res.data.data.records ?? []
      myState.total = res.data.data.totalRow ?? 0
      return
    }

    message.error(res.data.message || '获取我的作品失败')
  } catch {
    message.error('获取我的作品失败，请稍后重试')
  } finally {
    myState.loading = false
  }
}

const loadGoodApps = async () => {
  goodState.loading = true
  try {
    const res = await listGoodAppByPage({
      pageNum: goodState.pageNum,
      pageSize: Math.min(goodState.pageSize, 20),
      appName: goodState.keyword.trim() || undefined,
      sortField: 'createTime',
      sortOrder: 'desc',
    })

    if (res.data.code === 0 && res.data.data) {
      goodState.list = res.data.data.records ?? []
      goodState.total = res.data.data.totalRow ?? 0
      return
    }

    message.error(res.data.message || '获取精选案例失败')
  } catch {
    message.error('获取精选案例失败，请稍后重试')
  } finally {
    goodState.loading = false
  }
}

const handleCreateApp = async () => {
  const trimmedPrompt = createPrompt.value.trim()
  if (!trimmedPrompt) {
    message.warning('请输入应用提示词')
    return
  }

  if (!isLoggedIn.value) {
    message.info('请先登录后再创建应用')
    goToLogin()
    return
  }

  creating.value = true
  try {
    const res = await addApp({ initPrompt: trimmedPrompt })
    if (res.data.code === 0 && res.data.data) {
      await router.push({
        path: `/app/chat/${res.data.data}`,
        query: { initPrompt: trimmedPrompt },
      })
      return
    }
    message.error(res.data.message || '创建应用失败')
  } catch {
    message.error('创建应用失败，请稍后重试')
  } finally {
    creating.value = false
  }
}

const handleMySearch = () => {
  myState.pageNum = 1
  void loadMyApps()
}

const handleMyReset = () => {
  myState.keyword = ''
  myState.pageNum = 1
  myState.pageSize = 8
  void loadMyApps()
}

const handleGoodSearch = () => {
  goodState.pageNum = 1
  void loadGoodApps()
}

const handleGoodReset = () => {
  goodState.keyword = ''
  goodState.pageNum = 1
  goodState.pageSize = 8
  void loadGoodApps()
}

const onMyPageChange = (page: number, pageSize: number) => {
  myState.pageNum = page
  myState.pageSize = pageSize
  void loadMyApps()
}

const onGoodPageChange = (page: number, pageSize: number) => {
  goodState.pageNum = page
  goodState.pageSize = pageSize
  void loadGoodApps()
}

const usePromptTemplate = (prompt: string) => {
  createPrompt.value = prompt
}

const patchCreatorInfo = (app: API.AppVO): API.AppVO => {
  if (app.user?.userName || app.user?.userAvatar) {
    return app
  }

  const loginUser = loginUserStore.loginUser
  if (loginUser?.id && app.userId === loginUser.id) {
    return {
      ...app,
      user: {
        id: loginUser.id,
        userName: loginUser.userName,
        userAvatar: loginUser.userAvatar,
      },
    }
  }

  return app
}

const myDisplayApps = computed(() => myState.list.map(patchCreatorInfo))
const goodDisplayApps = computed(() => goodState.list.map(patchCreatorInfo))

const scrollToMySection = async () => {
  await nextTick()
  mySectionRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

watch(isLoggedIn, (loggedIn) => {
  if (loggedIn) {
    void loadMyApps()
    return
  }
  myState.list = []
  myState.total = 0
})

watch(
  () => route.query.tab,
  (tab) => {
    if (tab === 'my' && isLoggedIn.value) {
      void scrollToMySection()
    }
  },
)

onMounted(() => {
  void loadGoodApps()
  if (isLoggedIn.value) {
    void loadMyApps()
  }
})
</script>

<template>
  <section class="home-page">
    <section class="hero">
      <div class="hero__glow hero__glow--left" />
      <div class="hero__glow hero__glow--right" />

      <div class="hero__content">
        <h1 class="hero__title">
          一句话
          <span class="hero__title-mark">呈所想</span>
        </h1>
        <p class="hero__subtitle">与 AI 对话，快速创建你的网站应用</p>

        <div class="composer">
          <a-textarea
            v-model:value="createPrompt"
            :auto-size="{ minRows: 4, maxRows: 7 }"
            placeholder="使用 NoCode 创建一个有趣的小游戏，玩法是..."
            class="composer__textarea"
          />

          <div class="composer__footer">
            <span class="composer__hint">支持自然语言描述你的产品目标、页面风格和功能需求</span>
            <a-button
              type="primary"
              shape="round"
              size="large"
              :loading="creating"
              @click="handleCreateApp"
            >
              开始生成
            </a-button>
          </div>
        </div>

        <div class="hero__suggestions">
          <button
            v-for="item in inspirationPrompts"
            :key="item"
            type="button"
            class="hero__chip"
            @click="usePromptTemplate(item)"
          >
            {{ item }}
          </button>
        </div>
      </div>
    </section>

    <section class="home-page__content">
      <section v-if="isLoggedIn" ref="mySectionRef" class="app-section">
        <div class="app-section__header">
          <h2>我的作品</h2>
          <div class="app-section__query">
            <a-input
              v-model:value="myState.keyword"
              allow-clear
              placeholder="按应用名称搜索"
              class="app-section__search"
              @pressEnter="handleMySearch"
            />
            <a-button type="primary" @click="handleMySearch">查询</a-button>
            <a-button @click="handleMyReset">重置</a-button>
          </div>
        </div>

        <a-spin :spinning="myState.loading">
          <div v-if="myDisplayApps.length" class="app-grid">
            <AppCard
              v-for="item in myDisplayApps"
              :key="item.id"
              :app="item"
              @view-chat="goToChat"
              @view-work="goToWork"
            />
          </div>
          <a-empty v-else description="暂无作品，先从上方输入提示词创建一个吧" />
        </a-spin>

        <div class="app-section__pagination">
          <a-pagination
            :current="myState.pageNum"
            :page-size="myState.pageSize"
            :total="myState.total"
            :show-size-changer="true"
            :show-total="(count: number) => `共 ${count} 条`"
            :page-size-options="['8', '12', '16', '20']"
            @change="onMyPageChange"
          />
        </div>
      </section>

      <section class="app-section">
        <div class="app-section__header">
          <h2>精选案例</h2>
          <div class="app-section__query">
            <a-input
              v-model:value="goodState.keyword"
              allow-clear
              placeholder="按应用名称搜索"
              class="app-section__search"
              @pressEnter="handleGoodSearch"
            />
            <a-button type="primary" @click="handleGoodSearch">查询</a-button>
            <a-button @click="handleGoodReset">重置</a-button>
          </div>
        </div>

        <a-spin :spinning="goodState.loading">
          <div v-if="goodDisplayApps.length" class="app-grid">
            <AppCard
              v-for="item in goodDisplayApps"
              :key="item.id"
              :app="item"
              @view-chat="goToChat"
              @view-work="goToWork"
            />
          </div>
          <a-empty v-else description="暂无精选案例" />
        </a-spin>

        <div class="app-section__pagination">
          <a-pagination
            :current="goodState.pageNum"
            :page-size="goodState.pageSize"
            :total="goodState.total"
            :show-size-changer="true"
            :show-total="(count: number) => `共 ${count} 条`"
            :page-size-options="['8', '12', '16', '20']"
            @change="onGoodPageChange"
          />
        </div>
      </section>
    </section>
  </section>
</template>

<style scoped>
.home-page {
  min-height: 100%;
  background: linear-gradient(164deg, #f7fbff 0%, #d6f4ff 42%, #b5dbff 100%);
}

.hero {
  position: relative;
  overflow: hidden;
  padding: 84px 24px 130px;
}

.hero__content {
  position: relative;
  z-index: 2;
  width: min(980px, 100%);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  animation: fade-up 0.7s ease;
}

.hero__title {
  margin: 0;
  font-size: clamp(40px, 5.2vw, 74px);
  line-height: 1.08;
  letter-spacing: 1px;
  color: #0f172a;
  font-weight: 700;
}

.hero__title-mark {
  color: #0b4abf;
}

.hero__subtitle {
  margin: 14px 0 0;
  color: rgba(15, 23, 42, 0.66);
  font-size: 19px;
}

.composer {
  width: min(860px, 100%);
  margin-top: 40px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 30px 60px rgba(14, 116, 144, 0.12);
  padding: 18px 18px 14px;
}

.composer__textarea :deep(textarea) {
  border: 0;
  background: transparent;
  box-shadow: none;
  font-size: 20px;
  line-height: 1.58;
  color: #0f172a;
  padding: 8px;
}

.composer__textarea :deep(textarea::placeholder) {
  color: rgba(15, 23, 42, 0.3);
}

.composer__footer {
  margin-top: 12px;
  padding: 4px 8px 2px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.composer__hint {
  color: rgba(15, 23, 42, 0.52);
  font-size: 13px;
}

.hero__suggestions {
  margin-top: 20px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: center;
}

.hero__chip {
  border: 0;
  border-radius: 999px;
  padding: 10px 16px;
  font-size: 13px;
  cursor: pointer;
  color: rgba(15, 23, 42, 0.72);
  background: rgba(255, 255, 255, 0.74);
  transition: all 0.22s ease;
}

.hero__chip:hover {
  transform: translateY(-2px);
  background: #fff;
  color: #0f172a;
}

.hero__glow {
  position: absolute;
  border-radius: 999px;
  filter: blur(4px);
}

.hero__glow--left {
  width: 460px;
  height: 460px;
  left: -130px;
  top: 54px;
  background: radial-gradient(circle, rgba(34, 211, 238, 0.3) 0%, rgba(34, 211, 238, 0) 72%);
}

.hero__glow--right {
  width: 540px;
  height: 540px;
  right: -170px;
  bottom: -120px;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.38) 0%, rgba(59, 130, 246, 0) 74%);
}

.home-page__content {
  position: relative;
  background: #f8fbff;
  border-radius: 34px 34px 0 0;
  margin-top: -56px;
  padding: 42px 24px 52px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.app-section {
  width: min(1240px, 100%);
  margin: 0 auto;
  padding: 26px;
  border-radius: 24px;
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.08);
  animation: fade-up 0.55s ease;
}

.app-section__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.app-section__header h2 {
  margin: 0;
  font-size: 28px;
  color: #0f172a;
  letter-spacing: 0.3px;
}

.app-section__query {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.app-section__search {
  width: 230px;
}

.app-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.app-section__pagination {
  margin-top: 22px;
  display: flex;
  justify-content: flex-end;
}

@keyframes fade-up {
  from {
    transform: translateY(16px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

@media (max-width: 1320px) {
  .app-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .hero {
    padding: 58px 16px 104px;
  }

  .hero__subtitle {
    font-size: 16px;
  }

  .composer {
    border-radius: 22px;
    padding: 14px;
  }

  .composer__textarea :deep(textarea) {
    font-size: 16px;
    line-height: 1.56;
  }

  .composer__footer {
    flex-direction: column;
    align-items: flex-start;
  }

  .home-page__content {
    margin-top: -44px;
    border-radius: 22px 22px 0 0;
    padding: 24px 12px 30px;
  }

  .app-section {
    padding: 16px;
    border-radius: 18px;
  }

  .app-section__header {
    flex-direction: column;
    align-items: stretch;
  }

  .app-section__header h2 {
    font-size: 22px;
  }

  .app-section__query {
    justify-content: flex-start;
  }

  .app-section__search {
    width: 100%;
  }

  .app-grid {
    grid-template-columns: repeat(1, minmax(0, 1fr));
  }

  .app-section__pagination {
    justify-content: center;
  }
}
</style>
