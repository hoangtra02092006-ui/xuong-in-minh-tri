<script setup lang="ts">
import { computed, reactive } from 'vue'
import type { NestStats, TypeStats } from '@/api/types'
import { apportion, CM_COLUMN_UNIT_MM, M_COLUMN_UNIT_MM } from '@/shared/apportion'
import { categoryColor } from '@/shared/category-colors'
import { formatAreaCm2, formatCm, formatCmFixed, formatInt, formatM, formatSizeCm } from '@/shared/units'
import { getApi } from '@/stores/api-provider'

const props = defineProps<{ stats: NestStats }>()
const broken = reactive(new Set<string>())

const rows = computed(() => [...props.stats.byType].sort((a, b) => a.categoryIndex - b.categoryIndex))
const totals = computed(() =>
  rows.value.reduce((t, r) => ({ pieces: t.pieces + r.pieces, area: t.area + r.shapeAreaMm2 }), {
    pieces: 0,
    area: 0,
  }),
)
/** Lengths shown per column: rows add up exactly to the Total row, no design shows 0 (invariant 11). */
function shown(unitMm: number) {
  const r = rows.value
  const { units, total } = apportion(
    r.map((x) => x.lengthMm),
    unitMm,
    r.map((x) => x.pieces > 0),
  )
  return { rows: units.map((u) => u * unitMm), total: total * unitMm }
}
const cmCol = computed(() => shown(CM_COLUMN_UNIT_MM))
const mCol = computed(() => shown(M_COLUMN_UNIT_MM))
const caption = (r: TypeStats, i: number) =>
  `${formatSizeCm(r.widthMm, r.heightMm)} · ${formatInt(r.pieces)} bản · ${formatM(mCol.value.rows[i])} m`
</script>

<template>
  <div class="table-scroll">
    <table class="data-table bytype">
      <thead>
        <tr>
          <th>Xem trước</th>
          <th>Hình</th>
          <th class="num">Kích thước (cm)</th>
          <th class="num">Số bản</th>
          <th class="num">Diện tích (cm²)</th>
          <th class="num">Chiều dài (cm)</th>
          <th class="num">Chiều dài (m)</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(r, i) in rows" :key="r.fileId + r.categoryIndex">
          <td class="c-thumb">
            <span class="thumb" :title="caption(r, i)">
              <img
                v-if="!broken.has(r.fileId)"
                :src="getApi().getPreviewUrl(r.fileId)"
                :alt="caption(r, i)"
                width="36"
                height="36"
                loading="lazy"
                @error="broken.add(r.fileId)"
              />
            </span>
          </td>
          <td class="name-cell">
            <span class="dot" :style="{ background: categoryColor(r.categoryIndex) }" aria-hidden="true" />
            <span class="name" :title="r.label">{{ r.label }}</span>
          </td>
          <td class="num" data-label="Kích thước (cm)">
            {{ formatCm(r.widthMm) }} x {{ formatCm(r.heightMm) }}
          </td>
          <td class="num" data-label="Số bản">{{ formatInt(r.pieces) }}</td>
          <td class="num" data-label="Diện tích (cm²)">{{ formatAreaCm2(r.shapeAreaMm2) }}</td>
          <td class="num" data-label="Chiều dài (cm)">{{ formatCmFixed(cmCol.rows[i], 1) }}</td>
          <td class="num strong" data-label="Chiều dài (m)">{{ formatM(mCol.rows[i]) }}</td>
        </tr>
      </tbody>
      <tfoot>
        <tr>
          <td class="c-thumb" />
          <td class="name-cell">Tổng</td>
          <td class="c-empty" />
          <td class="num" data-label="Số bản">{{ formatInt(totals.pieces) }}</td>
          <td class="num" data-label="Diện tích (cm²)">{{ formatAreaCm2(totals.area) }}</td>
          <td class="num" data-label="Chiều dài (cm)">{{ formatCmFixed(cmCol.total, 1) }}</td>
          <td class="num" data-label="Chiều dài (m)">{{ formatM(mCol.total) }}</td>
        </tr>
      </tfoot>
    </table>
  </div>
</template>

<style scoped>
.bytype {
  min-width: 680px;
}

@media (max-width: 760px) {
  .bytype {
    min-width: 0;
  }

  .bytype thead {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
  }

  .bytype tr {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: var(--space-2);
    padding: var(--space-3) var(--space-4);
    border-bottom: 1px solid var(--color-border);
  }

  .bytype tfoot tr {
    background: var(--color-surface-2);
    border-top: 1px solid var(--color-border-strong);
    border-bottom: 0;
  }

  .bytype td,
  .bytype tfoot td {
    display: block;
    padding: 0;
    border: 0;
    background: none;
  }

  .bytype td.num {
    text-align: left;
  }

  .bytype td[data-label]::before {
    content: attr(data-label);
    display: block;
    font-size: var(--text-xs);
    font-weight: 400;
    color: var(--color-muted);
  }

  .bytype .c-thumb,
  .bytype .c-empty {
    display: none;
  }

  .bytype .name-cell {
    grid-column: 1 / -1;
    max-width: none;
  }
}

.thumb {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: repeating-conic-gradient(#f1f0eb 0% 25%, #fff 0% 50%) 50% / 8px 8px;
  overflow: hidden;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.name-cell {
  max-width: 280px;
}

.name-cell > * {
  vertical-align: middle;
}

.dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 3px;
  margin-right: var(--space-2);
}

.name {
  display: inline-block;
  max-width: calc(100% - 22px);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.strong {
  font-weight: 600;
}
</style>
