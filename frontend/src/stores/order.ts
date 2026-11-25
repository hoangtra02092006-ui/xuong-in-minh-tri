import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import type { UploadedFile } from '@/api/types'
import { resizeLocked, type Side } from '@/shared/aspect'
import {
  buildNestRequest,
  DEFAULT_PARAMS,
  estimateMinLengthMm,
  hasInvalidSize,
  itemsWiderThanSheet,
  newItem,
  nonNestableItems,
  normalizeQuantity,
  orderTotals,
  validateParams,
  type OrderItem,
  type OrderParams,
} from '@/shared/order'
import { local, readJson, writeJson } from '@/shared/storage'

/** Draft order survives a reload on steps 1-2 (design 4). Bump the version when the shape changes. */
export const ORDER_STORAGE_KEY = 'xuong-in.order.v1'

interface SavedOrder {
  items: OrderItem[]
  params: OrderParams
}

function isSavedOrder(v: unknown): v is SavedOrder {
  if (typeof v !== 'object' || v === null) return false
  const o = v as Partial<SavedOrder>
  return Array.isArray(o.items) && typeof o.params === 'object' && o.params !== null
}

export const useOrderStore = defineStore('order', () => {
  const saved = readJson<unknown>(local, ORDER_STORAGE_KEY)
  const initial = isSavedOrder(saved) ? saved : null

  const items = ref<OrderItem[]>(initial?.items ?? [])
  const params = ref<OrderParams>({ ...DEFAULT_PARAMS, ...(initial?.params ?? {}) })

  watch(
    [items, params],
    () => writeJson(local, ORDER_STORAGE_KEY, { items: items.value, params: params.value }),
    { deep: true },
  )

  const find = (fileId: string) => items.value.find((i) => i.file.id === fileId)

  function addFiles(files: UploadedFile[]): void {
    items.value.push(...files.map(newItem))
  }

  /** Locked-ratio edit from a cm value; ignored when empty, <= 0 or unreadable (spec 5.4). */
  function setSide(fileId: string, side: Side, valueMm: number | null): void {
    const item = find(fileId)
    if (!item) return
    const next = resizeLocked({ widthMm: item.file.widthMm, heightMm: item.file.heightMm }, side, valueMm)
    if (!next) return
    item.widthMm = next.widthMm
    item.heightMm = next.heightMm
  }

  function resetSize(fileId: string): void {
    const item = find(fileId)
    if (!item) return
    item.widthMm = item.file.widthMm
    item.heightMm = item.file.heightMm
  }

  function setQuantity(fileId: string, raw: string | number): void {
    const item = find(fileId)
    if (item) item.quantity = normalizeQuantity(raw)
  }

  function setAllowRotate(fileId: string, value: boolean): void {
    const item = find(fileId)
    if (item) item.allowRotate = value
  }

  function remove(fileId: string): void {
    items.value = items.value.filter((i) => i.file.id !== fileId)
  }

  /** "Ghép file in" on home starts a clean order (spec 5.3). */
  function reset(): void {
    items.value = []
    params.value = { ...DEFAULT_PARAMS }
  }

  const totals = computed(() => orderTotals(items.value))
  const canContinue = computed(() => items.value.length > 0 && !hasInvalidSize(items.value))
  const validation = computed(() => validateParams(params.value))
  const estimateMm = computed(() => estimateMinLengthMm(items.value, validation.value.parsed))
  const tooWide = computed(() =>
    itemsWiderThanSheet(items.value, validation.value.parsed, params.value.allowRotateGlobal),
  )
  const nonNestable = computed(() => nonNestableItems(items.value, params.value.mode))
  const canSubmit = computed(() => canContinue.value && validation.value.parsed !== null)
  const request = computed(() =>
    validation.value.parsed ? buildNestRequest(items.value, params.value, validation.value.parsed) : null,
  )

  return {
    items,
    params,
    addFiles,
    setSide,
    resetSize,
    setQuantity,
    setAllowRotate,
    remove,
    reset,
    totals,
    canContinue,
    validation,
    estimateMm,
    tooWide,
    nonNestable,
    canSubmit,
    request,
  }
})
