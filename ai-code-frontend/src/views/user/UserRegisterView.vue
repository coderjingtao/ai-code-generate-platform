<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'

import { register as userRegister } from '@/api/usersController'

const { t } = useI18n()
const router = useRouter()
const loading = ref(false)

const formState = reactive<API.UserRegisterRequest>({
  userEmail: '',
  userPassword: '',
  checkPassword: '',
})

const emailRules = computed(() => [
  { required: true, message: t('register.rules.emailRequired') },
  { type: 'email', message: t('register.rules.emailInvalid') },
])
const passwordRules = computed(() => [
  { required: true, message: t('register.rules.passwordRequired') },
])
const checkPasswordRules = computed(() => [
  { required: true, message: t('register.rules.checkPasswordRequired') },
  {
    validator: async (_rule: unknown, value: string | undefined) => {
      if (!value || value === formState.userPassword) {
        return Promise.resolve()
      }
      return Promise.reject(new Error(t('register.rules.passwordMismatch')))
    },
  },
])

const handleFinish = async () => {
  loading.value = true
  try {
    const res = await userRegister({
      userEmail: formState.userEmail,
      userPassword: formState.userPassword,
      checkPassword: formState.checkPassword,
    })
    if (res.data.code === 0) {
      message.success(t('register.messages.success'))
      await router.replace('/user/login')
      return
    }
    message.error(res.data.message || t('register.messages.failed'))
  } catch {
    message.error(t('register.messages.error'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="user-register-view">
    <a-card class="register-card" :bordered="false">
      <h1 class="register-title">{{ $t('register.title') }}</h1>
      <p class="register-subtitle">AI Code Generation Platform</p>
      <p class="register-slogan">
        Generate entire applications without writing a single line of code.
      </p>

      <a-form layout="vertical" :model="formState" autocomplete="off" @finish="handleFinish">
        <a-form-item :label="$t('register.form.email')" name="userEmail" :rules="emailRules">
          <a-input
            v-model:value="formState.userEmail"
            :placeholder="$t('register.placeholders.email')"
            size="large"
          />
        </a-form-item>

        <a-form-item :label="$t('register.form.password')" name="userPassword" :rules="passwordRules">
          <a-input-password
            v-model:value="formState.userPassword"
            :placeholder="$t('register.placeholders.password')"
            size="large"
          />
        </a-form-item>

        <a-form-item
          :label="$t('register.form.checkPassword')"
          name="checkPassword"
          :rules="checkPasswordRules"
        >
          <a-input-password
            v-model:value="formState.checkPassword"
            :placeholder="$t('register.placeholders.checkPassword')"
            size="large"
          />
        </a-form-item>

        <div class="login-tip">
          {{ $t('register.links.hasAccount') }}
          <RouterLink to="/user/login">{{ $t('register.links.goLogin') }}</RouterLink>
        </div>

        <a-form-item class="submit-wrap">
          <a-button type="primary" html-type="submit" size="large" block :loading="loading">
            {{ $t('common.header.register') }}
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
