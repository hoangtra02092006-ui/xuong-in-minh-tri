<script setup lang="ts">
import { computed, onBeforeUnmount, watch } from 'vue'
import { formatCm, formatInt, formatPercent } from '@/shared/units'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import PageHeader from '@/shared/ui/PageHeader.vue'
import ProgressBar from '@/shared/ui/ProgressBar.vue'
import StatTile from '@/shared/ui/StatTile.vue'
import StateBlock from '@/shared/ui/StateBlock.vue'
import { getApi } from '@/stores/api-provider'
import { isJobRunning, useJobStore } from '@/stores/job'
import ByTypeTable from './components/ByTypeTable.vue'
import SheetPreview from './components/SheetPreview.vue'
import SheetTable from './components/SheetTable.vue'
import TifProgressLine from './components/TifProgressLine.vue'
import WizardSteps from './components/WizardSteps.vue'
import { useDownloads } from './useDownloads'

const props = defineProps<{ jobId: string }>()
const jobStore = useJobStore()
const downloads = useDownloads(() => props.jobId)

watch(
  () => props.jobId,
  (id) => void jobStore.open(id),
  { immediate: true },
)
// Leaving the screen stops polling (spec 5.6); the server keeps building TIFs.
onBeforeUnmount(() => jobStore.stop())

const job = computed(() => (jobStore.job?.jobId === props.jobId ? jobStore.job : null))
const result = computed(() => job.value?.result ?? null)
const stats = computed(() => result.value?.stats ?? null)
const zip = (f: 'pdf' | 'tif' | 'cut') => getApi().zipDownloadUrl(props.jobId, f)
const tifLabel = computed(() => {
  const p = jobStore.tifProgress
  return p && !p.settled ? `Tải tất cả TIF (${p.ready}/${p.total})` : 'Tải tất cả TIF'
})
</script>

<template>
  <div class="page">
    <WizardSteps :current="3" />
    <PageHeader title="Kết quả ghép" subtitle="Kiểm tra bố trí rồi tải file TIF gửi máy in.">
      <template #actions>
        <AppButton variant="ghost" :to="{ name: 'home' }">
          <template #icon><AppIcon name="arrow-left" :size="16" /></template>
          Về trang chủ
        </AppButton>
      </template>
    </PageHeader>

    <AppCard v-if="jobStore.loadError">
      <StateBlock
        variant="error"
        :title="
          jobStore.loadError.code === 'JOB_NOT_FOUND'
            ? 'Không tìm thấy lần ghép này'
            : 'Không đọc được kết quả'
        "
        :text="jobStore.loadError.message"
      >
        <AppButton variant="secondary" :to="{ name: 'home' }">Về trang chủ</AppButton>
        <AppButton variant="primary" :to="{ name: 'settings' }">Ghép lại</AppButton>
      </StateBlock>
    </AppCard>

    <AppCard v-else-if="!job">
      <StateBlock variant="loading" title="Đang đọc kết quả…" />
    </AppCard>

    <AppCard v-else-if="isJobRunning(job)">
      <StateBlock
        variant="loading"
        title="Đang tính toán bố trí…"
        text="App đang thử hàng trăm phương án xếp để tìm cách tốn ít giấy nhất. Việc này thường mất vài giây."
      >
        <ProgressBar class="run-bar" :value="job.progress" label="Tiến độ ghép" />
      </StateBlock>
    </AppCard>

    <AppCard v-else-if="job.status === 'FAILED'">
      <StateBlock
        variant="error"
        title="Không ghép được"
        :text="job.error?.message ?? 'Có lỗi không mong muốn.'"
      >
        <AppButton variant="primary" :to="{ name: 'settings' }">Sửa tham số và thử lại</AppButton>
      </StateBlock>
    </AppCard>

    <template v-else-if="result && stats">
      <div class="stats">
        <StatTile label="Số file xuất ra" :value="formatInt(stats.totalSheets)" unit="file" />
        <StatTile label="Tổng chiều dài" :value="formatCm(stats.totalLengthMm)" unit="cm" tone="primary" />
        <StatTile label="Tỷ lệ lấp đầy" :value="formatPercent(stats.fillRate)" unit="%" />
        <StatTile
          label="Tiết kiệm so với in rời"
          :value="formatPercent(stats.savedVsIndividualPct / 100)"
          unit="%"
          tone="success"
        />
      </div>

      <AppCard
        v-if="stats.byType.length"
        title="Từng mẫu ăn mấy mét"
        subtitle="Chiều dài thực tế đã tính cả phần giấy bỏ đi, chia đều theo tỷ lệ. Cộng lại đúng bằng tổng chiều dài."
        flush
      >
        <ByTypeTable :stats="stats" />
      </AppCard>

      <AppCard
        title="Chi tiết từng file"
        subtitle="TIF đưa thẳng vào RIP máy in. File CẮT dùng sau khi in xong."
        flush
      >
        <TifProgressLine v-if="jobStore.tifProgress" :progress="jobStore.tifProgress" />
        <SheetTable :job-id="jobId" :sheets="result.sheets" />
      </AppCard>

      <AppCard title="Xem trước bố trí" subtitle="Di chuột lên từng hình để xem tên file và kích thước.">
        <SheetPreview :sheets="result.sheets" :job-id="jobId" />
      </AppCard>

      <div class="final">
        <AppButton variant="ghost" :to="{ name: 'settings' }">
          <template #icon><AppIcon name="arrow-left" :size="16" /></template>
          Ghép lại với tham số khác
        </AppButton>
        <div class="final__dl">
          <AppButton variant="secondary" :href="zip('pdf')" @click="downloads.guardDemo">
            <template #icon><AppIcon name="file" :size="16" /></template>
            Tải tất cả PDF
          </AppButton>
          <AppButton variant="secondary" :href="zip('cut')" @click="downloads.guardDemo">
            <template #icon><AppIcon name="scissors" :size="16" /></template>
            Tải tất cả file cắt
          </AppButton>
          <AppButton variant="primary" size="lg" :href="zip('tif')" @click="downloads.guardDemo">
            <template #icon><AppIcon name="download" :size="18" /></template>
            {{ tifLabel }}
            <template #trailing><span class="pill">đề xuất</span></template>
          </AppButton>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.page > * + * {
  margin-top: var(--space-4);
}

.page > .wizard-steps + * {
  margin-top: 0;
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-3);
}

.run-bar {
  width: min(320px, 70vw);
}

.final {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--space-3);
  padding-top: var(--space-2);
}

.final__dl {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.pill {
  height: 20px;
  padding: 0 7px;
  border-radius: 999px;
  background: rgb(255 255 255 / 20%);
  font-size: var(--text-xs);
  font-weight: 600;
  line-height: 20px;
}

@media (max-width: 760px) {
  .stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .final {
    flex-direction: column-reverse;
    align-items: stretch;
  }

  .final__dl {
    flex-direction: column-reverse;
    align-items: stretch;
  }
}
</style>
