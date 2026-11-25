<script setup lang="ts">
withDefaults(defineProps<{ value: number; label: string; tone?: 'primary' | 'danger' | 'success' }>(), {
  tone: 'primary',
})
</script>

<template>
  <div
    class="progress"
    role="progressbar"
    :aria-label="label"
    :aria-valuenow="Math.round(value)"
    aria-valuemin="0"
    aria-valuemax="100"
  >
    <div
      class="progress__bar"
      :class="`tone-${tone}`"
      :style="{ transform: `scaleX(${Math.max(0, Math.min(100, value)) / 100})` }"
    />
  </div>
</template>

<style scoped>
.progress {
  height: 6px;
  border-radius: 3px;
  background: #ebeae4;
  overflow: hidden;
}

.progress__bar {
  height: 100%;
  width: 100%;
  transform-origin: left center;
  transition: transform 300ms var(--ease);
}

.tone-primary {
  background: var(--color-primary);
}

.tone-success {
  background: var(--color-success);
}

.tone-danger {
  background: var(--color-danger);
}
</style>
