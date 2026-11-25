import { reactive } from 'vue'

export type ToastKind = 'success' | 'error' | 'info'

export interface ToastAction {
  label: string
  run: () => void
}

export interface Toast {
  id: number
  kind: ToastKind
  text: string
  action?: ToastAction
}

/** Toasts hide after 4 seconds (spec 5.1). */
export const TOAST_MS = 4000
/** A toast with a button stays longer so there is time to press it. */
export const ACTION_TOAST_MS = 15_000

const state = reactive({ items: [] as Toast[] })
let nextId = 1

export function dismissToast(id: number): void {
  const i = state.items.findIndex((t) => t.id === id)
  if (i >= 0) state.items.splice(i, 1)
}

export function showToast(kind: ToastKind, text: string, action?: ToastAction): number {
  const id = nextId++
  state.items.push(action ? { id, kind, text, action } : { id, kind, text })
  setTimeout(() => dismissToast(id), action ? ACTION_TOAST_MS : TOAST_MS)
  return id
}

/** Runs a toast's button and closes the toast. */
export function runToastAction(id: number): void {
  const t = state.items.find((x) => x.id === id)
  dismissToast(id)
  t?.action?.run()
}

export const toast = {
  success: (text: string) => showToast('success', text),
  error: (text: string) => showToast('error', text),
  info: (text: string) => showToast('info', text),
  action: (kind: ToastKind, text: string, action: ToastAction) => showToast(kind, text, action),
}

export function useToasts() {
  return state
}

export const DEMO_DOWNLOAD_MESSAGE =
  'Chế độ demo không xuất được PDF thật. Hãy chạy backend và tắt VITE_USE_MOCK để tải file.'
