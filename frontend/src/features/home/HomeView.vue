<script setup lang="ts">
import { useRouter } from 'vue-router'
import AppIcon, { type IconName } from '@/shared/ui/AppIcon.vue'
import PageHeader from '@/shared/ui/PageHeader.vue'
import { useJobStore } from '@/stores/job'
import { useOrderStore } from '@/stores/order'

const router = useRouter()
const order = useOrderStore()
const jobStore = useJobStore()

const upcoming: { title: string; text: string; icon: IconName }[] = [
  { title: 'Thư viện file', text: 'Lưu các mẫu in hay dùng để không phải tải lại mỗi lần.', icon: 'folder' },
  { title: 'Lịch sử ghép', text: 'Xem lại các lần ghép trước và tải lại file đã xuất.', icon: 'history' },
  { title: 'Preset khổ in', text: 'Lưu sẵn bộ tham số cho từng loại cuộn và từng máy in.', icon: 'ruler' },
  { title: 'Báo cáo tiết kiệm', text: 'Thống kê số mét và số tiền đã tiết kiệm theo tháng.', icon: 'chart' },
]

/** Spec 5.3: start a clean order (list, params, job, polling) then go to step 1. */
function startNesting() {
  jobStore.reset()
  order.reset()
  void router.push({ name: 'upload' })
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="Công cụ sản xuất"
      subtitle="Chọn một chức năng để bắt đầu. Hiện tại chức năng ghép file đã sẵn sàng dùng."
    />

    <div class="tools">
      <button type="button" class="tool tool--active" @click="startNesting">
        <span class="tool__icon"><AppIcon name="layers" :size="26" /></span>
        <span class="tool__body">
          <span class="tool__title">Ghép file in</span>
          <span class="tool__text">Tự dàn khuôn nhiều file lên khổ cuộn sao cho tốn ít mét nhất.</span>
          <span class="tool__flow">
            <span>Nạp file</span>
            <AppIcon name="arrow-right" :size="14" />
            <span>Tham số in</span>
            <AppIcon name="arrow-right" :size="14" />
            <span>Tải TIF, PDF, file cắt</span>
          </span>
        </span>
        <span class="tool__go">
          Bắt đầu
          <AppIcon name="arrow-right" :size="18" />
        </span>
      </button>

      <div v-for="t in upcoming" :key="t.title" class="tool tool--locked" aria-disabled="true">
        <div class="tool__top">
          <span class="tool__icon tool__icon--muted"><AppIcon :name="t.icon" :size="20" /></span>
          <span class="soon"><AppIcon name="lock" :size="12" />Sắp có</span>
        </div>
        <h2 class="tool__title tool__title--sm">{{ t.title }}</h2>
        <p class="tool__text">{{ t.text }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tools {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-4);
}

.tool {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  text-align: left;
  min-width: 0;
}

.tool--active {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  gap: var(--space-5);
  padding: var(--space-6);
  cursor: pointer;
  border-color: var(--color-primary);
  transition:
    box-shadow var(--dur) var(--ease),
    background-color var(--dur) var(--ease);
}

.tool--active:hover {
  box-shadow: 0 0 0 4px var(--color-primary-soft);
}

.tool__icon {
  display: grid;
  place-items: center;
  flex: none;
  width: 56px;
  height: 56px;
  border-radius: var(--radius-lg);
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.tool__icon--muted {
  width: 40px;
  height: 40px;
  border-radius: var(--radius);
  background: #efeee9;
  color: var(--color-subtle);
}

.tool__body {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  flex: 1;
  min-width: 0;
}

.tool__title {
  font-size: var(--text-lg);
  font-weight: 650;
  line-height: var(--leading-tight);
}

.tool__title--sm {
  font-size: var(--text-md);
  font-weight: 600;
  margin-top: var(--space-3);
  color: var(--color-muted);
}

.tool__text {
  color: var(--color-muted);
}

.tool__flow {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: var(--space-2);
  font-size: var(--text-sm);
  color: var(--color-primary-ink);
}

.tool__flow .icon {
  color: var(--color-subtle);
}

.tool__go {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  flex: none;
  height: 44px;
  padding: 0 var(--space-5);
  border-radius: var(--radius);
  background: var(--color-primary);
  color: #fff;
  font-weight: 600;
  font-size: var(--text-md);
}

.tool--active:hover .tool__go {
  background: var(--color-primary-hover);
}

.tool--locked {
  padding: var(--space-4) var(--space-5) var(--space-5);
  background: var(--color-surface-2);
  cursor: not-allowed;
}

.tool--locked .tool__text {
  font-size: var(--text-sm);
  color: var(--color-subtle);
  margin-top: 2px;
}

.tool__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.soon {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  font-size: var(--text-xs);
  font-weight: 500;
  color: var(--color-muted);
}

@media (max-width: 960px) {
  .tools {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .tools {
    grid-template-columns: 1fr;
    gap: var(--space-3);
  }

  .tool--active {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-4);
    padding: var(--space-5);
  }

  .tool__go {
    width: 100%;
    justify-content: center;
  }

  .tool--locked {
    position: relative;
    display: grid;
    grid-template-columns: auto 1fr;
    column-gap: var(--space-3);
    padding: var(--space-3) var(--space-4);
  }

  .tool--locked .tool__top {
    grid-row: span 2;
  }

  .tool--locked .soon {
    position: absolute;
    top: var(--space-3);
    right: var(--space-3);
  }

  .tool__title--sm {
    margin-top: 0;
    padding-right: 72px;
  }
}
</style>
