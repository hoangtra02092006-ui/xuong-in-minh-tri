<script setup lang="ts">
// Centered modal on a scrim; Esc or a click on the scrim closes it.
import { onBeforeUnmount, onMounted, ref, nextTick } from 'vue'
import AppIcon from './AppIcon.vue'

defineProps<{ label: string }>()
const emit = defineEmits<{ close: [] }>()
const closeBtn = ref<HTMLButtonElement | null>(null)

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}

onMounted(() => {
  document.addEventListener('keydown', onKey)
  void nextTick(() => closeBtn.value?.focus())
})
onBeforeUnmount(() => document.removeEventListener('keydown', onKey))
</script>

<template>
  <Teleport to="body">
    <div class="scrim" @click.self="emit('close')">
      <div class="dialog" role="dialog" aria-modal="true" :aria-label="label">
        <button ref="closeBtn" type="button" class="dialog__close" aria-label="Đóng" @click="emit('close')">
          <AppIcon name="x" :size="18" />
        </button>
        <slot />
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.scrim {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: grid;
  place-items: center;
  padding: var(--space-4);
  background: rgb(27 28 26 / 45%);
  overscroll-behavior: contain;
}

.dialog {
  position: relative;
  max-width: min(720px, 100%);
  max-height: calc(100vh - 2 * var(--space-4));
  overflow: auto;
  padding: var(--space-5);
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-overlay);
}

.dialog__close {
  position: absolute;
  top: var(--space-2);
  right: var(--space-2);
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-muted);
  cursor: pointer;
}

.dialog__close:hover {
  background: rgb(27 28 26 / 6%);
  color: var(--color-text);
}
</style>
