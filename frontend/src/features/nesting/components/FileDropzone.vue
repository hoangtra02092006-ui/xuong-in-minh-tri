<script setup lang="ts">
import { ref } from 'vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import ProgressBar from '@/shared/ui/ProgressBar.vue'
import type { UploadRow } from '../useUploader'

defineProps<{ rows: UploadRow[]; compact?: boolean }>()
const emit = defineEmits<{ files: [files: File[]] }>()

const input = ref<HTMLInputElement | null>(null)
const dragging = ref(false)
let depth = 0

function pick(e: Event) {
  const el = e.target as HTMLInputElement
  const files = Array.from(el.files ?? [])
  // Reset so choosing the same file again still fires change (spec 5.4).
  el.value = ''
  if (files.length) emit('files', files)
}

function onDragEnter() {
  depth += 1
  dragging.value = true
}

function onDragLeave() {
  depth = Math.max(0, depth - 1)
  if (depth === 0) dragging.value = false
}

function onDrop(e: DragEvent) {
  depth = 0
  dragging.value = false
  const files = Array.from(e.dataTransfer?.files ?? [])
  if (files.length) emit('files', files)
}
</script>

<template>
  <section
    class="drop"
    :class="{ 'is-dragging': dragging, 'is-compact': compact }"
    @dragenter.prevent="onDragEnter"
    @dragover.prevent
    @dragleave.prevent="onDragLeave"
    @drop.prevent="onDrop"
  >
    <div class="drop__main">
      <span class="drop__icon"><AppIcon name="upload" :size="22" /></span>
      <div class="drop__text">
        <h2 class="drop__title">Kéo thả file in vào đây</h2>
        <p class="drop__hint">Nhận file PDF, PNG, JPG — chọn được nhiều file cùng lúc.</p>
      </div>
      <AppButton variant="secondary" @click="input?.click()">Chọn file</AppButton>
      <input
        ref="input"
        class="sr-only"
        type="file"
        multiple
        accept=".pdf,.png,.jpg,.jpeg"
        tabindex="-1"
        aria-hidden="true"
        @change="pick"
      />
    </div>
    <ul v-if="rows.length" class="queue" aria-label="Tiến độ tải file">
      <li v-for="r in rows" :key="r.key" class="queue__row" :class="`is-${r.status}`">
        <span class="queue__name" :title="r.name">{{ r.name }}</span>
        <ProgressBar
          class="queue__bar"
          :value="r.status === 'error' ? 100 : r.percent"
          :tone="r.status === 'error' ? 'danger' : r.status === 'done' ? 'success' : 'primary'"
          :label="`Tải ${r.name}`"
        />
        <span class="queue__pct num">
          <template v-if="r.status === 'error'">Lỗi</template>
          <template v-else-if="r.status === 'waiting'">Chờ</template>
          <template v-else>{{ r.percent }}%</template>
        </span>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.drop {
  border: 1.5px dashed var(--color-border-strong);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  transition:
    border-color var(--dur) var(--ease),
    background-color var(--dur) var(--ease);
}

.drop.is-dragging {
  border-color: var(--color-primary);
  border-style: solid;
  background: var(--color-primary-soft);
}

.drop__main {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-6) var(--space-5);
}

.is-compact .drop__main {
  padding: var(--space-4) var(--space-5);
}

.drop__icon {
  display: grid;
  place-items: center;
  flex: none;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.drop__text {
  flex: 1;
  min-width: 0;
}

.drop__title {
  font-size: var(--text-md);
}

.drop__hint {
  color: var(--color-muted);
  font-size: var(--text-sm);
}

.queue {
  list-style: none;
  margin: 0;
  padding: var(--space-3) var(--space-5) var(--space-4);
  border-top: 1px solid var(--color-border);
  display: grid;
  gap: var(--space-2);
}

.queue__row {
  display: grid;
  grid-template-columns: minmax(0, 220px) 1fr 44px;
  align-items: center;
  gap: var(--space-3);
  font-size: var(--text-sm);
}

.queue__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.is-error .queue__name,
.is-error .queue__pct {
  color: var(--color-danger);
  font-weight: 500;
}

.queue__pct {
  color: var(--color-muted);
}

@media (max-width: 640px) {
  .drop__main {
    flex-wrap: wrap;
    padding: var(--space-5) var(--space-4);
  }

  .drop__main > :deep(.btn) {
    width: 100%;
  }

  .queue {
    padding: var(--space-3) var(--space-4);
  }

  .queue__row {
    grid-template-columns: minmax(0, 1fr) 80px 40px;
  }
}
</style>
