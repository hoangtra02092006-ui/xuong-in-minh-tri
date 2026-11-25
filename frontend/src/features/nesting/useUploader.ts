import { reactive } from 'vue'
import { toApiError } from '@/api/errors'
import type { UploadedFile } from '@/api/types'
import { toast } from '@/shared/toast'
import { getApi } from '@/stores/api-provider'

export interface UploadRow {
  key: number
  name: string
  percent: number
  status: 'waiting' | 'uploading' | 'done' | 'error'
}

/** Progress bars stay ~1.2 s after the batch ends, then clear (spec 5.4). */
export const CLEAR_AFTER_MS = 1200

/**
 * Uploads one file per request, in order, with a progress row per file (spec 5.4). Failed files
 * get an error toast; successful ones are passed to `onUploaded` immediately so the table fills.
 */
export function useUploader(onUploaded: (file: UploadedFile) => void) {
  const state = reactive({ rows: [] as UploadRow[], busy: false })
  let key = 1
  const queue: { row: UploadRow; file: File }[] = []
  let okInBatch = 0

  async function run() {
    state.busy = true
    while (queue.length > 0) {
      const { row, file } = queue.shift() as { row: UploadRow; file: File }
      row.status = 'uploading'
      try {
        const uploaded = await getApi().uploadFile(file, (p) => (row.percent = p))
        row.percent = 100
        row.status = 'done'
        okInBatch += 1
        onUploaded(uploaded)
      } catch (err) {
        row.status = 'error'
        toast.error(`${file.name}: ${toApiError(err).message}`)
      }
    }
    state.busy = false
    if (okInBatch > 0) toast.success(`Đã nạp ${okInBatch} file.`)
    okInBatch = 0
    const finished = [...state.rows]
    setTimeout(() => {
      if (!state.busy) state.rows = state.rows.filter((r) => !finished.includes(r))
    }, CLEAR_AFTER_MS)
  }

  function add(files: File[]) {
    for (const file of files) {
      const row = reactive<UploadRow>({ key: key++, name: file.name, percent: 0, status: 'waiting' })
      state.rows.push(row)
      queue.push({ row, file })
    }
    if (!state.busy && queue.length > 0) void run()
  }

  return { state, add }
}
