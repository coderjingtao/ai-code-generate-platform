import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { setupAuthCheck } from './authCheck'

import Antd from 'ant-design-vue'

const app = createApp(App)
const pinia = createPinia()

app.use(Antd)
app.use(pinia)
setupAuthCheck(pinia)
app.use(router)

app.mount('#app')
