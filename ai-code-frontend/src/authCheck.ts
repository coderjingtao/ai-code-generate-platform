import type { Pinia } from 'pinia'
import { message } from 'ant-design-vue'
import router from './router'
import { useLoginUserStore } from './stores/loginUserStore'

export function setupAuthCheck(pinia: Pinia) {
  router.beforeEach(async (to) => {
    if (!to.path.startsWith('/admin')) {
      return true
    }

    const loginUserStore = useLoginUserStore(pinia)

    if (!loginUserStore.loginUser.userRole) {
      await loginUserStore.fetchLoginUser()
    }

    if (loginUserStore.loginUser.userRole !== 'admin') {
      message.error('没有管理员权限')
      return false
    }

    return true
  })
}
