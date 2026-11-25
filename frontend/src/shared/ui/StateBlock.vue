<script setup lang="ts">
// Whole-area state (invariant 12): empty, loading or error, always with a next action slot.
import AppIcon, { type IconName } from './AppIcon.vue'
import AppSpinner from './AppSpinner.vue'

withDefaults(
  defineProps<{ variant?: 'empty' | 'loading' | 'error'; title: string; text?: string; icon?: IconName }>(),
  { variant: 'empty', text: '', icon: 'file' },
)
</script>

<template>
  <div class="state" :class="`state--${variant}`" :role="variant === 'error' ? 'alert' : 'status'">
    <div class="state__icon">
      <AppSpinner v-if="variant === 'loading'" :size="26" />
      <AppIcon v-else :name="variant === 'error' ? 'alert' : icon" :size="24" />
    </div>
    <h2 class="state__title">{{ title }}</h2>
    <p v-if="text" class="state__text">{{ text }}</p>
    <div v-if="$slots.default" class="state__actions"><slot /></div>
  </div>
</template>

<style scoped>
.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: var(--space-7) var(--space-5);
  gap: var(--space-2);
}

.state__icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: #efeee9;
  color: var(--color-muted);
  margin-bottom: var(--space-2);
}

.state--loading .state__icon {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.state--error .state__icon {
  background: var(--color-danger-soft);
  color: var(--color-danger);
}

.state__title {
  font-size: var(--text-lg);
}

.state__text {
  max-width: 54ch;
  color: var(--color-muted);
}

.state__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--space-2);
  margin-top: var(--space-4);
}
</style>
