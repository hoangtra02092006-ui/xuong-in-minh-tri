<script setup lang="ts">
import { useRouter } from 'vue-router'
import { formatAreaCm2, formatInt } from '@/shared/units'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import PageHeader from '@/shared/ui/PageHeader.vue'
import StateBlock from '@/shared/ui/StateBlock.vue'
import { useOrderStore } from '@/stores/order'
import FileDropzone from './components/FileDropzone.vue'
import ItemTable from './components/ItemTable.vue'
import StickyFooter from './components/StickyFooter.vue'
import WizardSteps from './components/WizardSteps.vue'
import { useUploader } from './useUploader'

const router = useRouter()
const order = useOrderStore()
const uploader = useUploader((file) => order.addFiles([file]))
</script>

<template>
  <div class="page">
    <WizardSteps :current="1" />
    <PageHeader title="Nạp file in" subtitle="Chọn các file cần in và khai báo số lượng mỗi loại.">
      <template #actions>
        <AppButton variant="ghost" :to="{ name: 'home' }">
          <template #icon><AppIcon name="arrow-left" :size="16" /></template>
          Về trang chủ
        </AppButton>
      </template>
    </PageHeader>

    <FileDropzone :rows="uploader.state.rows" :compact="order.items.length > 0" @files="uploader.add" />

    <AppCard
      class="list"
      :title="order.items.length ? 'Danh sách hình' : ''"
      :subtitle="
        order.items.length ? 'Sửa kích thước một cạnh, cạnh kia tự tính theo đúng tỷ lệ của file.' : ''
      "
      :flush="order.items.length > 0"
    >
      <ItemTable v-if="order.items.length" />
      <StateBlock
        v-else
        title="Chưa có file nào"
        text="Kéo thả file PDF, PNG hoặc JPG vào khung phía trên. App sẽ tự đọc kích thước thật của từng file; nếu đọc sai, bạn sửa lại trực tiếp trong bảng."
        icon="image"
      />
    </AppCard>
  </div>
  <StickyFooter>
    <span>
      Tổng: <strong>{{ formatInt(order.totals.types) }}</strong> loại hình ·
      <strong>{{ formatInt(order.totals.pieces) }}</strong> bản · diện tích hình
      <strong>{{ formatAreaCm2(order.totals.areaMm2) }} cm²</strong>
    </span>
    <template #actions>
      <AppButton
        variant="primary"
        size="lg"
        :disabled="!order.canContinue"
        @click="router.push({ name: 'settings' })"
      >
        Tiếp tục →
      </AppButton>
    </template>
  </StickyFooter>
</template>

<style scoped>
.list {
  margin-top: var(--space-4);
}

strong {
  color: var(--color-text);
  font-weight: 600;
}
</style>
