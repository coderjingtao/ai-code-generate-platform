import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { setupAuthCheck } from './authCheck'
import i18n from './locales'

import Antd from 'ant-design-vue'

import '@/assets/styles/theme.css'

const app = createApp(App)
const pinia = createPinia()

app.use(Antd)
app.use(pinia)
app.use(i18n)
setupAuthCheck(pinia)
app.use(router)

app.mount('#app')
