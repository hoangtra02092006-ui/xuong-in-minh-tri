<script setup lang="ts">
import { dismissToast, runToastAction, useToasts } from '../toast'
import AppIcon from './AppIcon.vue'

const toasts = useToasts()
const ICON = { success: 'check', error: 'alert', info: 'info' } as const
</script>

<template>
  <div class="toasts" aria-live="polite">
    <TransitionGroup name="toast">
      <div
        v-for="t in toasts.items"
        :key="t.id"
        class="toast"
        :class="`toast--${t.kind}`"
        :role="t.kind === 'error' ? 'alert' : 'status'"
      >
        <AppIcon :name="ICON[t.kind]" :size="18" class="toast__icon" />
        <div class="toast__body">
          <p class="toast__text">{{ t.text }}</p>
          <button v-if="t.action" type="button" class="toast__action" @click="runToastAction(t.id)">
            <AppIcon name="download" :size="15" />
            {{ t.action.label }}
          </button>
        </div>
        <button type="button" class="toast__close" aria-label="Đóng thông báo" @click="dismissToast(t.id)">
          <AppIcon name="x" :size="16" />
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toasts {
  position: fixed;
  top: calc(var(--header-h) + var(--space-3));
  right: var(--space-4);
  z-index: 50;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  width: min(400px, calc(100vw - 2 * var(--space-4)));
  pointer-events: none;
}

.toast {
  pointer-events: auto;
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-3) var(--space-3) var(--space-4);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-primary);
  border-radius: var(--radius);
  box-shadow: var(--shadow-overlay);
}

.toast--success {
  border-left-color: var(--color-success);
}

.toast--error {
  border-left-color: var(--color-danger);
}

.toast__icon {
  margin-top: 2px;
  color: var(--color-primary);
}

.toast--success .toast__icon {
  color: var(--color-success);
}

.toast--error .toast__icon {
  color: var(--color-danger);
}

.toast__body {
  flex: 1;
  min-width: 0;
}

.toast__text {
  overflow-wrap: anywhere;
}

.toast__action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  margin-top: var(--space-2);
  padding: 0 var(--space-3);
  border: 1px solid #c3dfda;
  border-radius: var(--radius-sm);
  background: var(--color-primary-soft);
  color: var(--color-primary-ink);
  font: inherit;
  font-size: var(--text-sm);
  font-weight: 600;
  cursor: pointer;
  transition:
    background-color var(--dur) var(--ease),
    border-color var(--dur) var(--ease);
}

.toast__action:hover {
  background: #d8ebe7;
  border-color: var(--color-primary);
}

.toast__close {
  flex: none;
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 0;
  background: none;
  color: var(--color-subtle);
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.toast__close:hover {
  background: rgb(27 28 26 / 6%);
  color: var(--color-text);
}

.toast-enter-active,
.toast-leave-active {
  transition:
    opacity var(--dur) var(--ease),
    transform var(--dur) var(--ease);
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(12px);
}
</style>
