<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'

import GlobalFooter from '@/components/GlobalFooter.vue'
import GlobalHeader from '@/components/GlobalHeader.vue'
import { globalMenuItems } from '@/config/navigation'

const route = useRoute()

const isChatPage = computed(() => route.path.startsWith('/app/chat'))
const flushLayout = computed(() => route.path === '/' || route.path.startsWith('/app/chat'))
const showFooter = computed(() => route.path !== '/' && !route.path.startsWith('/app/chat'))
</script>

<template>
  <a-layout class="basic-layout">
    <GlobalHeader :menu-items="globalMenuItems" />

    <a-layout-content
      :class="['basic-layout__content', { 'basic-layout__content--chat': isChatPage }]"
    >
      <main
        :class="[
          'basic-layout__content-inner',
          { 'basic-layout__content-inner--flush': flushLayout },
          { 'basic-layout__content-inner--chat': isChatPage },
        ]"
      >
        <RouterView />
      </main>
    </a-layout-content>

    <GlobalFooter v-if="showFooter" />
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
  background: var(--ac-bg-page);
  font-family: var(--ac-font-sans);
}

.basic-layout {
  --global-header-height: var(--ac-header-height);
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.basic-layout__content {
  flex: 1;
  display: flex;
  width: 100%;
  min-height: 0;
  overflow: auto;
}

.basic-layout__content--chat {
  height: calc(100vh - var(--global-header-height));
  overflow: hidden;
}

.basic-layout__content-inner {
  width: 100%;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  padding: 24px;
}

.basic-layout__content-inner--flush {
  padding: 0;
}

.basic-layout__content-inner--chat {
  flex: 1;
  min-height: 0;
  height: 100%;
  overflow: hidden;
}

@media (max-width: 900px) {
  .basic-layout {
    --global-header-height: 64px;
  }

  .basic-layout__content-inner {
    padding: 16px;
  }

  .basic-layout__content-inner--flush {
    padding: 0;
  }
}
</style>
