<script setup lang="ts">
// Switch with a fixed-width state label so tables do not shift when toggled (spec 5.4).
const model = defineModel<boolean>({ required: true })
withDefaults(defineProps<{ label: string; onText?: string; offText?: string; showState?: boolean }>(), {
  onText: 'Có',
  offText: 'Không',
  showState: true,
})
</script>

<template>
  <button
    type="button"
    role="switch"
    class="switch"
    :aria-checked="model"
    :aria-label="label"
    @click="model = !model"
  >
    <span class="switch__track" :class="{ on: model }"><span class="switch__thumb" /></span>
    <span v-if="showState" class="switch__state">{{ model ? onText : offText }}</span>
  </button>
</template>

<style scoped>
.switch {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: 2px;
  border: 0;
  background: none;
  cursor: pointer;
  border-radius: var(--radius-sm);
}

.switch__track {
  position: relative;
  width: 36px;
  height: 20px;
  border-radius: 10px;
  background: var(--color-border-strong);
  transition: background-color var(--dur) var(--ease);
  flex: none;
}

.switch__track.on {
  background: var(--color-primary);
}

.switch__thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 2px rgb(0 0 0 / 20%);
  transition: transform var(--dur) var(--ease);
}

.on .switch__thumb {
  transform: translateX(16px);
}

.switch__state {
  display: inline-block;
  width: 3.4em;
  text-align: left;
  font-size: var(--text-sm);
  color: var(--color-muted);
}
</style>
