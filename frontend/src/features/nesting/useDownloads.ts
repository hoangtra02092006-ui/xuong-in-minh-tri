import { reactive } from 'vue'
import { toApiError } from '@/api/errors'
import type { ExportFormat } from '@/api/types'
import { DEMO_DOWNLOAD_MESSAGE, toast } from '@/shared/toast'
import { getApi } from '@/stores/api-provider'

/** PDF and CẮT are built on click; show a spinner per link while building (design 4a). */
export function useDownloads(jobId: () => string) {
  const busy = reactive(new Set<string>())
  const key = (i: number, f: ExportFormat) => `${i}-${f}`

  async function sheet(index: number, format: ExportFormat) {
    const api = getApi()
    if (api.isDemo) {
      toast.info(DEMO_DOWNLOAD_MESSAGE)
      return
    }
    const k = key(index, format)
    if (busy.has(k)) return
    busy.add(k)
    try {
      await api.downloadSheet(jobId(), index, format)
    } catch (err) {
      toast.error(`Tấm ${index + 1}: ${toApiError(err).message}`)
    } finally {
      busy.delete(k)
    }
  }

  /** Zip links are plain links; demo mode blocks them with an info toast (spec 5.6). */
  function guardDemo(e: Event) {
    if (getApi().isDemo) {
      e.preventDefault()
      toast.info(DEMO_DOWNLOAD_MESSAGE)
    }
  }

  return { busy, isBusy: (i: number, f: ExportFormat) => busy.has(key(i, f)), sheet, guardDemo }
}
