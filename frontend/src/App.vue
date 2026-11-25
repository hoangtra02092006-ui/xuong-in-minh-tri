<script setup lang="ts">
import AppHeader from '@/features/shell/AppHeader.vue'
import ToastHost from '@/shared/ui/ToastHost.vue'
import { getApi } from '@/stores/api-provider'

const demo = getApi().isDemo
</script>

<template>
  <a class="skip" href="#main">Bỏ qua, tới nội dung chính</a>
  <AppHeader :demo="demo" />
  <main id="main" tabindex="-1">
    <RouterView />
  </main>
  <ToastHost />
</template>

<style scoped>
.skip {
  position: absolute;
  left: var(--space-3);
  top: -60px;
  z-index: 100;
  padding: var(--space-2) var(--space-3);
  background: var(--color-surface);
  border-radius: var(--radius);
  box-shadow: var(--shadow-overlay);
}

.skip:focus {
  top: var(--space-2);
}

main {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - var(--header-h));
}

main > :deep(.page) {
  flex: 1;
  width: 100%;
}

main:focus {
  outline: none;
}
</style>
