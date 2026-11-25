<script setup lang="ts">
// Labelled form field for a decimal typed as text (settings form). The parent keeps the raw
// string and validates it with parseDecimal; errors show right under the input (spec 5.5).
import { useId } from 'vue'

const model = defineModel<string>({ required: true })
withDefaults(
  defineProps<{
    label: string
    hint?: string
    error?: string
    unit?: string
    placeholder?: string
    name?: string
  }>(),
  { hint: '', error: '', unit: 'cm', placeholder: '', name: undefined },
)
const id = useId()
</script>

<template>
  <div class="field" :class="{ 'has-error': error }">
    <label class="field__label" :for="id">{{ label }}</label>
    <div class="field__control">
      <input
        :id="id"
        v-model="model"
        class="field__input"
        type="text"
        inputmode="decimal"
        autocomplete="off"
        :name="name"
        :placeholder="placeholder"
        :aria-invalid="error ? true : undefined"
        :aria-describedby="`${id}-msg`"
      />
      <span v-if="unit" class="field__unit" aria-hidden="true">{{ unit }}</span>
    </div>
    <p :id="`${id}-msg`" class="field__msg" :class="{ 'is-error': error }" aria-live="polite">
      {{ error || hint }}
    </p>
  </div>
</template>

<style scoped>
.field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  min-width: 0;
}

.field__label {
  font-weight: 500;
  font-size: var(--text-base);
}

.field__control {
  position: relative;
}

.field__input {
  width: 100%;
  height: 40px;
  padding: 0 44px 0 var(--space-3);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  font-size: var(--text-md);
  font-variant-numeric: tabular-nums;
  transition: border-color var(--dur) var(--ease);
}

.field__input:hover {
  border-color: var(--color-subtle);
}

.field__input:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: -1px;
  border-color: var(--color-focus);
}

.field__input::placeholder {
  color: var(--color-subtle);
  font-size: var(--text-base);
}

.field__unit {
  position: absolute;
  right: var(--space-3);
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-subtle);
  font-size: var(--text-sm);
  pointer-events: none;
}

.field__msg {
  font-size: var(--text-sm);
  color: var(--color-muted);
  min-height: 1.4em;
}

.field__msg.is-error {
  color: var(--color-danger);
  font-weight: 500;
}

.has-error .field__input {
  border-color: var(--color-danger);
}
</style>
