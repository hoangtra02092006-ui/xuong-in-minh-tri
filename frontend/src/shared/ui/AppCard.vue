<script setup lang="ts">
withDefaults(defineProps<{ title?: string; subtitle?: string; flush?: boolean }>(), {
  title: '',
  subtitle: '',
  flush: false,
})
</script>

<template>
  <section class="card">
    <header v-if="title || $slots.actions" class="card__head">
      <div class="card__titles">
        <h2 v-if="title" class="card__title">{{ title }}</h2>
        <p v-if="subtitle" class="card__subtitle">{{ subtitle }}</p>
      </div>
      <div v-if="$slots.actions" class="card__actions"><slot name="actions" /></div>
    </header>
    <div :class="['card__body', { 'card__body--flush': flush }]"><slot /></div>
    <footer v-if="$slots.footer" class="card__foot"><slot name="footer" /></footer>
  </section>
</template>

<style scoped>
.card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  min-width: 0;
}

.card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5) 0;
}

.card__titles {
  min-width: 0;
}

.card__title {
  font-size: var(--text-md);
}

.card__subtitle {
  margin-top: 2px;
  color: var(--color-muted);
  font-size: var(--text-sm);
}

.card__actions {
  display: flex;
  gap: var(--space-2);
  flex: none;
}

.card__body {
  padding: var(--space-4) var(--space-5) var(--space-5);
}

.card__body--flush {
  padding: var(--space-3) 0 0;
}

.card__foot {
  padding: var(--space-3) var(--space-5);
  border-top: 1px solid var(--color-border);
  background: var(--color-surface-2);
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
}

@media (max-width: 640px) {
  .card__head {
    padding: var(--space-4) var(--space-4) 0;
  }

  .card__body {
    padding: var(--space-3) var(--space-4) var(--space-4);
  }

  .card__body--flush {
    padding: var(--space-3) 0 0;
  }

  .card__foot {
    padding: var(--space-3) var(--space-4);
  }
}
</style>
