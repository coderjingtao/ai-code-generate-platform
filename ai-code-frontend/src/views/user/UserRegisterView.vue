<script setup lang="ts">
import { reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'

import { register as userRegister } from '@/api/usersController'

const router = useRouter()
const loading = ref(false)

const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

const accountRules = [{ required: true, message: '请输入账号' }]
const passwordRules = [{ required: true, message: '请输入密码' }]
const checkPasswordRules = [
  { required: true, message: '请再次输入密码' },
  {
    validator: async (_rule: unknown, value: string | undefined) => {
      if (!value || value === formState.userPassword) {
        return Promise.resolve()
      }
      return Promise.reject(new Error('两次输入的密码不一致'))
    },
  },
]

const handleFinish = async () => {
  loading.value = true
  try {
    const res = await userRegister({
      userAccount: formState.userAccount,
      userPassword: formState.userPassword,
      checkPassword: formState.checkPassword,
    })
    if (res.data.code === 0) {
      message.success('注册成功')
      await router.replace('/user/login')
      return
    }
    message.error(res.data.message || '注册失败，请稍后重试')
  } catch {
    message.error('注册失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="user-register-view">
    <a-card class="register-card" :bordered="false">
      <h1 class="register-title">欢迎注册</h1>
      <p class="register-subtitle">AI Code Generation Platform</p>
      <p class="register-slogan">
        Generate entire applications without writing a single line of code.
      </p>

      <a-form layout="vertical" :model="formState" autocomplete="off" @finish="handleFinish">
        <a-form-item label="账号" name="userAccount" :rules="accountRules">
          <a-input v-model:value="formState.userAccount" placeholder="请输入账号" size="large" />
        </a-form-item>

        <a-form-item label="密码" name="userPassword" :rules="passwordRules">
          <a-input-password
            v-model:value="formState.userPassword"
            placeholder="请输入密码"
            size="large"
          />
        </a-form-item>

        <a-form-item label="确认密码" name="checkPassword" :rules="checkPasswordRules">
          <a-input-password
            v-model:value="formState.checkPassword"
            placeholder="请再次输入密码"
            size="large"
          />
        </a-form-item>

        <div class="login-tip">
          已有账号，
          <RouterLink to="/user/login">去登录</RouterLink>
        </div>

        <a-form-item class="submit-wrap">
          <a-button type="primary" html-type="submit" size="large" block :loading="loading">
            注册
          </a-button>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<style scoped>
.user-register-view {
  flex: 1;
  width: 100%;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  box-sizing: border-box;
}

.register-card {
  width: 100%;
  max-width: 420px;
  border-radius: 14px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08);
}

.register-title {
  margin: 0;
  font-size: 30px;
  line-height: 1.2;
}

.register-subtitle {
  margin: 8px 0 6px;
  color: rgba(0, 0, 0, 0.55);
}

.register-slogan {
  margin: 0 0 24px;
  color: rgba(0, 0, 0, 0.68);
  font-size: 14px;
}

.login-tip {
  margin: -6px 0 16px;
  text-align: right;
  color: rgba(0, 0, 0, 0.65);
  font-size: 14px;
}

.login-tip a {
  color: #1677ff;
}

.submit-wrap {
  margin-bottom: 0;
  margin-top: 6px;
}
</style>
