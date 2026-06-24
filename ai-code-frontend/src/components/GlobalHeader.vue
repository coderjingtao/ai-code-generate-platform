<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'

import logoUrl from '@/assets/logo.png'
import { userLogout } from '@/api/usersController'
import type { GlobalMenuItem } from '@/config/navigation'
import { useLoginUserStore } from '@/stores/loginUserStore'
import { useLocaleStore } from '@/stores/localeStore'

const loginUserStore = useLoginUserStore()
const localeStore = useLocaleStore()
const { t, locale } = useI18n()
const props = defineProps<{
  menuItems: GlobalMenuItem[]
}>()

const route = useRoute()
const router = useRouter()

const isLoggedIn = computed(() => Boolean(loginUserStore.loginUser?.id))
const isAdmin = computed(() => loginUserStore.loginUser?.userRole === 'admin')
const username = computed(() => loginUserStore.loginUser?.userName || 'User')
const userAvatar = computed(() => loginUserStore.loginUser?.userAvatar || '')
const isHomePage = computed(() => route.path === '/')

// 把导航项的 labelKey 翻译成当前语言的 label 再交给 a-menu
const translatedMenuItems = computed(() =>
  props.menuItems.map((item) => ({ ...item, label: t(item.labelKey) })),
)

// 触发按钮显示「点击后将切换到的目标语言」：中文界面显示 English，反之显示 中文
const localeLabel = computed(() => (locale.value === 'zh' ? 'English' : '中文'))

const selectedKeys = computed(() => {
  const activeItem = props.menuItems.find((item) => {
    if (item.path === '/') {
      return route.path === '/'
    }

    return route.path === item.path || route.path.startsWith(`${item.path}/`)
  })

  return activeItem ? [activeItem.key] : []
})

const handleMenuClick = ({ key }: { key: string }) => {
  const targetItem = props.menuItems.find((item) => item.key === key)
  if (targetItem && targetItem.path !== route.path) {
    void router.push(targetItem.path)
  }
}

const handleUserMenuClick = async ({ key }: { key: string }) => {
  if (key === 'myApps') {
    await router.push({ path: '/', query: { tab: 'my' } })
    return
  }

  if (key === 'userManagement') {
    await router.push('/admin/userManagement')
    return
  }

  if (key === 'appManagement') {
    await router.push('/admin/appManagement')
    return
  }

  if (key === 'chatManagement') {
    await router.push('/admin/chatManagement')
    return
  }

  if (key === 'logout') {
    try {
      const res = await userLogout()
      if (res.data.code === 0) {
        loginUserStore.setLoginUser({})
        message.success(t('common.header.logoutSuccess'))
        await router.replace('/')
        return
      }
      message.error(res.data.message || t('common.header.logoutFailed'))
    } catch {
      message.error(t('common.header.logoutFailed'))
    }
    return
  }

  message.info(t('common.header.featureInDev'))
}

const goToHome = () => {
  void router.push('/')
}

const goToLoginPage = () => {
  void router.push('/user/login')
}

const goToRegisterPage = () => {
  void router.push('/user/register')
}
</script>

<template>
  <a-layout-header :class="['global-header', { 'global-header--home': isHomePage }]">
    <div class="global-header__inner">
      <button type="button" class="global-header__brand" @click="goToHome">
        <img :src="logoUrl" alt="NoCode" class="global-header__logo" />
        <span class="global-header__title">NoCode</span>
      </button>

      <div class="global-header__menu-wrap">
        <a-menu
          mode="horizontal"
          :selected-keys="selectedKeys"
          :items="translatedMenuItems"
          @click="handleMenuClick"
        />
      </div>

      <button
        type="button"
        class="global-header__lang-trigger"
        :title="$t('common.header.language')"
        @click="localeStore.toggleLocale()"
      >
        <span class="global-header__lang-icon">🌐</span>
        <span class="global-header__lang-label">{{ localeLabel }}</span>
      </button>

      <a-dropdown v-if="isLoggedIn" :trigger="['click']">
        <button type="button" class="global-header__user-trigger">
          <a-avatar :size="36" :src="userAvatar" class="global-header__avatar">
            {{ username.slice(0, 1).toUpperCase() }}
          </a-avatar>
          <span class="global-header__username">{{ username }}</span>
          <span class="global-header__caret">▾</span>
        </button>
        <template #overlay>
          <a-menu @click="handleUserMenuClick">
            <a-menu-item key="myApps">{{ $t('common.header.myApps') }}</a-menu-item>
            <a-menu-item v-if="isAdmin" key="appManagement">{{
              $t('common.header.appManagement')
            }}</a-menu-item>
            <a-menu-item v-if="isAdmin" key="chatManagement">{{
              $t('common.header.chatManagement')
            }}</a-menu-item>
            <a-menu-item v-if="isAdmin" key="userManagement">{{
              $t('common.header.userManagement')
            }}</a-menu-item>
            <a-menu-divider />
            <a-menu-item key="logout">{{ $t('common.header.logout') }}</a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>

      <div v-else class="global-header__auth-actions">
        <a-button type="text" @click="goToRegisterPage">{{ $t('common.header.register') }}</a-button>
        <a-button type="primary" shape="round" @click="goToLoginPage">{{
          $t('common.header.login')
        }}</a-button>
      </div>
    </div>
  </a-layout-header>
