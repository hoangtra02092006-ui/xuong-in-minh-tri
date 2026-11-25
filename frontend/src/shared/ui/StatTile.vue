<script setup lang="ts">
withDefaults(
  defineProps<{
    label: string
    value: string
    unit?: string
    tone?: 'default' | 'primary' | 'success'
    hint?: string
  }>(),
  { unit: '', tone: 'default', hint: '' },
)
</script>

<template>
  <div class="stat" :class="`stat--${tone}`">
    <p class="stat__label">{{ label }}</p>
    <p class="stat__value">
      <span class="stat__num">{{ value }}</span>
      <span v-if="unit" class="stat__unit">{{ unit }}</span>
    </p>
    <p v-if="hint" class="stat__hint">{{ hint }}</p>
  </div>
</template>

<style scoped>
.stat {
  padding: var(--space-4) var(--space-5);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  min-width: 0;
}

.stat--primary {
  border-color: var(--color-primary);
  box-shadow: inset 0 0 0 1px var(--color-primary);
}

.stat__label {
  font-size: var(--text-sm);
  color: var(--color-muted);
}

.stat__value {
  display: flex;
  align-items: baseline;
  gap: var(--space-1);
  margin-top: var(--space-1);
}

.stat__num {
  font-size: var(--text-2xl);
  font-weight: 600;
  line-height: 1.2;
  letter-spacing: -0.01em;
  font-variant-numeric: tabular-nums;
}

.stat--primary .stat__num {
  color: var(--color-primary);
}

.stat--success .stat__num {
  color: var(--color-success);
}

.stat__unit {
  font-size: var(--text-md);
  color: var(--color-muted);
}

.stat__hint {
  margin-top: 2px;
  font-size: var(--text-xs);
  color: var(--color-subtle);
}

@media (max-width: 640px) {
  .stat {
    padding: var(--space-3) var(--space-4);
  }

  .stat__num {
    font-size: var(--text-xl);
  }
}
</style>
