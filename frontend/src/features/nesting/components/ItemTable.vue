<script setup lang="ts">
import { reactive, ref } from 'vue'
import { isResized } from '@/shared/aspect'
import type { OrderItem } from '@/shared/order'
import { cmInputValue, cmToMm, formatSizeCm } from '@/shared/units'
import AppBadge from '@/shared/ui/AppBadge.vue'
import AppDialog from '@/shared/ui/AppDialog.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import AppSwitch from '@/shared/ui/AppSwitch.vue'
import DecimalInput from '@/shared/ui/DecimalInput.vue'
import { getApi } from '@/stores/api-provider'
import { useOrderStore } from '@/stores/order'

const order = useOrderStore()
const brokenPreview = reactive(new Set<string>())
const zoomed = ref<OrderItem | null>(null)

const previewOf = (item: OrderItem) => getApi().getPreviewUrl(item.file.id) || item.file.previewUrl
const original = (item: OrderItem) => ({ widthMm: item.file.widthMm, heightMm: item.file.heightMm })
const resized = (item: OrderItem) => isResized(original(item), item)
const toMm = (cm: number | null) => (cm === null ? null : cmToMm(cm))

function caption(item: OrderItem): string {
  let text = formatSizeCm(item.widthMm, item.heightMm)
  if (item.file.trimmed) {
    text += ` · đã bỏ phần trắng bao quanh (khổ gốc ${formatSizeCm(item.file.sourceWidthMm, item.file.sourceHeightMm)})`
  }
  return text
}
</script>

<template>
  <div class="items">
    <table class="data-table items__table">
      <thead>
        <tr>
          <th class="col-thumb">Xem trước</th>
          <th>Tên file</th>
          <th class="num col-size">Rộng (cm)</th>
          <th class="num col-size">Dài (cm)</th>
          <th class="num col-qty">Số lượng</th>
          <th class="col-rot">Cho xoay</th>
          <th class="col-act"><span class="sr-only">Thao tác</span></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in order.items" :key="item.file.id">
          <td class="cell-thumb">
            <button
              v-if="!brokenPreview.has(item.file.id)"
              type="button"
              class="thumb"
              :aria-label="`Xem to ${item.file.originalName}`"
              @click="zoomed = item"
            >
              <img
                :src="previewOf(item)"
                alt=""
                width="44"
                height="44"
                loading="lazy"
                @error="brokenPreview.add(item.file.id)"
              />
            </button>
          </td>
          <td class="cell-name">
            <div class="name">
              <span class="name__text" :title="item.file.originalName">{{ item.file.originalName }}</span>
              <span class="name__badges">
                <AppBadge>{{ item.file.type === 'PDF' ? 'PDF' : 'Ảnh' }}</AppBadge>
                <AppBadge
                  v-if="item.file.trimmed"
                  tone="primary"
                  :title="`Khổ gốc ${formatSizeCm(item.file.sourceWidthMm, item.file.sourceHeightMm)}`"
                >
                  đã cắt trắng
                </AppBadge>
              </span>
            </div>
            <p v-if="resized(item)" class="name__orig">
              gốc {{ formatSizeCm(item.file.widthMm, item.file.heightMm) }}
            </p>
          </td>
          <td class="num cell-w" data-label="Rộng (cm)">
            <DecimalInput
              :value="item.widthMm"
              :format="cmInputValue"
              :label="`Rộng (cm) của ${item.file.originalName}`"
              :invalid="!(item.widthMm > 0)"
              @input="(v) => order.setSide(item.file.id, 'width', toMm(v))"
            />
          </td>
          <td class="num cell-h" data-label="Dài (cm)">
            <DecimalInput
              :value="item.heightMm"
              :format="cmInputValue"
              :label="`Dài (cm) của ${item.file.originalName}`"
              :invalid="!(item.heightMm > 0)"
              @input="(v) => order.setSide(item.file.id, 'height', toMm(v))"
            />
          </td>
          <td class="num cell-q" data-label="Số lượng">
            <DecimalInput
              :value="item.quantity"
              :format="String"
              integer
              :label="`Số lượng của ${item.file.originalName}`"
              @input="(v) => v !== null && v >= 1 && order.setQuantity(item.file.id, v)"
              @commit="(raw) => order.setQuantity(item.file.id, raw)"
            />
          </td>
          <td class="cell-rot" data-label="Cho xoay">
            <AppSwitch
              :model-value="item.allowRotate"
              :label="`Cho xoay ${item.file.originalName}`"
              @update:model-value="(v) => order.setAllowRotate(item.file.id, v)"
            />
          </td>
          <td class="cell-act">
            <div class="actions">
              <button
                v-if="resized(item)"
                type="button"
                class="icon-btn"
                :title="`Trả về kích thước gốc ${formatSizeCm(item.file.widthMm, item.file.heightMm)}`"
                :aria-label="`Hoàn tác kích thước ${item.file.originalName}`"
                @click="order.resetSize(item.file.id)"
              >
                <AppIcon name="undo" :size="17" />
              </button>
              <button
                type="button"
                class="icon-btn icon-btn--danger"
                :title="`Xoá ${item.file.originalName}`"
                :aria-label="`Xoá ${item.file.originalName}`"
                @click="order.remove(item.file.id)"
              >
                <AppIcon name="trash" :size="17" />
              </button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>

    <AppDialog v-if="zoomed" :label="zoomed.file.originalName" @close="zoomed = null">
      <figure class="zoom">
        <img :src="previewOf(zoomed)" :alt="zoomed.file.originalName" class="zoom__img" />
        <figcaption class="zoom__cap">
          <strong>{{ zoomed.file.originalName }}</strong>
          <span>{{ caption(zoomed) }}</span>
        </figcaption>
      </figure>
    </AppDialog>
  </div>
