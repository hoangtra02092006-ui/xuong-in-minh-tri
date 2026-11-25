<script setup lang="ts">
// Overall TIF pre-build line (design 4a): "Đang chuẩn bị file TIF: 2/5 tấm sẵn sàng", then
// "Tất cả file TIF đã sẵn sàng" for a moment, then hidden.
import { ref, watch } from 'vue'
import type { TifProgress } from '@/stores/job'
import AppIcon from '@/shared/ui/AppIcon.vue'
import ProgressBar from '@/shared/ui/ProgressBar.vue'

/** How long the "all ready" line stays before hiding. */
const ALL_READY_VISIBLE_MS = 3000

const props = defineProps<{ progress: TifProgress }>()
const hidden = ref(props.progress.settled && props.progress.failed === 0)
let timer: ReturnType<typeof setTimeout> | null = null

watch(
  () => props.progress,
  (p, old) => {
    if (timer) clearTimeout(timer)
    if (!p.settled || p.failed > 0) {
      hidden.value = false
      return
    }
    if (old && !old.settled) timer = setTimeout(() => (hidden.value = true), ALL_READY_VISIBLE_MS)
  },
)
</script>

<template>
  <div
    v-if="!hidden"
    class="tifline"
    :class="{ 'is-done': progress.settled && !progress.failed }"
    role="status"
  >
    <div class="tifline__text">
      <template v-if="!progress.settled">
        <span>
          Đang chuẩn bị file TIF: <strong class="num">{{ progress.ready }}/{{ progress.total }}</strong> tấm
          sẵn sàng
        </span>
        <span class="tifline__note"
          >Có thể tải PDF, file cắt ngay; bấm TIF của tấm đang chờ để dựng tấm đó trước.</span
        >
      </template>
      <template v-else-if="progress.failed">
        <span>
          <strong class="num">{{ progress.ready }}/{{ progress.total }}</strong> tấm TIF sẵn sàng,
          <strong class="num danger">{{ progress.failed }}</strong> tấm lỗi
        </span>
        <span class="tifline__note">Rê chuột vào nút "Lỗi · Thử lại" để xem lý do, bấm để dựng lại.</span>
      </template>
      <span v-else class="tifline__ok"><AppIcon name="check" :size="16" />Tất cả file TIF đã sẵn sàng</span>
    </div>
    <ProgressBar
      v-if="!progress.settled"
      class="tifline__bar"
      :value="(progress.ready / progress.total) * 100"
      label="Tiến độ chuẩn bị file TIF"
    />
  </div>
</template>

<style scoped>
.tifline {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-5);
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface-2);
}

.tifline.is-done {
  background: var(--color-success-soft);
}

.tifline__text {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-1) var(--space-4);
}

.tifline__note {
  font-size: var(--text-xs);
  color: var(--color-muted);
}

.tifline__ok {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-success);
  font-weight: 600;
}

.danger {
  color: var(--color-danger);
}

@media (max-width: 640px) {
  .tifline {
    padding: var(--space-3) var(--space-4);
  }
}
</style>
