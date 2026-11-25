<script setup lang="ts">
// Wizard stepper (spec 5.1): passed steps are links back, future steps are truly disabled.
import type { RouteLocationRaw } from 'vue-router'
import AppIcon from './AppIcon.vue'

export interface StepItem {
  label: string
  state: 'done' | 'current' | 'locked'
  to?: RouteLocationRaw
}

defineProps<{ steps: StepItem[] }>()
</script>

<template>
  <nav class="stepper" aria-label="Các bước ghép file">
    <ol>
      <li v-for="(s, i) in steps" :key="s.label" :class="`is-${s.state}`">
        <RouterLink v-if="s.state === 'done' && s.to" :to="s.to" class="step">
          <span class="step__dot"><AppIcon name="check" :size="14" /></span>
          <span class="step__label">{{ s.label }}</span>
        </RouterLink>
        <button
          v-else
          type="button"
          class="step"
          :disabled="s.state === 'locked'"
          :aria-current="s.state === 'current' ? 'step' : undefined"
        >
          <span class="step__dot">{{ i + 1 }}</span>
          <span class="step__label">{{ s.label }}</span>
        </button>
        <span v-if="i < steps.length - 1" class="step__line" aria-hidden="true" />
      </li>
    </ol>
  </nav>
</template>

<style scoped>
.stepper ol {
  display: flex;
  align-items: center;
  list-style: none;
  margin: 0;
  padding: 0;
  gap: var(--space-2);
}

.stepper li {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

.step {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-1) var(--space-2) var(--space-1) var(--space-1);
  border: 0;
  border-radius: 999px;
  background: none;
  color: var(--color-muted);
  font-size: var(--text-sm);
  font-weight: 500;
  text-decoration: none;
  white-space: nowrap;
}

a.step:hover {
  background: rgb(27 28 26 / 5%);
  color: var(--color-text);
}

button.step {
  cursor: default;
}

button.step:disabled {
  color: var(--color-subtle);
}

.step__dot {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 1px solid var(--color-border-strong);
  background: var(--color-surface);
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
}

.is-done .step__dot {
  background: var(--color-primary-soft);
  border-color: transparent;
  color: var(--color-primary);
}

.is-current .step {
  color: var(--color-text);
}

.is-current .step__dot {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
}

.step__line {
  width: 32px;
  height: 1px;
  background: var(--color-border-strong);
}

.is-done .step__line {
  background: var(--color-primary);
}

@media (max-width: 640px) {
  .step__line {
    width: 12px;
  }

  .stepper li:not(.is-current) .step__label {
    display: none;
  }
}
</style>
