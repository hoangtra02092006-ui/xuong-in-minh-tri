<script setup lang="ts">
defineProps<{ title: string; subtitle?: string }>()
</script>

<template>
  <header class="page-head">
    <div class="page-head__text">
      <h1 class="page-head__title">{{ title }}</h1>
      <p v-if="subtitle" class="page-head__subtitle">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.actions" class="page-head__actions"><slot name="actions" /></div>
  </header>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}

.page-head__text {
  min-width: 0;
}

.page-head__title {
  font-size: var(--text-xl);
  font-weight: 650;
  letter-spacing: -0.01em;
}

.page-head__subtitle {
  margin-top: var(--space-1);
  color: var(--color-muted);
  max-width: 70ch;
}

.page-head__actions {
  flex: none;
  display: flex;
  gap: var(--space-2);
}

@media (max-width: 640px) {
  .page-head {
    flex-direction: column;
    align-items: stretch;
    margin-bottom: var(--space-4);
  }

  .page-head__title {
    font-size: var(--text-lg);
  }

  .page-head__actions {
    order: -1;
  }
}
</style>
