import axios from 'axios'
import { message } from 'ant-design-vue'
import { API_BASE_URL } from '@/config/env'

const loginRequiredPathPrefixes = ['/app/chat', '/app/edit', '/admin', '/workspace', '/history']

const shouldRedirectToLogin = (pathname: string) => {
  return loginRequiredPathPrefixes.some((prefix) => pathname.startsWith(prefix))
}

// 创建 Axios 实例
const myAxios = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000,
  withCredentials: true, //false: 不携带cookie，true: 携带cookie
})

// 全局请求拦截器
myAxios.interceptors.request.use(
  function (config) {
    // Do something before request is sent
    return config
  },
  function (error) {
    // Do something with request error
    return Promise.reject(error)
  },
)

// 全局响应拦截器
myAxios.interceptors.response.use(
  function (response) {
    const { data } = response
    // 未登录
    if (data.code === 40100) {
      const currentPath = window.location.pathname
      const isLoginUserApi = response.request.responseURL.includes('user/get/login')
      const isAuthPage =
        currentPath.startsWith('/user/login') || currentPath.startsWith('/user/register')

      if (!isLoginUserApi && !isAuthPage && shouldRedirectToLogin(currentPath)) {
        message.warning('Please log in first')
        window.location.href = `/user/login?redirect=${window.location.href}`
      }
    }
    return response
  },
  function (error) {
    // Any status codes that falls outside the range of 2xx cause this function to trigger
    // Do something with response error
    return Promise.reject(error)
  },
)

export default myAxios
