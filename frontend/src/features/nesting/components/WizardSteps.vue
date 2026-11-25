<script setup lang="ts">
import { computed } from 'vue'
import AppStepper, { type StepItem } from '@/shared/ui/AppStepper.vue'
import { useJobStore } from '@/stores/job'

const props = defineProps<{ current: 1 | 2 | 3 }>()
const jobStore = useJobStore()

const steps = computed<StepItem[]>(() => {
  const jobId = jobStore.job?.jobId
  const defs: { label: string; to?: StepItem['to'] }[] = [
    { label: 'Nạp file', to: { name: 'upload' } },
    { label: 'Tham số in', to: { name: 'settings' } },
    { label: 'Kết quả', to: jobId ? { name: 'result', params: { jobId } } : undefined },
  ]
  return defs.map((d, i) => ({
    label: d.label,
    to: d.to,
    state: i + 1 < props.current ? 'done' : i + 1 === props.current ? 'current' : 'locked',
  }))
})
</script>

<template>
  <AppStepper :steps="steps" class="wizard-steps" />
</template>

<style scoped>
.wizard-steps {
  margin-bottom: var(--space-4);
}
</style>
