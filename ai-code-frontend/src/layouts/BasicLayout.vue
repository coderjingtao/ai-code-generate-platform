<script setup lang="ts">
import { RouterView } from 'vue-router'

import GlobalFooter from '@/components/GlobalFooter.vue'
import GlobalHeader from '@/components/GlobalHeader.vue'
import { globalMenuItems } from '@/config/navigation'
</script>

<template>
  <a-layout class="basic-layout">
    <GlobalHeader :menu-items="globalMenuItems" />

    <a-layout-content class="basic-layout__content">
      <main class="basic-layout__content-inner">
        <RouterView />
      </main>
    </a-layout-content>

    <GlobalFooter />
  </a-layout>
</template>

<style scoped>
:global(*) {
  box-sizing: border-box;
}

:global(html),
:global(body),
:global(#app) {
  width: 100%;
  height: 100%;
  margin: 0;
}

:global(body) {
  overflow: hidden;
  background: #f5f7fb;
}

.basic-layout {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.basic-layout__content {
  flex: 1;
  display: flex;
  width: 100%;
  overflow: auto;
}

.basic-layout__content-inner {
  width: 100%;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  padding: 24px;
}

@media (max-width: 900px) {
  .basic-layout__content-inner {
    padding: 16px;
  }
}
</style>