</template>

<style scoped>
.items__table {
  table-layout: auto;
}

.col-thumb {
  width: 72px;
}

.col-size {
  width: 118px;
}

.col-qty {
  width: 100px;
}

.col-rot {
  width: 112px;
}

.col-act {
  width: 84px;
}

.thumb {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  padding: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: repeating-conic-gradient(#f1f0eb 0% 25%, #fff 0% 50%) 50% / 10px 10px;
  overflow: hidden;
  cursor: zoom-in;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.name {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

.cell-name {
  max-width: 0;
  width: 100%;
}

.name__text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
  min-width: 0;
}

.name__badges {
  display: inline-flex;
  gap: var(--space-1);
  flex: none;
}

.name__orig {
  font-size: var(--text-xs);
  color: var(--color-subtle);
  font-variant-numeric: tabular-nums;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 2px;
}

.icon-btn {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--color-muted);
  cursor: pointer;
}

.icon-btn:hover {
  background: rgb(27 28 26 / 6%);
  color: var(--color-text);
}

.icon-btn--danger:hover {
  background: var(--color-danger-soft);
  color: var(--color-danger);
}

.zoom {
  margin: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
}

.zoom__img {
  max-width: min(560px, 100%);
  max-height: 60vh;
  object-fit: contain;
  background: repeating-conic-gradient(#f1f0eb 0% 25%, #fff 0% 50%) 50% / 16px 16px;
  border: 1px solid var(--color-border);
}

.zoom__cap {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 2px;
  color: var(--color-muted);
  font-variant-numeric: tabular-nums;
}

.zoom__cap strong {
  color: var(--color-text);
  font-weight: 600;
  overflow-wrap: anywhere;
}

/* Phone: each row becomes a compact card, inputs keep their labels. */
@media (max-width: 760px) {
  .items__table thead {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
  }

  .items__table tbody tr {
    display: grid;
    grid-template-columns: 44px repeat(5, minmax(0, 1fr));
    grid-template-areas:
      'thumb name name name name act'
      'w w h h q q'
      'rot rot rot rot rot rot';
    gap: var(--space-2) var(--space-2);
    padding: var(--space-3) var(--space-4);
    border-bottom: 1px solid var(--color-border);
  }

  .items__table tbody tr:last-child {
    border-bottom: 0;
  }

  .items__table td {
    display: block;
    padding: 0;
    border: 0;
  }

  .cell-thumb {
    grid-area: thumb;
  }

  .cell-name {
    grid-area: name;
    max-width: none;
    width: auto;
    min-width: 0;
    align-self: center;
  }

  .name {
    flex-wrap: wrap;
    row-gap: 2px;
  }

  .cell-act {
    grid-area: act;
    align-self: start;
  }

  .cell-w {
    grid-area: w;
  }

  .cell-h {
    grid-area: h;
  }

  .cell-q {
    grid-area: q;
  }

  .items__table td.cell-rot {
    grid-area: rot;
    display: flex;
    align-items: center;
    gap: var(--space-3);
  }

  .cell-w::before,
  .cell-h::before,
  .cell-q::before,
  .cell-rot::before {
    content: attr(data-label);
    display: block;
    font-size: var(--text-xs);
    color: var(--color-muted);
    text-align: left;
    margin-bottom: 2px;
  }

  .cell-rot::before {
    margin: 0;
  }
}
</style>
