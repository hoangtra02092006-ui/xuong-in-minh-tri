<script setup lang="ts">
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import StateBlock from '@/shared/ui/StateBlock.vue'
</script>

<template>
  <div class="page">
    <AppCard>
      <StateBlock
        title="Không tìm thấy trang"
        text="Đường dẫn này không có trong app. Có thể link đã cũ hoặc gõ nhầm."
        icon="search"
      >
        <AppButton variant="primary" :to="{ name: 'home' }">Về trang chủ</AppButton>
      </StateBlock>
    </AppCard>
  </div>
</template>