</template>

<style scoped>
.global-header {
  height: auto;
  min-height: var(--ac-header-height);
  line-height: normal;
  padding: 0 28px;
  position: sticky;
  top: 0;
  z-index: 10;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--ac-border);
}

.global-header--home {
  background: rgba(16, 17, 20, 0.92);
  border-bottom: none;
  backdrop-filter: blur(18px);
}

.global-header__inner {
  display: flex;
  align-items: center;
  gap: 22px;
  min-height: var(--ac-header-height);
  width: 100%;
}

.global-header__brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: max-content;
  border: 0;
  background: transparent;
  padding: 0;
  cursor: pointer;
}

.global-header__logo {
  width: 36px;
  height: 36px;
  object-fit: contain;
}

.global-header__title {
  color: var(--ac-text);
  font-size: 32px;
  font-weight: 700;
  letter-spacing: 0;
}

.global-header--home .global-header__title {
  color: var(--ac-text-inverse);
}

.global-header__menu-wrap {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.global-header__menu-wrap :deep(.ant-menu) {
  min-width: 0;
  border-bottom: none;
  background: transparent;
  color: var(--ac-text-muted);
  font-weight: 500;
}

.global-header__menu-wrap :deep(.ant-menu-item-selected) {
  color: var(--ac-text);
}

.global-header--home .global-header__menu-wrap :deep(.ant-menu) {
  color: var(--ac-text-inverse-muted);
}

.global-header--home .global-header__menu-wrap :deep(.ant-menu-item-selected),
.global-header--home .global-header__menu-wrap :deep(.ant-menu-item:hover) {
  color: var(--ac-text-inverse);
}

.global-header__menu-wrap :deep(.ant-menu-horizontal > .ant-menu-item::after),
.global-header__menu-wrap :deep(.ant-menu-horizontal > .ant-menu-submenu::after) {
  border-bottom: none !important;
}

.global-header__lang-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-pill);
  padding: 4px 12px;
  background: var(--ac-surface);
  color: var(--ac-text);
  cursor: pointer;
  min-width: max-content;
  font-size: 14px;
}

.global-header__lang-trigger:hover {
  border-color: var(--ac-primary-border);
}

.global-header--home .global-header__lang-trigger {
  border-color: var(--ac-border-inverse);
  background: rgba(255, 248, 235, 0.1);
  color: var(--ac-text-inverse);
}

.global-header__lang-icon {
  font-size: 15px;
  line-height: 1;
}

.global-header__user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-pill);
  padding: 4px 12px 4px 4px;
  background: var(--ac-surface);
  color: var(--ac-text);
  cursor: pointer;
  min-width: max-content;
}

.global-header__user-trigger:hover {
  border-color: var(--ac-primary-border);
}

.global-header--home .global-header__user-trigger {
  border-color: var(--ac-border-inverse);
  background: rgba(255, 248, 235, 0.1);
  color: var(--ac-text-inverse);
}

.global-header__avatar {
  background: var(--ac-brand-gradient);
  font-weight: 600;
}

.global-header__username {
  font-size: 14px;
}

.global-header__caret {
  font-size: 13px;
  opacity: 0.75;
}

.global-header__auth-actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.global-header--home .global-header__auth-actions :deep(.ant-btn-text) {
  color: var(--ac-text-inverse-muted);
}

@media (max-width: 900px) {
  .global-header {
    min-height: 64px;
    padding: 0 16px;
  }

  .global-header__inner {
    flex-wrap: wrap;
    gap: 10px;
    min-height: 64px;
    padding: 8px 0;
  }

  .global-header__menu-wrap {
    order: 3;
    width: 100%;
  }

  .global-header__title {
    font-size: 26px;
  }

  .global-header__logo {
    width: 32px;
    height: 32px;
  }

  .global-header__user-trigger {
    margin-left: auto;
  }

  .global-header__auth-actions {
    margin-left: auto;
  }

  .global-header__username {
    max-width: 110px;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
}
</style>
