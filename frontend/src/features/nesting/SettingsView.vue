<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import NumberField from '@/shared/ui/NumberField.vue'
import PageHeader from '@/shared/ui/PageHeader.vue'
import { useJobStore } from '@/stores/job'
import { useOrderStore } from '@/stores/order'
import ModePicker from './components/ModePicker.vue'
import OrderSummary from './components/OrderSummary.vue'
import StickyFooter from './components/StickyFooter.vue'
import SwitchRow from './components/SwitchRow.vue'
import WizardSteps from './components/WizardSteps.vue'

const router = useRouter()
const order = useOrderStore()
const jobStore = useJobStore()
const errors = computed(() => order.validation.errors)

const rotateHint = computed(() =>
  order.params.mode === 'TRUE_SHAPE'
    ? 'Tắt sẽ chặn TẤT CẢ các góc xoay, kể cả 45 độ — hình giữ nguyên chiều như trong file. Chỉ tắt khi hình buộc phải đúng chiều (chữ phải đọc ngang, canh sợi vải).'
    : 'Tắt sẽ chặn xoay 90 độ ở mọi hình, ghi đè lên cột "Cho xoay" ở bước trước. Xoay được thường tiết kiệm thêm rất nhiều.',
)

async function submit() {
  const request = order.request
  if (!request || !order.canSubmit) return
  const jobId = await jobStore.submit(request)
  await router.push({ name: 'result', params: { jobId } })
}
</script>

<template>
  <div class="page">
    <WizardSteps :current="2" />
    <PageHeader
      title="Tham số in"
      subtitle="Khai báo khổ cuộn và khoảng cách. Đây là những số quyết định số mét giấy phải dùng."
    />

    <form id="settings-form" class="layout" novalidate @submit.prevent="submit">
      <div class="form-col">
        <AppCard title="Khổ in" subtitle="Kích thước cuộn giấy và giới hạn chiều dài mỗi file.">
          <div class="grid">
            <NumberField
              v-model="order.params.sheetWidthCm"
              name="sheetWidth"
              label="Khổ ngang (cm)"
              hint="Chiều rộng cuộn giấy, đo thực tế không kể phần mép kẹp."
              :error="errors.sheetWidthCm"
            />
            <NumberField
              v-model="order.params.marginCm"
              name="margin"
              label="Lề biên (cm)"
              hint="Chừa trống mỗi phía để máy in không ăn vào hình."
              :error="errors.marginCm"
            />
            <NumberField
              v-model="order.params.maxLengthCm"
              class="grid__wide"
              name="maxLength"
              label="Chiều dài tối đa mỗi file (cm)"
              placeholder="Để trống = không giới hạn"
              hint="Máy in yếu dễ bị lag với file quá dài; app sẽ tự chia thành nhiều file."
              :error="errors.maxLengthCm"
            />
          </div>
        </AppCard>

        <AppCard title="Khoảng cách & xoay" subtitle="Ảnh hưởng trực tiếp tới số mét giấy tốn.">
          <div class="stack">
            <NumberField
              v-model="order.params.gapCm"
              class="gap-field"
              name="gap"
              label="Khoảng cách giữa các hình (cm)"
              hint="Khoảng hở tối thiểu để thợ có đường cắt."
              :error="errors.gapCm"
            />
            <ModePicker v-model="order.params.mode" />
            <div class="switches">
              <SwitchRow
                v-model="order.params.allowRotateGlobal"
                label="Cho phép xoay hình"
                :hint="rotateHint"
              />
              <SwitchRow
                v-model="order.params.drawCutLines"
                label="Vẽ đường cắt quanh mỗi hình"
                hint="Đường xám mờ 0,25pt, giúp thợ cắt nhanh hơn."
              />
            </div>
          </div>
        </AppCard>
      </div>

      <aside class="side-col">
        <OrderSummary />
      </aside>
    </form>
  </div>
  <StickyFooter>
    <AppButton variant="secondary" :to="{ name: 'upload' }">← Quay lại</AppButton>
    <template #actions>
      <AppButton
        variant="primary"
        size="lg"
        type="submit"
        form="settings-form"
        :disabled="!order.canSubmit"
        :loading="jobStore.submitting"
      >
        Ghép file
      </AppButton>
    </template>
  </StickyFooter>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 400px);
  gap: var(--space-5);
  align-items: start;
}

.form-col {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  min-width: 0;
}

.side-col {
  position: sticky;
  top: calc(var(--header-h) + var(--space-4));
  min-width: 0;
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3) var(--space-4);
}

.grid__wide {
  grid-column: 1 / -1;
}

.stack {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.gap-field {
  max-width: calc(50% - var(--space-2));
}

.switches {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-border);
}

@media (max-width: 960px) {
  .layout {
    grid-template-columns: 1fr;
  }

  .side-col {
    position: static;
  }
}

@media (max-width: 640px) {
  .grid {
    grid-template-columns: 1fr;
  }

  .gap-field {
    max-width: none;
  }
}
</style>
