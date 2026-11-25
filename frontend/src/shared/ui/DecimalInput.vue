<script setup lang="ts">
// Numeric input that keeps the raw text while typing (CONSTRAINTS: type="text" inputmode="decimal"
// + parseDecimal; type="number" + v-model once froze inputs). Shows the formatted value when idle.
import { ref, watch } from 'vue'
import { parseDecimal } from '../units'

const props = withDefaults(
  defineProps<{
    value: number | null
    format?: (n: number) => string
    label: string
    invalid?: boolean
    integer?: boolean
    placeholder?: string
  }>(),
  {
    format: (n: number) => String(n).replace('.', ','),
    invalid: false,
    integer: false,
    placeholder: '',
  },
)

const emit = defineEmits<{
  /** Parsed value on every keystroke; null when empty or not a number. */
  input: [value: number | null]
  /** Raw text when the field loses focus. */
  commit: [raw: string]
}>()

const text = ref(props.value === null ? '' : props.format(props.value))
const focused = ref(false)

watch(
  () => props.value,
  (v) => {
    if (!focused.value) text.value = v === null ? '' : props.format(v)
  },
)

function onInput(e: Event) {
  text.value = (e.target as HTMLInputElement).value
  emit('input', parseDecimal(text.value))
}

function onBlur() {
  focused.value = false
  emit('commit', text.value)
  text.value = props.value === null ? '' : props.format(props.value)
}
</script>

<template>
  <input
    class="decimal-input"
    :class="{ 'is-invalid': invalid }"
    type="text"
    :inputmode="integer ? 'numeric' : 'decimal'"
    autocomplete="off"
    :aria-label="label"
    :aria-invalid="invalid || undefined"
    :placeholder="placeholder"
    :value="text"
    @focus="focused = true"
    @input="onInput"
    @blur="onBlur"
    @keydown.enter="($event.target as HTMLInputElement).blur()"
  />
</template>

<style scoped>
.decimal-input {
  width: 100%;
  height: 34px;
  padding: 0 var(--space-2);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  text-align: right;
  font-variant-numeric: tabular-nums;
  transition: border-color var(--dur) var(--ease);
}

.decimal-input:hover {
  border-color: var(--color-subtle);
}

.decimal-input:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: -1px;
  border-color: var(--color-focus);
}

.decimal-input.is-invalid {
  border-color: var(--color-danger);
  background: var(--color-danger-soft);
}
</style>
