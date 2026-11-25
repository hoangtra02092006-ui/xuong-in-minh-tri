<script setup lang="ts">
// Button with explicit variants (DESIGN.md). Renders <RouterLink> with `to`, <a> with `href`.
import { computed } from 'vue'
import type { RouteLocationRaw } from 'vue-router'
import AppSpinner from './AppSpinner.vue'

const props = withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
    size?: 'sm' | 'md' | 'lg'
    type?: 'button' | 'submit'
    disabled?: boolean
    loading?: boolean
    to?: RouteLocationRaw
    href?: string
    block?: boolean
  }>(),
  {
    variant: 'secondary',
    size: 'md',
    type: 'button',
    disabled: false,
    loading: false,
    to: undefined,
    href: undefined,
    block: false,
  },
)

const classes = computed(() => [
  'btn',
  `btn--${props.variant}`,
  `btn--${props.size}`,
  { 'btn--block': props.block, 'is-loading': props.loading },
])
</script>

<template>
  <RouterLink v-if="to && !disabled" :to="to" :class="classes">
    <slot name="icon" />
    <span class="btn__label"><slot /></span>
    <slot name="trailing" />
  </RouterLink>
  <a v-else-if="href && !disabled" :href="href" :class="classes" target="_blank" rel="noopener">
    <slot name="icon" />
    <span class="btn__label"><slot /></span>
    <slot name="trailing" />
  </a>
  <button
    v-else
    :type="type"
    :class="classes"
    :disabled="disabled || loading"
    :aria-busy="loading || undefined"
  >
    <AppSpinner v-if="loading" :size="16" />
    <slot v-else name="icon" />
    <span class="btn__label"><slot /></span>
    <slot name="trailing" />
  </button>
</template>

<style scoped>
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  border: 1px solid transparent;
  border-radius: var(--radius);
  font-weight: 500;
  line-height: 1.2;
  text-decoration: none;
  cursor: pointer;
  white-space: nowrap;
  transition:
    background-color var(--dur) var(--ease),
    border-color var(--dur) var(--ease),
    color var(--dur) var(--ease);
}

.btn:active:not(:disabled) {
  transform: translateY(1px);
}

.btn--sm {
  height: 30px;
  padding: 0 var(--space-3);
  font-size: var(--text-sm);
  border-radius: var(--radius-sm);
}

.btn--md {
  height: 38px;
  padding: 0 var(--space-4);
  font-size: var(--text-base);
}

.btn--lg {
  height: 44px;
  padding: 0 var(--space-5);
  font-size: var(--text-md);
  font-weight: 600;
}

.btn--block {
  width: 100%;
}

.btn--primary {
  background: var(--color-primary);
  color: #fff;
}

.btn--primary:hover:not(:disabled) {
  background: var(--color-primary-hover);
}

.btn--secondary {
  background: var(--color-surface);
  border-color: var(--color-border-strong);
  color: var(--color-text);
}

.btn--secondary:hover:not(:disabled) {
  background: var(--color-surface-2);
  border-color: var(--color-subtle);
}

.btn--ghost {
  background: transparent;
  color: var(--color-muted);
}

.btn--ghost:hover:not(:disabled) {
  background: rgb(27 28 26 / 5%);
  color: var(--color-text);
}

.btn--danger {
  background: var(--color-surface);
  border-color: var(--color-border-strong);
  color: var(--color-danger);
}

.btn--danger:hover:not(:disabled) {
  background: var(--color-danger-soft);
  border-color: var(--color-danger);
}

.btn:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.btn.is-loading:disabled {
  opacity: 0.8;
  cursor: progress;
}

.btn__label:empty {
  display: none;
}
</style>
