import { reactive } from 'vue'

export type ToastKind = 'success' | 'error' | 'info'

export interface Toast {
  id: number
  kind: ToastKind
  text: string
}

/** Toasts hide after 4 seconds (spec 5.1). */
export const TOAST_MS = 4000

const state = reactive({ items: [] as Toast[] })
let nextId = 1

export function dismissToast(id: number): void {
  const i = state.items.findIndex((t) => t.id === id)
  if (i >= 0) state.items.splice(i, 1)
}

export function showToast(kind: ToastKind, text: string): number {
  const id = nextId++
  state.items.push({ id, kind, text })
  setTimeout(() => dismissToast(id), TOAST_MS)
  return id
}

export const toast = {
  success: (text: string) => showToast('success', text),
  error: (text: string) => showToast('error', text),
  info: (text: string) => showToast('info', text),
}

export function useToasts() {
  return state
}

export const DEMO_DOWNLOAD_MESSAGE =
  'Chế độ demo không xuất được PDF thật. Hãy chạy backend và tắt VITE_USE_MOCK để tải file.'
