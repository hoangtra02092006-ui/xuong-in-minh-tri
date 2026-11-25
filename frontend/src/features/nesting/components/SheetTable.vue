<script setup lang="ts">
import type { Sheet } from '@/api/types'
import { formatCm, formatInt, formatPercent } from '@/shared/units'
import AppIcon from '@/shared/ui/AppIcon.vue'
import AppSpinner from '@/shared/ui/AppSpinner.vue'
import { getApi } from '@/stores/api-provider'
import { useJobStore } from '@/stores/job'
import { useDownloads } from '../useDownloads'
import TifCell from './TifCell.vue'

const props = defineProps<{ jobId: string; sheets: Sheet[] }>()
const jobStore = useJobStore()
const downloads = useDownloads(() => props.jobId)
const tifHref = (i: number) => getApi().sheetDownloadUrl(props.jobId, i, 'tif')
</script>

<template>
  <div class="table-scroll">
    <table class="data-table sheets">
      <thead>
        <tr>
          <th class="num">STT</th>
          <th class="num">Kích thước (cm)</th>
          <th class="num">Số hình</th>
          <th class="num">Lấp đầy (%)</th>
          <th class="col-dl">Tải về</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="s in sheets" :key="s.index">
          <td class="num c-no" data-label="Tấm">{{ s.index + 1 }}</td>
          <td class="num c-size" data-label="Kích thước (cm)">
            {{ formatCm(s.widthMm) }} x {{ formatCm(s.lengthMm) }}
          </td>
          <td class="num c-count" data-label="Số hình">{{ formatInt(s.placements.length) }}</td>
          <td class="num c-fill" data-label="Lấp đầy (%)">{{ formatPercent(s.fillRate) }}</td>
          <td class="c-dl">
            <div class="dl">
              <TifCell
                :tif="s.tif"
                :href="tifHref(s.index)"
                :sheet-no="s.index + 1"
                :auto="jobStore.autoDownload.includes(s.index)"
                :working="jobStore.prioritizing.includes(s.index)"
                @request="jobStore.requestTif(s.index)"
                @ready="downloads.guardDemo"
              />
              <button
                type="button"
                class="dl__link"
                :aria-label="`Tải PDF tấm ${s.index + 1}`"
                :aria-busy="downloads.isBusy(s.index, 'pdf') || undefined"
                @click="downloads.sheet(s.index, 'pdf')"
              >
                <AppSpinner v-if="downloads.isBusy(s.index, 'pdf')" :size="13" />
                <AppIcon v-else name="file" :size="15" />
                PDF
              </button>
              <button
                type="button"
                class="dl__link"
                title="Đường cắt + 4 dấu định vị cho máy cắt Graphtec"
                :aria-label="`Tải file CẮT tấm ${s.index + 1}`"
                :aria-busy="downloads.isBusy(s.index, 'cut') || undefined"
                @click="downloads.sheet(s.index, 'cut')"
              >
                <AppSpinner v-if="downloads.isBusy(s.index, 'cut')" :size="13" />
                <AppIcon v-else name="scissors" :size="15" />
                CẮT
              </button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.sheets {
  min-width: 600px;
}

@media (max-width: 760px) {
  .sheets {
    min-width: 0;
  }

  .sheets thead {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
  }

  .sheets tbody tr {
    display: grid;
    grid-template-columns: 36px minmax(0, 1.5fr) minmax(0, 1fr) minmax(0, 1fr);
    gap: var(--space-2);
    padding: var(--space-3) var(--space-4);
    border-bottom: 1px solid var(--color-border);
  }

  .sheets tbody tr:last-child {
    border-bottom: 0;
  }

  .sheets td {
    display: block;
    padding: 0;
    border: 0;
    text-align: left;
  }

  .sheets td.num {
    text-align: left;
  }

  .sheets td::before {
    content: attr(data-label);
    display: block;
    font-size: var(--text-xs);
    color: var(--color-muted);
  }

  .sheets td.c-dl {
    grid-column: 1 / -1;
  }

  .sheets td.c-dl::before {
    display: none;
  }

  .dl {
    flex-wrap: wrap;
  }
}

.col-dl {
  width: 1%;
}

.dl {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
}

.dl__link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
  font-size: var(--text-sm);
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
}

.dl__link:hover {
  border-color: var(--color-border-strong);
  background: var(--color-surface-2);
}

.dl__link .icon {
  color: var(--color-muted);
}
</style>
