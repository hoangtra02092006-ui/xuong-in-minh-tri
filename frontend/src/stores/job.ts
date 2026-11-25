import { defineStore } from 'pinia'
import { computed, ref, shallowRef } from 'vue'
import { ApiError, toApiError } from '@/api/errors'
import type { NestJob, NestRequest, Sheet } from '@/api/types'
import { openDownload, startDownload } from '@/shared/download'
import { readJson, removeKey, session, writeJson } from '@/shared/storage'
import { DEMO_DOWNLOAD_MESSAGE, toast } from '@/shared/toast'
import { getApi } from './api-provider'

/** Poll while the job runs (spec 5.6). */
export const JOB_POLL_MS = 700
/** Slower poll while TIFs build in the background after DONE (design 4a). */
export const TIF_POLL_MS = 1500
/** Sync errors become a fake FAILED job so the result screen shows them in one place (spec 5.5). */
export const LOCAL_ERROR_ID = 'local-error'
const LOCAL_ERROR_KEY = 'xuong-in.local-error-job'

export function isJobRunning(job: NestJob | null): boolean {
  return job !== null && (job.status === 'PENDING' || job.status === 'RUNNING')
}

export function tifPending(sheet: Sheet): boolean {
  return sheet.tif.status === 'QUEUED' || sheet.tif.status === 'BUILDING'
}

export interface TifProgress {
  ready: number
  failed: number
  total: number
  /** Every sheet is READY or FAILED. */
  settled: boolean
}

export function tifProgressOf(job: NestJob | null): TifProgress | null {
  const sheets = job?.result?.sheets
  if (!sheets || sheets.length === 0) return null
  const ready = sheets.filter((s) => s.tif.status === 'READY').length
  const failed = sheets.filter((s) => s.tif.status === 'FAILED').length
  return { ready, failed, total: sheets.length, settled: ready + failed === sheets.length }
}

