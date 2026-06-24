<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'

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
const { t } = useI18n()

const createPrompt = ref('')
const creating = ref(false)
const mySectionRef = ref<HTMLElement>()
const composerRef = ref<HTMLElement>()

const isLoggedIn = computed(() => Boolean(loginUserStore.loginUser?.id))

const inspirationPrompts = computed(() => [
  t('home.inspirationPrompts.coffee'),
  t('home.inspirationPrompts.aiProduct'),
  t('home.inspirationPrompts.musicFestival'),
  t('home.inspirationPrompts.portfolio'),
])

const styleSignals = computed(() => [
  t('home.styleSignals.brandFeel'),
  t('home.styleSignals.pageStructure'),
  t('home.styleSignals.interactions'),
  t('home.styleSignals.shipCode'),
])

const workflowSteps = computed(() => [
  {
    title: t('home.workflow.step1.title'),
    text: t('home.workflow.step1.text'),
  },
  {
    title: t('home.workflow.step2.title'),
    text: t('home.workflow.step2.text'),
  },
  {
    title: t('home.workflow.step3.title'),
    text: t('home.workflow.step3.text'),
  },
])

const showcaseNotes = computed(() => [
  t('home.showcaseNotes.event'),
  t('home.showcaseNotes.brand'),
  t('home.showcaseNotes.idea'),
])

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
    message.warning(t('home.messages.appIdMissing'))
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
    message.info(t('home.messages.notDeployed'))
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

    message.error(res.data.message || t('home.messages.loadMyFailed'))
  } catch {
    message.error(t('home.messages.loadMyError'))
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

    message.error(res.data.message || t('home.messages.loadGoodFailed'))
  } catch {
    message.error(t('home.messages.loadGoodError'))
  } finally {
    goodState.loading = false
  }
}

const handleCreateApp = async () => {
  const trimmedPrompt = createPrompt.value.trim()
  if (!trimmedPrompt) {
    message.warning(t('home.messages.promptRequired'))
    return
  }

  if (!isLoggedIn.value) {
    message.info(t('home.messages.loginBeforeCreate'))
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
    message.error(res.data.message || t('home.messages.createFailed'))
  } catch {
    message.error(t('home.messages.createError'))
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
          <p class="hero__eyebrow">{{ $t('home.hero.eyebrow') }}</p>
          <h1 class="hero__title">{{ $t('home.hero.title') }}</h1>
          <p class="hero__subtitle">
            {{ $t('home.hero.subtitle') }}
          </p>

          <div ref="composerRef" class="composer">
            <a-textarea
              v-model:value="createPrompt"
              :auto-size="{ minRows: 4, maxRows: 7 }"
              :placeholder="$t('home.composer.placeholder')"
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
                {{ $t('home.hero.startCreate') }}
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
            <span class="section-kicker">{{ $t('home.mySection.kicker') }}</span>
            <h2>{{ $t('home.mySection.title') }}</h2>
          </div>
          <div class="app-section__query">
            <a-input
              v-model:value="myState.keyword"
              allow-clear
              :placeholder="$t('home.mySection.searchPlaceholder')"
              class="app-section__search"
              @pressEnter="handleMySearch"
            />
            <a-button type="primary" @click="handleMySearch">{{ $t('common.actions.search') }}</a-button>
            <a-button @click="handleMyReset">{{ $t('common.actions.reset') }}</a-button>
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
          <a-empty v-else :description="$t('home.mySection.empty')" />
        </a-spin>

        <div class="app-section__pagination">
          <a-pagination
            :current="myState.pageNum"
            :page-size="myState.pageSize"
            :total="myState.total"
            :show-size-changer="true"
            :show-total="(count: number) => t('home.pagination.total', { count })"
            :page-size-options="['8', '12', '16', '20']"
            @change="onMyPageChange"
          />
        </div>
      </section>

      <section class="app-section">
        <div class="app-section__header">
          <div>
            <span class="section-kicker">{{ $t('home.gallery.kicker') }}</span>
            <h2>{{ $t('home.gallery.title') }}</h2>
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
          <a-empty v-else :description="$t('home.gallery.empty')" />
        </a-spin>
      </section>

      
      <section class="manifest-section">
        <div class="manifest-section__copy">
          <span class="section-kicker">{{ $t('home.manifest.kicker') }}</span>
          <h2>{{ $t('home.manifest.title') }}</h2>
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
        <span class="section-kicker">{{ $t('home.finalCta.kicker') }}</span>
        <h2>{{ $t('home.finalCta.title') }}</h2>
        <a-button type="primary" shape="round" size="large" @click="scrollToComposer">
          {{ $t('home.finalCta.backToComposer') }}
        </a-button>
      </section>
    </section>
  </section>
</template>
