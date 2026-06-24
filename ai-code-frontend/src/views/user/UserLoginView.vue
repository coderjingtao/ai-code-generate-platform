<script setup lang="ts">
import { reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'

import { userLogin } from '@/api/usersController'
import { useLoginUserStore } from '@/stores/loginUserStore'

const { t } = useI18n()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const loading = ref(false)

const formState = reactive<API.UserLoginRequest>({
  userEmail: '',
  userPassword: '',
})

const handleFinish = async () => {
  loading.value = true
  try {
    const res = await userLogin({
      userEmail: formState.userEmail,
      userPassword: formState.userPassword,
    })
    if (res.data.code === 0 && res.data.data) {
      loginUserStore.setLoginUser(res.data.data)
      message.success(t('login.messages.success'))
      await router.replace('/')
      return
    }
    message.error(res.data.message || t('login.messages.failed'))
  } catch {
    message.error(t('login.messages.error'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="user-login-view">
    <a-card class="login-card" :bordered="false">
      <h1 class="login-title">{{ $t('login.title') }}</h1>
      <p class="login-subtitle">AI Code Generation Platform</p>
      <p class="login-slogan">
        Generate entire applications without writing a single line of code.
      </p>

      <a-form layout="vertical" :model="formState" autocomplete="off" @finish="handleFinish">
        <a-form-item
          :label="$t('login.form.email')"
          name="userEmail"
          :rules="[
            { required: true, message: $t('login.rules.emailRequired') },
            { type: 'email', message: $t('login.rules.emailInvalid') }
          ]"
        >
          <a-input
            v-model:value="formState.userEmail"
            :placeholder="$t('login.placeholders.email')"
            size="large"
          />
        </a-form-item>

        <a-form-item
          :label="$t('login.form.password')"
          name="userPassword"
          :rules="[{ required: true, message: $t('login.rules.passwordRequired') }]"
        >
          <a-input-password
            v-model:value="formState.userPassword"
            :placeholder="$t('login.placeholders.password')"
            size="large"
          />
        </a-form-item>

        <div class="register-tip">
          {{ $t('login.links.noAccount') }}
          <RouterLink to="/user/register">{{ $t('login.links.goRegister') }}</RouterLink>
        </div>

        <a-form-item class="submit-wrap">
          <a-button type="primary" html-type="submit" size="large" block :loading="loading">
            {{ $t('common.header.login') }}
          </a-button>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<style scoped>
.user-login-view {
  flex: 1;
  width: 100%;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  box-sizing: border-box;
}

.login-card {
  width: 100%;
  max-width: 420px;
  border-radius: 14px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08);
}

.login-title {
  margin: 0;
  font-size: 30px;
  line-height: 1.2;
}

.login-subtitle {
  margin: 8px 0 6px;
  color: rgba(0, 0, 0, 0.55);
}

.login-slogan {
  margin: 0 0 24px;
  color: rgba(0, 0, 0, 0.68);
  font-size: 14px;
}

.register-tip {
  margin: -6px 0 16px;
  text-align: right;
  color: rgba(0, 0, 0, 0.65);
  font-size: 14px;
}

.register-tip a {
  color: #1677ff;
}

.submit-wrap {
  margin-bottom: 0;
  margin-top: 6px;
}
</style>
