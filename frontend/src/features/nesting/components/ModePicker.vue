<script setup lang="ts">
import type { NestingMode } from '@/api/types'

const model = defineModel<NestingMode>({ required: true })

// Order: safest to most saving; wording from spec 5.5.
const MODES: { value: NestingMode; title: string; text: string }[] = [
  {
    value: 'ORTHOGONAL',
    title: 'Khung chữ nhật (mặc định)',
    text: 'Mỗi hình chiếm trọn khung chữ nhật, chỉ xoay 0 hoặc 90 độ. Bố trí thành lưới ngay ngắn, cắt kiểu nào cũng được.',
  },
  {
    value: 'FREE',
    title: 'Nhồi hình nhỏ vào góc trống',
    text: 'Hình nhỏ được chui LỌT HẲN vào phần trống bên trong khung của hình lớn. Chỉ dùng được khi cắt theo viền hình.',
  },
  {
    value: 'TRUE_SHAPE',
    title: 'Xếp lồng theo hình thật',
    text: 'Các hình lồng vào nhau theo đường nét thật, xoay nhiều góc kể cả góc chéo. CHỈ dùng khi cắt theo viền hình — cắt theo hình chữ nhật sẽ hỏng bản in.',
  },
]
</script>

<template>
  <fieldset class="modes">
    <legend class="modes__legend">Cách xếp</legend>
    <label v-for="m in MODES" :key="m.value" class="mode" :class="{ 'is-on': model === m.value }">
      <input v-model="model" class="mode__radio" type="radio" name="mode" :value="m.value" />
      <span class="mode__body">
        <span class="mode__title">{{ m.title }}</span>
        <span class="mode__text">{{ m.text }}</span>
      </span>
    </label>
  </fieldset>
</template>

<style scoped>
.modes {
  margin: 0;
  padding: 0;
  border: 0;
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}

.modes__legend {
  padding: 0;
  margin-bottom: var(--space-2);
  font-weight: 500;
}

.mode {
  display: flex;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  cursor: pointer;
  transition:
    border-color var(--dur) var(--ease),
    background-color var(--dur) var(--ease);
}

.mode:hover {
  border-color: var(--color-border-strong);
}

.mode.is-on {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}

.mode:focus-within {
  outline: 2px solid var(--color-focus);
  outline-offset: 1px;
}

.mode__radio {
  flex: none;
  width: 16px;
  height: 16px;
  margin: 3px 0 0;
  accent-color: var(--color-primary);
}

.mode__radio:focus-visible {
  outline: none;
}

.mode__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.mode__title {
  font-weight: 600;
}

.mode__text {
  font-size: var(--text-sm);
  color: var(--color-muted);
}
</style>
