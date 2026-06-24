<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import enUS from 'ant-design-vue/es/locale/en_US'
import BasicLayout from '@/layouts/BasicLayout.vue'
import { useLoginUserStore } from './stores/loginUserStore'
// 初始化语言（同步 i18n 与 <html lang>）
import { useLocaleStore } from './stores/localeStore'

const loginUserStore = useLoginUserStore()
loginUserStore.fetchLoginUser()

useLocaleStore()

const { locale } = useI18n()
// 根据当前语言切换 ant-design-vue 内置组件文案（分页、空状态、日期选择器等）
const antLocale = computed(() => (locale.value === 'zh' ? zhCN : enUS))
</script>

<template>
  <a-config-provider :locale="antLocale">
    <BasicLayout />
  </a-config-provider>
</template>
