<script setup lang="ts">
import logoUrl from '@/assets/logo.png'
import { toast } from '@/shared/toast'
import AppIcon from '@/shared/ui/AppIcon.vue'

defineProps<{ demo: boolean }>()

// Spec 5.1 wording, with the suggested "tải TIF" correction.
const HELP =
  'Quy trình: nạp file in → nhập khổ ngang, lề, khoảng cách → bấm Ghép file → tải TIF về gửi máy in.'
</script>

<template>
  <header class="header">
    <div class="header__inner">
      <RouterLink to="/" class="brand" aria-label="Xưởng in Minh Trí, về trang chủ">
        <img :src="logoUrl" alt="" width="34" height="34" class="brand__logo" />
        <span class="brand__text">
          <span class="brand__name">Xưởng in Minh Trí</span>
          <span class="brand__tag">Ghép file in tiết kiệm khổ</span>
        </span>
      </RouterLink>
      <div class="header__right">
        <span v-if="demo" class="demo" title="Chưa nối máy chủ: dữ liệu chạy ngay trên trình duyệt"
          >Chế độ demo</span
        >
        <button type="button" class="help" @click="toast.info(HELP)">
          <AppIcon name="help" :size="18" />
          <span class="help__text">Trợ giúp</span>
        </button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 40;
  height: var(--header-h);
  background: rgb(255 255 255 / 92%);
  backdrop-filter: saturate(1.4) blur(8px);
  border-bottom: 1px solid var(--color-border);
}

.header__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  height: 100%;
  max-width: var(--page-max);
  margin: 0 auto;
  padding: 0 var(--space-5);
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
  color: inherit;
  text-decoration: none;
  border-radius: var(--radius-sm);
}

.brand__logo {
  border-radius: 50%;
  flex: none;
}

.brand__text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.25;
}

.brand__name {
  font-weight: 650;
  font-size: var(--text-md);
  white-space: nowrap;
}

.brand__tag {
  font-size: var(--text-xs);
  color: var(--color-muted);
  white-space: nowrap;
}

.header__right {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex: none;
}

.demo {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 var(--space-2);
  border: 1px solid #e9d9b4;
  border-radius: 999px;
  background: var(--color-warning-soft);
  color: var(--color-warning);
  font-size: var(--text-xs);
  font-weight: 600;
  white-space: nowrap;
}

.help {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
  color: var(--color-muted);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 500;
}

.help:hover {
  color: var(--color-text);
  border-color: var(--color-border-strong);
}

@media (max-width: 640px) {
  .header__inner {
    padding: 0 var(--space-4);
  }

  .brand__tag {
    display: none;
  }

  .help {
    width: 34px;
    padding: 0;
    justify-content: center;
  }

  .help__text {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
  }
}
</style>
