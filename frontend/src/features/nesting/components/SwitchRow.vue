<script setup lang="ts">
import { useId } from 'vue'
import AppSwitch from '@/shared/ui/AppSwitch.vue'

const model = defineModel<boolean>({ required: true })
defineProps<{ label: string; hint: string }>()
const id = useId()
</script>

<template>
  <div class="row">
    <div class="row__text">
      <p :id="id" class="row__label">{{ label }}</p>
      <p class="row__hint">{{ hint }}</p>
    </div>
    <AppSwitch v-model="model" :label="label" :aria-describedby="id" />
  </div>
</template>

<style scoped>
.row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.row__text {
  min-width: 0;
}

.row__label {
  font-weight: 500;
}

.row__hint {
  margin-top: 2px;
  font-size: var(--text-sm);
  color: var(--color-muted);
}
</style>
