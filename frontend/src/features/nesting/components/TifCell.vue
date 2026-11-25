<script setup lang="ts">
// TIF column (design 4a): Sẵn sàng / Đang dựng… / Đang chờ / Lỗi · Thử lại. Never locked.
import type { SheetTif } from '@/api/types'
import { formatBytes } from '@/shared/units'
import AppIcon from '@/shared/ui/AppIcon.vue'
import AppSpinner from '@/shared/ui/AppSpinner.vue'

defineProps<{ tif: SheetTif; href: string; sheetNo: number; auto: boolean; working: boolean }>()
const emit = defineEmits<{ request: []; ready: [e: MouseEvent] }>()
</script>

<template>
  <div class="tif">
    <a
      v-if="tif.status === 'READY'"
      class="tif__btn tif__btn--ready"
      :href="href"
      target="_blank"
      rel="noopener"
      :aria-label="`Tải TIF tấm ${sheetNo}`"
      @click="emit('ready', $event)"
    >
      <AppIcon name="download" :size="15" />
      TIF <span class="tif__size">{{ tif.sizeBytes ? formatBytes(tif.sizeBytes) : '' }}</span>
    </a>
    <button
      v-else-if="tif.status === 'FAILED'"
      type="button"
      class="tif__btn tif__btn--failed"
      :title="tif.error?.message ?? 'Dựng TIF bị lỗi'"
      :aria-label="`TIF tấm ${sheetNo} lỗi: ${tif.error?.message ?? ''}. Thử lại`"
      @click="emit('request')"
    >
      <AppSpinner v-if="working" :size="13" />
      <AppIcon v-else name="refresh" :size="15" />
      Lỗi · Thử lại
    </button>
    <button
      v-else
      type="button"
      class="tif__btn tif__btn--pending"
      :title="
        tif.status === 'QUEUED'
          ? 'Bấm để dựng tấm này trước và tự tải khi xong'
          : 'Đang dựng, bấm để tự tải khi xong'
      "
      :aria-label="`TIF tấm ${sheetNo}: ${tif.status === 'BUILDING' ? 'đang dựng' : 'đang chờ'}`"
      @click="emit('request')"
    >
      <AppSpinner v-if="tif.status === 'BUILDING' || working" :size="13" />
      <span v-else class="tif__wait" aria-hidden="true" />
      {{ tif.status === 'BUILDING' ? 'Đang dựng…' : 'Đang chờ' }}
    </button>
    <span v-if="auto && tif.status !== 'READY' && tif.status !== 'FAILED'" class="tif__auto"
      >tự tải khi xong</span
    >
  </div>
</template>

<style scoped>
.tif {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.tif__btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  min-width: 112px;
  padding: 0 var(--space-3);
  border-radius: var(--radius-sm);
  border: 1px solid transparent;
  font-size: var(--text-sm);
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
  cursor: pointer;
  transition:
    background-color var(--dur) var(--ease),
    border-color var(--dur) var(--ease);
}

.tif__btn--ready {
  background: var(--color-primary-soft);
  border-color: #c3dfda;
  color: var(--color-primary-ink);
}

.tif__btn--ready:hover {
  background: #d8ebe7;
  border-color: var(--color-primary);
}

.tif__size {
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.tif__btn--pending {
  background: var(--color-surface);
  border-color: var(--color-border);
  color: var(--color-muted);
  font-weight: 500;
}

.tif__btn--pending:hover {
  border-color: var(--color-border-strong);
  color: var(--color-text);
}

.tif__btn--failed {
  background: var(--color-danger-soft);
  border-color: #f2c9c5;
  color: var(--color-danger);
}

.tif__btn--failed:hover {
  border-color: var(--color-danger);
}

.tif__wait {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 1.5px solid var(--color-subtle);
}

.tif__auto {
  font-size: var(--text-xs);
  color: var(--color-primary-ink);
}
</style>
