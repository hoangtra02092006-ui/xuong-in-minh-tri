<script setup lang="ts">
import { computed } from 'vue'
import { formatAreaCm2, formatInt, formatM, formatSizeCm } from '@/shared/units'
import AppBadge from '@/shared/ui/AppBadge.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import InlineAlert from '@/shared/ui/InlineAlert.vue'
import { categoryColor } from '@/shared/category-colors'
import { useOrderStore } from '@/stores/order'

const order = useOrderStore()
const lockedRotate = (allow: boolean) => !allow || !order.params.allowRotateGlobal
const nonNestableNames = computed(() => order.nonNestable.map((i) => i.file.originalName))
</script>

<template>
  <div class="summary">
    <section class="estimate" aria-labelledby="estimate-title">
      <p id="estimate-title" class="estimate__label">Ước lượng tối thiểu</p>
      <p class="estimate__value">
        <template v-if="order.estimateMm !== null">
          <span class="estimate__num">{{ formatM(order.estimateMm) }}</span>
          <span class="estimate__unit">m</span>
        </template>
        <span v-else class="estimate__none">Nhập khổ ngang và lề hợp lệ để xem</span>
      </p>
      <p class="estimate__note">
        Diện tích hình chia cho bề rộng khả dụng (khổ trừ 2 lần lề). Kết quả ghép thật luôn dài hơn số này vì
        còn khoảng cách và chỗ trống.
      </p>
    </section>

    <InlineAlert v-if="order.tooWide.length" tone="danger" title="Có hình rộng hơn khổ in">
      <ul class="names">
        <li v-for="i in order.tooWide" :key="i.file.id">
          {{ i.file.originalName }} ({{ formatSizeCm(i.widthMm, i.heightMm) }})
        </li>
      </ul>
      Tăng khổ ngang, giảm lề hoặc thu nhỏ hình ở bước 1, nếu không máy chủ sẽ từ chối.
    </InlineAlert>

    <InlineAlert
      v-if="nonNestableNames.length"
      tone="warning"
      :title="`${nonNestableNames.length} mẫu sẽ được xếp như khối chữ nhật`"
    >
      Ảnh PNG/JPG không có đường nét vector, còn hình đã sửa kích thước thì không đọc lại được nét, nên không
      lồng được vào nhau:
      <ul class="names">
        <li v-for="n in nonNestableNames" :key="n">{{ n }}</li>
      </ul>
    </InlineAlert>

    <AppCard title="Danh sách sẽ ghép" :subtitle="`${formatInt(order.totals.types)} loại hình`" flush>
      <ul class="list">
        <li v-for="(item, i) in order.items" :key="item.file.id" class="list__row">
          <span class="dot" :style="{ background: categoryColor(i) }" aria-hidden="true" />
          <span class="list__name" :title="item.file.originalName">{{ item.file.originalName }}</span>
          <span class="list__size num">{{ formatSizeCm(item.widthMm, item.heightMm) }}</span>
          <AppBadge tone="neutral" class="num">x{{ formatInt(item.quantity) }}</AppBadge>
          <AppBadge v-if="lockedRotate(item.allowRotate)" tone="warning">khoá xoay</AppBadge>
        </li>
      </ul>
      <template #footer>
        <dl class="totals">
          <div>
            <dt>Tổng bản in</dt>
            <dd class="num">{{ formatInt(order.totals.pieces) }}</dd>
          </div>
          <div>
            <dt>Diện tích hình</dt>
            <dd class="num">{{ formatAreaCm2(order.totals.areaMm2) }} cm²</dd>
          </div>
        </dl>
      </template>
    </AppCard>
  </div>
</template>

<style scoped>
.summary {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.estimate {
  padding: var(--space-4) var(--space-5);
  border-radius: var(--radius-lg);
  background: var(--color-primary-soft);
  border: 1px solid #cfe4e0;
}

.estimate__label {
  font-size: var(--text-sm);
  color: var(--color-primary-ink);
  font-weight: 500;
}

.estimate__value {
  display: flex;
  align-items: baseline;
  gap: var(--space-1);
  margin: 2px 0 var(--space-1);
}

.estimate__num {
  font-size: var(--text-2xl);
  font-weight: 650;
  color: var(--color-primary-ink);
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.estimate__unit {
  color: var(--color-primary-ink);
  font-size: var(--text-md);
}

.estimate__none {
  color: var(--color-muted);
}

.estimate__note {
  font-size: var(--text-xs);
  color: var(--color-muted);
}

.names {
  margin: var(--space-1) 0;
  padding-left: 1.2em;
  color: var(--color-text);
}

.list {
  list-style: none;
  margin: 0;
  padding: 0 var(--space-5) var(--space-3);
  max-height: 340px;
  overflow-y: auto;
}

.list__row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 6px 0;
  border-bottom: 1px solid var(--color-border);
}

.list__row:last-child {
  border-bottom: 0;
}

.dot {
  flex: none;
  width: 8px;
  height: 8px;
  border-radius: 2px;
}

.list__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.list__size {
  color: var(--color-muted);
  font-size: var(--text-sm);
}

.totals {
  margin: 0;
  display: grid;
  gap: 2px;
}

.totals div {
  display: flex;
  justify-content: space-between;
}

.totals dt {
  color: var(--color-muted);
}

.totals dd {
  margin: 0;
  font-weight: 600;
}

@media (max-width: 640px) {
  .list {
    padding: 0 var(--space-4) var(--space-3);
  }

  .estimate {
    padding: var(--space-4);
  }
}
</style>