export const useJobStore = defineStore('job', () => {
  const job = shallowRef<NestJob | null>(null)
  /** Reading a job that is not in memory (direct URL, reload). */
  const loading = ref(false)
  const loadError = shallowRef<ApiError | null>(null)
  const submitting = ref(false)
  /** Sheets the user clicked while their TIF was not ready: download as soon as READY. */
  const autoDownload = ref<number[]>([])
  const prioritizing = ref<number[]>([])
  let timer: ReturnType<typeof setTimeout> | null = null
  let watching: string | null = null

  const tifProgress = computed(() => tifProgressOf(job.value))

  function stop(): void {
    if (timer) clearTimeout(timer)
    timer = null
    watching = null
  }

  function reset(): void {
    stop()
    job.value = null
    loadError.value = null
    loading.value = false
    autoDownload.value = []
    prioritizing.value = []
    removeKey(session, LOCAL_ERROR_KEY)
  }

  function needsPolling(j: NestJob | null): number | null {
    if (!j) return null
    if (isJobRunning(j)) return JOB_POLL_MS
    if (j.status === 'DONE' && j.result?.sheets.some(tifPending)) return TIF_POLL_MS
    return null
  }

  /** Click on a READY sheet: we are inside the user's gesture, a new tab is fine (spec 5.6 c). */
  function deliverTif(index: number): void {
    const api = getApi()
    if (api.isDemo) toast.info(DEMO_DOWNLOAD_MESSAGE)
    else if (job.value) openDownload(api.sheetDownloadUrl(job.value.jobId, index, 'tif'))
  }

  /**
   * A sheet the user asked for became READY during polling: no gesture any more, so a new tab
   * would be a blocked popup. Download in place, and keep a button in a toast in case the
   * browser still refuses (we cannot see a silent block).
   */
  function autoDeliverTif(index: number): void {
    const api = getApi()
    if (api.isDemo) {
      toast.info(DEMO_DOWNLOAD_MESSAGE)
      return
    }
    if (!job.value) return
    const url = api.sheetDownloadUrl(job.value.jobId, index, 'tif')
    const action = { label: `Tải TIF tấm ${index + 1}`, run: () => openDownload(url) }
    if (startDownload(url)) {
      toast.action(
        'success',
        `Tấm ${index + 1}: TIF đã xong, đang tải về. Không thấy file thì bấm nút.`,
        action,
      )
    } else {
      toast.action('info', `Tấm ${index + 1}: TIF đã xong. Bấm nút để tải.`, action)
    }
  }

  function applyJob(next: NestJob): void {
    job.value = next
    const sheets = next.result?.sheets ?? []
    const waiting: number[] = []
    for (const index of autoDownload.value) {
      const status = sheets[index]?.tif.status
      if (status === 'READY') autoDeliverTif(index)
      else if (status === 'FAILED')
        toast.error(`Tấm ${index + 1}: ${sheets[index].tif.error?.message ?? 'Lỗi dựng TIF.'}`)
      else waiting.push(index)
    }
    autoDownload.value = waiting
  }

  function schedule(jobId: string): void {
    const wait = needsPolling(job.value)
    if (wait === null || watching !== jobId) return
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => void tick(jobId), wait)
  }

  async function tick(jobId: string): Promise<void> {
    timer = null
    try {
      const next = await getApi().getJob(jobId)
      if (watching !== jobId) return
      applyJob(next)
    } catch (err) {
      const e = toApiError(err)
      // One failed poll on the network is skipped; the next poll tries again (spec 5.6).
      if (e.code !== 'NETWORK_ERROR' && watching === jobId) {
        loadError.value = e
        stop()
        return
      }
    }
    schedule(jobId)
  }

  function watch(jobId: string): void {
    stop()
    watching = jobId
    schedule(jobId)
  }

  /** Creates a job. Returns the id to navigate to (LOCAL_ERROR_ID on a sync error). */
  async function submit(request: NestRequest): Promise<string> {
    reset()
    submitting.value = true
    try {
      const created = await getApi().createJob(request)
      job.value = created
      watch(created.jobId)
      return created.jobId
    } catch (err) {
      const e = toApiError(err)
      toast.error(e.message)
      const failed: NestJob = {
        jobId: LOCAL_ERROR_ID,
        status: 'FAILED',
        progress: 0,
        createdAt: new Date().toISOString(),
        error: { code: e.code, message: e.message, details: e.details },
      }
      job.value = failed
      writeJson(session, LOCAL_ERROR_KEY, failed)
      return LOCAL_ERROR_ID
    } finally {
      submitting.value = false
    }
  }

  /** Result screen entry: reuse the job in memory, otherwise read it from the server. */
  async function open(jobId: string): Promise<void> {
    loadError.value = null
    if (job.value?.jobId === jobId) {
      if (watching !== jobId) watch(jobId)
      return
    }
    stop()
    job.value = null
    if (jobId === LOCAL_ERROR_ID) {
      const saved = readJson<NestJob>(session, LOCAL_ERROR_KEY)
      if (saved) job.value = saved
      else
        loadError.value = new ApiError({
          code: 'JOB_NOT_FOUND',
          message: 'Không tìm thấy lần ghép này.',
        })
      return
    }
    loading.value = true
    try {
      const fetched = await getApi().getJob(jobId)
      job.value = fetched
      watch(jobId)
    } catch (err) {
      loadError.value = toApiError(err)
    } finally {
      loading.value = false
    }
  }

  /**
   * TIF button on a sheet that is not ready, or "Thử lại" on a failed one: build it first and
   * download automatically when READY (design 4a). Buttons are never locked.
   */
  async function requestTif(index: number): Promise<void> {
    const current = job.value
    if (!current?.result) return
    const sheet = current.result.sheets[index]
    if (!sheet) return
    if (sheet.tif.status === 'READY') {
      deliverTif(index)
      return
    }
    if (!autoDownload.value.includes(index)) autoDownload.value.push(index)
    prioritizing.value = [...prioritizing.value, index]
    try {
      await getApi().prioritizeTif(current.jobId, index)
      // Refresh now so the new state shows without waiting for the next poll.
      const next = await getApi().getJob(current.jobId)
      if (job.value?.jobId === current.jobId) applyJob(next)
      if (watching !== current.jobId || timer === null) watch(current.jobId)
    } catch (err) {
      autoDownload.value = autoDownload.value.filter((i) => i !== index)
      toast.error(toApiError(err).message)
    } finally {
      prioritizing.value = prioritizing.value.filter((i) => i !== index)
    }
  }

  return {
    job,
    loading,
    loadError,
    submitting,
    autoDownload,
    prioritizing,
    tifProgress,
    submit,
    open,
    stop,
    reset,
    requestTif,
  }
})
