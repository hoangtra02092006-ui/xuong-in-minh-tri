<script setup lang="ts">
withDefaults(defineProps<{ tone?: 'neutral' | 'primary' | 'success' | 'warning' | 'danger' }>(), {
  tone: 'neutral',
})
</script>

<template>
  <span class="badge" :class="`badge--${tone}`"><slot /></span>
</template>

<style scoped>
.badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 20px;
  padding: 0 7px;
  border-radius: 4px;
  font-size: var(--text-xs);
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
}

.badge--neutral {
  background: #efeee9;
  color: var(--color-muted);
}

.badge--primary {
  background: var(--color-primary-soft);
  color: var(--color-primary-ink);
}

.badge--success {
  background: var(--color-success-soft);
  color: var(--color-success);
}

.badge--warning {
  background: var(--color-warning-soft);
  color: var(--color-warning);
}

.badge--danger {
  background: var(--color-danger-soft);
  color: var(--color-danger);
}
</style>
