<script setup lang="ts">
import BasicLayout from '@/layouts/BasicLayout.vue'
import { useLoginUserStore } from './stores/loginUserStore';

const loginUserStore = useLoginUserStore();
loginUserStore.fetchLoginUser();

</script>

<template>
  <BasicLayout />
</template>
