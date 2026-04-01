<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'

import logoUrl from '@/assets/logo.png'
import { userLogout } from '@/api/usersController'
import type { GlobalMenuItem } from '@/config/navigation'
import { useLoginUserStore } from '@/stores/loginUserStore'

const loginUserStore = useLoginUserStore()
const props = defineProps<{
  menuItems: GlobalMenuItem[]
}>()

const route = useRoute()
const router = useRouter()

const isLoggedIn = computed(() => Boolean(loginUserStore.loginUser?.id))
const isAdmin = computed(() => loginUserStore.loginUser?.userRole === 'admin')
const username = computed(() => loginUserStore.loginUser?.userName || 'User')
const userAvatar = computed(() => loginUserStore.loginUser?.userAvatar || '')

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
  if (key === 'userManagement') {
    await router.push('/admin/userManagement')
    return
  }

  if (key === 'logout') {
    try {
      const res = await userLogout()
      if (res.data.code === 0) {
        loginUserStore.setLoginUser({})
        message.success('已退出登录')
        await router.replace('/')
        return
      }
      message.error(res.data.message || '退出登录失败，请稍后重试')
    } catch {
      message.error('退出登录失败，请稍后重试')
    }
    return
  }

  message.info('该功能正在开发中')
}

const goToLoginPage = () => {
  void router.push('/user/login')
}

const goToRegisterPage = () => {
  void router.push('/user/register')
}
</script>

<template>
  <a-layout-header class="global-header">
    <div class="global-header__inner">
      <div class="global-header__brand">
        <img :src="logoUrl" alt="AI Code Generation Platform" class="global-header__logo" />
        <span class="global-header__title">AI Code Generation Platform</span>
      </div>

      <div class="global-header__menu-wrap">
        <a-menu
          mode="horizontal"
          theme="dark"
          :selected-keys="selectedKeys"
          :items="menuItems"
          @click="handleMenuClick"
        />
      </div>

      <a-dropdown v-if="isLoggedIn" :trigger="['click']">
        <button type="button" class="global-header__user-trigger">
          <a-avatar :size="40" :src="userAvatar" class="global-header__avatar">
            {{ username.slice(0, 1).toUpperCase() }}
          </a-avatar>
          <span class="global-header__username">{{ username }}</span>
          <span class="global-header__caret">▾</span>
        </button>
        <template #overlay>
          <a-menu @click="handleUserMenuClick">
            <a-menu-item v-if="isAdmin" key="userManagement">用户管理</a-menu-item>
            <a-menu-item key="profile">个人中心</a-menu-item>
            <a-menu-item key="settings">账号设置</a-menu-item>
            <a-menu-divider />
            <a-menu-item key="logout">退出登录</a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>

      <div v-else class="global-header__auth-actions">
        <a-button @click="goToRegisterPage">注册</a-button>
        <a-button type="primary" @click="goToLoginPage">登录</a-button>
      </div>
    </div>
  </a-layout-header>
</template>

<style scoped>
.global-header {
  height: auto;
  line-height: normal;
  padding: 0 28px;
  position: sticky;
  top: 0;
  z-index: 10;
}

.global-header__inner {
  display: flex;
  align-items: center;
  gap: 26px;
  min-height: 78px;
  width: 100%;
}

.global-header__brand {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: max-content;
}

.global-header__logo {
  width: 44px;
  height: 44px;
  object-fit: contain;
}

.global-header__title {
  color: #fff;
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 0.2px;
}

.global-header__menu-wrap {
  flex: 1;
  min-width: 0;
  overflow-x: auto;
}

.global-header__menu-wrap :deep(.ant-menu) {
  min-width: max-content;
  border-bottom: none;
  background: transparent;
}

.global-header__user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  border: 0;
  border-radius: 999px;
  padding: 6px 12px 6px 6px;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  cursor: pointer;
  min-width: max-content;
}

.global-header__user-trigger:hover {
  background: rgba(255, 255, 255, 0.2);
}

.global-header__avatar {
  background: linear-gradient(135deg, #4096ff, #73d13d);
  font-weight: 600;
}

.global-header__username {
  font-size: 15px;
}

.global-header__caret {
  font-size: 13px;
  opacity: 0.9;
}

.global-header__auth-actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

@media (max-width: 900px) {
  .global-header {
    padding: 0 16px;
  }

  .global-header__inner {
    flex-wrap: wrap;
    gap: 12px;
    min-height: 72px;
    padding: 8px 0;
  }

  .global-header__menu-wrap {
    order: 3;
    width: 100%;
  }

  .global-header__title {
    font-size: 16px;
  }

  .global-header__logo {
    width: 36px;
    height: 36px;
  }

  .global-header__user-trigger {
    margin-left: auto;
  }

  .global-header__auth-actions {
    margin-left: auto;
  }

  .global-header__username {
    max-width: 120px;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
}
</style>
