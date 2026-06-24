<template>
  <section class="page">
    <h1 class="page__title">{{ $t('workspace.title') }}</h1>
    <p class="page__description">{{ $t('workspace.description') }}</p>
  </section>
</template>

<style scoped>
.page__title {
  margin: 0;
  font-size: 30px;
  line-height: 1.3;
}

.page__description {
  margin: 14px 0 0;
  color: rgba(0, 0, 0, 0.65);
}
</style>
