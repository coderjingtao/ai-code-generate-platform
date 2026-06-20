<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'

import AppCard from '@/components/AppCard.vue'
import { DEPLOY_BASE_URL } from '@/config/env'
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
const composerRef = ref<HTMLElement>()

const isLoggedIn = computed(() => Boolean(loginUserStore.loginUser?.id))

const inspirationPrompts = [
  '做一个咖啡品牌官网，像独立杂志一样温暖高级',
  '做一个 AI 产品发布页，黑色科技感，首屏有强 CTA',
  '做一个音乐节活动页，明亮、大胆、适合移动端传播',
  '做一个个人作品集，像策展空间，突出项目和联系入口',
]

const styleSignals = ['品牌气质', '页面结构', '交互动效', '发布代码']

const workflowSteps = [
  {
    title: '写下想法',
    text: '用一句话说明行业、氛围、功能和你想打动的人。',
  },
  {
    title: '生成风格',
    text: 'AI 会把抽象想法转成页面结构、视觉语言和可继续对话的方案。',
  },
  {
    title: '继续打磨',
    text: '像和设计师协作一样，调整色彩、文案、布局和交互细节。',
  },
]

const showcaseNotes = [
  '从“做个活动页”到完整报名体验',
  '从“高级一点”到统一的品牌视觉',
  '从“我有个 idea”到可预览的网站',
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
  window.open(`${DEPLOY_BASE_URL}/${encodeURIComponent(deployKey)}`, '_blank')
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

const scrollToComposer = async () => {
  await nextTick()
  composerRef.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
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
      <div class="hero__grain" />
      <div class="hero__inner">
        <div class="hero__content">
          <p class="hero__eyebrow">NoCode AI Website Studio</p>
          <h1 class="hero__title">一句话，呈所想。</h1>
          <p class="hero__subtitle">
            把脑海里的 idea 写下来，让NoCode 把它快速变为现实。
          </p>

          <div ref="composerRef" class="composer">
            <a-textarea
              v-model:value="createPrompt"
              :auto-size="{ minRows: 4, maxRows: 7 }"
              placeholder="例如：做一个咖啡品牌官网，像独立杂志一样温暖高级，首屏有预约按钮..."
              class="composer__textarea"
            />

            <div class="composer__footer">
              <div class="composer__signals">
                <span v-for="item in styleSignals" :key="item">{{ item }}</span>
              </div>
              <a-button
                type="primary"
                shape="round"
                size="large"
                :loading="creating"
                class="composer__button"
                @click="handleCreateApp"
              >
                开始创建
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

        <div class="hero__visual" aria-hidden="true">
          <div class="hero__canvas">
            <div class="hero__browser-bar">
              <span />
              <span />
              <span />
            </div>
            <div class="hero__screenshot-container">
              <img src="@/assets/website_showcase.png" class="hero__screenshot" alt="Website Showcase" />
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="home-page__content">

      

      <section v-if="isLoggedIn" ref="mySectionRef" class="app-section">
        <div class="app-section__header">
          <div>
            <span class="section-kicker">Your studio</span>
            <h2>我的作品</h2>
          </div>
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
          <div>
            <span class="section-kicker">Gallery</span>
            <h2>精选案例</h2>
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
      </section>

      
      <section class="manifest-section">
        <div class="manifest-section__copy">
          <span class="section-kicker">Why it works</span>
          <h2>不是模板库，是你的创意现场。</h2>
        </div>
        <div class="manifest-section__notes">
          <p v-for="item in showcaseNotes" :key="item">{{ item }}</p>
        </div>
      </section>
<section class="workflow-section">
        <div v-for="(item, index) in workflowSteps" :key="item.title" class="workflow-step">
          <span>{{ String(index + 1).padStart(2, '0') }}</span>
          <h3>{{ item.title }}</h3>
          <p>{{ item.text }}</p>
        </div>
      </section>

      <section class="final-cta">
        <span class="section-kicker">Start now</span>
        <h2>把那个还没命名的想法，先写成第一句话。</h2>
        <a-button type="primary" shape="round" size="large" @click="scrollToComposer">
          回到输入框
        </a-button>
      </section>
    </section>
  </section>
</template>
