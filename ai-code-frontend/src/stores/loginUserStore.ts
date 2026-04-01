import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getLoginUser } from '@/api/usersController'

export const useLoginUserStore = defineStore('loginUser', () => {
  // 默认值，后续会通过接口获取并更新
  const loginUser = ref<API.LoginUserVO>({})

  // 获取登录用户信息
  async function fetchLoginUser() {
    const res = await getLoginUser()
    if (res.data.code === 0 && res.data.data) {
      loginUser.value = res.data.data
    }
  }

  // 更新登录用户信息
  function setLoginUser(newLoginUser: API.LoginUserVO) {
    loginUser.value = newLoginUser
  }

  return { loginUser, fetchLoginUser, setLoginUser }
})
