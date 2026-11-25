<script setup lang="ts">
import AppIcon from './AppIcon.vue'

withDefaults(defineProps<{ tone?: 'info' | 'warning' | 'danger'; title: string }>(), { tone: 'info' })
</script>

<template>
  <div class="alert" :class="`alert--${tone}`" :role="tone === 'danger' ? 'alert' : 'note'">
    <AppIcon :name="tone === 'info' ? 'info' : 'alert'" :size="18" class="alert__icon" />
    <div class="alert__body">
      <p class="alert__title">{{ title }}</p>
      <div v-if="$slots.default" class="alert__text"><slot /></div>
    </div>
  </div>
</template>

<style scoped>
.alert {
  display: flex;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius);
  border: 1px solid transparent;
}

.alert--info {
  background: var(--color-primary-soft);
  border-color: #cfe4e0;
}

.alert--warning {
  background: var(--color-warning-soft);
  border-color: #f0dfb8;
}

.alert--danger {
  background: var(--color-danger-soft);
  border-color: #f2c9c5;
}

.alert__icon {
  margin-top: 2px;
}

.alert--info .alert__icon {
  color: var(--color-primary);
}

.alert--warning .alert__icon {
  color: var(--color-warning);
}

.alert--danger .alert__icon {
  color: var(--color-danger);
}

.alert__body {
  min-width: 0;
}

.alert__title {
  font-weight: 600;
}

.alert__text {
  margin-top: 2px;
  font-size: var(--text-sm);
  color: var(--color-muted);
}
</style>
