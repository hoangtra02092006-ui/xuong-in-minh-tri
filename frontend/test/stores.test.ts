import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { createMockApi } from '@/api/mock'
import { RUN_MS, TIF_BUILD_MS } from '@/api/mock/jobs'
import type { ApiService } from '@/api/service'
import type { NestJob } from '@/api/types'
import { useToasts } from '@/shared/toast'
import { setApi } from '@/stores/api-provider'
import { JOB_POLL_MS, LOCAL_ERROR_ID, TIF_POLL_MS, tifProgressOf, useJobStore } from '@/stores/job'
import { ORDER_STORAGE_KEY, useOrderStore } from '@/stores/order'
import { file } from './helpers'

function demoApi(): ApiService {
  return createMockApi({
    storage: null,
    delay: () => Promise.resolve(),
    readImage: async () => ({ width: 96, height: 96 }),
    objectUrl: () => 'blob:x',
  })
}

beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
  useToasts().items.splice(0)
  setActivePinia(createPinia())
})

describe('order store', () => {
  it('adds files at the end with defaults and keeps the aspect ratio locked', () => {
    const order = useOrderStore()
    order.addFiles([file('a', 130, 40), file('b', 50, 70)])
    expect(order.items.map((i) => i.file.id)).toEqual(['a', 'b'])
    order.setSide('a', 'width', 65)
    expect(order.items[0]).toMatchObject({ widthMm: 65, heightMm: 20 })
    order.setSide('a', 'height', null)
    expect(order.items[0]).toMatchObject({ widthMm: 65, heightMm: 20 })
    order.resetSize('a')
    expect(order.items[0]).toMatchObject({ widthMm: 130, heightMm: 40 })
    order.setQuantity('a', '0')
    expect(order.items[0].quantity).toBe(1)
    order.setQuantity('a', '25')
    order.setAllowRotate('b', false)
    expect(order.totals).toEqual({ types: 2, pieces: 26, areaMm2: 130 * 40 * 25 + 50 * 70 })
    order.remove('b')
    expect(order.items).toHaveLength(1)
    // Unknown ids are ignored.
    order.setSide('zz', 'width', 10)
    order.setQuantity('zz', 3)
    order.setAllowRotate('zz', true)
    order.resetSize('zz')
  })

  it('persists the draft to localStorage and restores it after a reload', async () => {
    const order = useOrderStore()
    order.addFiles([file('a', 130, 40)])
    order.setQuantity('a', 12)
    order.params.sheetWidthCm = '60,5'
    order.params.mode = 'FREE'
    await nextTick()
    expect(JSON.parse(localStorage.getItem(ORDER_STORAGE_KEY)!).items[0].quantity).toBe(12)

    setActivePinia(createPinia()) // simulates a page reload
    const reloaded = useOrderStore()
    expect(reloaded.items[0]).toMatchObject({ quantity: 12, widthMm: 130 })
    expect(reloaded.params).toMatchObject({ sheetWidthCm: '60,5', mode: 'FREE', gapCm: '0,3' })
    expect(reloaded.request?.sheetWidthMm).toBe(605)
  })

  it('ignores a corrupt saved draft', () => {
    localStorage.setItem(ORDER_STORAGE_KEY, '{"items": 3}')
    expect(useOrderStore().items).toEqual([])
    localStorage.setItem(ORDER_STORAGE_KEY, 'not json')
    setActivePinia(createPinia())
    expect(useOrderStore().items).toEqual([])
  })

  it('reset clears items and params (home "Ghép file in")', () => {
    const order = useOrderStore()
    order.addFiles([file('a', 10, 10)])
    order.params.gapCm = '1'
    order.reset()
    expect(order.items).toEqual([])
    expect(order.params.gapCm).toBe('0,3')
  })

  it('derived flags: continue, submit, estimate, warnings', () => {
    const order = useOrderStore()
    expect(order.canContinue).toBe(false)
    order.addFiles([file('w', 600, 590), file('i', 50, 50, 'IMAGE')])
    expect(order.canContinue).toBe(true)
    expect(order.tooWide.map((i) => i.file.id)).toEqual(['w'])
    expect(order.nonNestable).toEqual([])
    order.params.mode = 'TRUE_SHAPE'
    expect(order.nonNestable.map((i) => i.file.id)).toEqual(['i'])
    expect(order.estimateMm).toBeCloseTo((600 * 590 + 2500) / 560, 6)
    order.params.sheetWidthCm = '0'
    expect(order.canSubmit).toBe(false)
    expect(order.request).toBeNull()
  })
})

describe('job store', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    setApi(demoApi())
  })
  afterEach(() => {
    useJobStore().stop()
    vi.useRealTimers()
  })

  async function uploadAndRequest(api: ApiService, names: string[], quantity = 20) {
    const order = useOrderStore()
    for (const n of names) order.addFiles([await api.uploadFile(new File(['%PDF'], n))])
    order.items.forEach((i) => (i.quantity = quantity))
    return order.request!
  }

  it('submit → polls every 700 ms until DONE, then keeps polling TIFs until all settle', async () => {
    const api = demoApi()
    setApi(api)
    const spy = vi.spyOn(api, 'getJob')
    const store = useJobStore()
    const jobId = await store.submit(await uploadAndRequest(api, ['nhan-20x18.pdf'], 30))
    expect(store.job?.status).toBe('PENDING')

    await vi.advanceTimersByTimeAsync(JOB_POLL_MS * 2)
    expect(store.job?.status).toBe('RUNNING')
    await vi.advanceTimersByTimeAsync(RUN_MS)
    expect(store.job?.status).toBe('DONE')
    const sheets = store.job!.result!.sheets.length
    expect(store.tifProgress).toMatchObject({ ready: 0, total: sheets, settled: false })

    await vi.advanceTimersByTimeAsync(TIF_BUILD_MS * sheets + TIF_POLL_MS * 2)
    expect(store.tifProgress?.settled).toBe(true)
    const calls = spy.mock.calls.length
    await vi.advanceTimersByTimeAsync(TIF_POLL_MS * 5)
    expect(spy.mock.calls.length).toBe(calls) // polling stopped
    expect(spy.mock.calls.every((c) => c[0] === jobId)).toBe(true)
  })

  it('reload on the result page restores the job from the URL', async () => {
    const api = demoApi()
    setApi(api)
    const jobId = await useJobStore().submit(await uploadAndRequest(api, ['nhan-13x4.pdf']))
    await vi.advanceTimersByTimeAsync(RUN_MS + JOB_POLL_MS)

    setActivePinia(createPinia()) // page reload: store is empty
    const fresh = useJobStore()
    expect(fresh.job).toBeNull()
    await fresh.open(jobId)
    expect(fresh.job?.jobId).toBe(jobId)
    expect(fresh.job?.status).toBe('DONE')
    expect(fresh.loading).toBe(false)
  })

  it('unknown job shows a load error instead of a blank screen', async () => {
    const store = useJobStore()
    await store.open('khong-co')
    expect(store.loadError?.code).toBe('JOB_NOT_FOUND')
    expect(store.job).toBeNull()
  })

  it('sync error becomes a local FAILED job with a toast, and survives reload', async () => {
    const api = demoApi()
    setApi(api)
    const store = useJobStore()
    const id = await store.submit(await uploadAndRequest(api, ['loi-qua-kho-60x59.pdf']))
    expect(id).toBe(LOCAL_ERROR_ID)
    expect(store.job).toMatchObject({ status: 'FAILED', error: { code: 'ITEM_WIDER_THAN_SHEET' } })
    expect(useToasts().items.at(-1)?.kind).toBe('error')

    setActivePinia(createPinia())
    const fresh = useJobStore()
    await fresh.open(LOCAL_ERROR_ID)
    expect(fresh.job?.error?.code).toBe('ITEM_WIDER_THAN_SHEET')
    sessionStorage.clear()
    setActivePinia(createPinia())
    const empty = useJobStore()
    await empty.open(LOCAL_ERROR_ID)
    expect(empty.loadError?.message).toBe('Không tìm thấy lần ghép này.')
  })

  it('engine failure arrives as FAILED and stops polling', async () => {
    const api = demoApi()
    setApi(api)
    const store = useJobStore()
    const req = await uploadAndRequest(api, ['nhan-4x17.pdf'])
    req.maxSheetLengthMm = 30
    req.items[0].allowRotate = false
    await store.submit(req)
    await vi.advanceTimersByTimeAsync(RUN_MS + JOB_POLL_MS)
    expect(store.job?.status).toBe('FAILED')
    expect(store.job?.error?.message).toContain('Chiều dài tối đa mỗi file')
  })

  it('a network error during polling is skipped; the next poll continues', async () => {
    const api = demoApi()
    setApi(api)
    const store = useJobStore()
    await store.submit(await uploadAndRequest(api, ['nhan-5x7.pdf']))
    const real = api.getJob.bind(api)
    let failOnce = true
    vi.spyOn(api, 'getJob').mockImplementation(async (id) => {
      if (failOnce) {
        failOnce = false
        const { ApiError } = await import('@/api/errors')
        throw new ApiError({ code: 'NETWORK_ERROR', message: 'mất mạng' })
      }
      return real(id)
    })
    await vi.advanceTimersByTimeAsync(RUN_MS + JOB_POLL_MS * 3)
    expect(store.loadError).toBeNull()
    expect(store.job?.status).toBe('DONE')
  })

  it('requestTif on a queued sheet prioritizes it and downloads when READY (demo: info toast)', async () => {
    const api = demoApi()
    setApi(api)
    const prio = vi.spyOn(api, 'prioritizeTif')
    const store = useJobStore()
    const jobId = await store.submit(await uploadAndRequest(api, ['nhan-20x18.pdf'], 30))
    await vi.advanceTimersByTimeAsync(RUN_MS + JOB_POLL_MS)
    const last = store.job!.result!.sheets.length - 1
    expect(store.job!.result!.sheets[last].tif.status).toBe('QUEUED')

    await store.requestTif(last)
    expect(prio).toHaveBeenCalledWith(jobId, last)
    expect(store.autoDownload).toEqual([last])
    await vi.advanceTimersByTimeAsync(TIF_BUILD_MS * 2 + TIF_POLL_MS * 2)
    expect(store.job!.result!.sheets[last].tif.status).toBe('READY')
    expect(store.autoDownload).toEqual([])
    expect(useToasts().items.some((t) => t.text.includes('Chế độ demo'))).toBe(true)

    // A READY sheet downloads straight away.
    useToasts().items.splice(0)
    await store.requestTif(last)
    expect(useToasts().items).toHaveLength(1)
  })

  it('requestTif error shows a toast and clears the auto download', async () => {
    const api = demoApi()
    setApi(api)
    const store = useJobStore()
    await store.submit(await uploadAndRequest(api, ['nhan-20x18.pdf'], 30))
    await vi.advanceTimersByTimeAsync(RUN_MS + JOB_POLL_MS)
    vi.spyOn(api, 'prioritizeTif').mockRejectedValue(new Error('x'))
    await store.requestTif(1)
    expect(store.autoDownload).toEqual([])
    expect(useToasts().items.at(-1)?.kind).toBe('error')
    await store.requestTif(99) // out of range: no-op
  })

  it('tifProgressOf counts ready and failed sheets', () => {
    const sheet = (status: 'READY' | 'FAILED' | 'QUEUED') => ({
      index: 0,
      widthMm: 570,
      lengthMm: 1000,
      fillRate: 0.9,
      placements: [],
      tif: { status, sizeBytes: null, error: null },
    })
    const job = (statuses: ('READY' | 'FAILED' | 'QUEUED')[]): NestJob => ({
      jobId: 'j',
      status: 'DONE',
      progress: 100,
      createdAt: '',
      result: { sheets: statuses.map(sheet), stats: {} as never },
    })
    expect(tifProgressOf(null)).toBeNull()
    expect(tifProgressOf(job(['READY', 'QUEUED']))).toEqual({ ready: 1, failed: 0, total: 2, settled: false })
    expect(tifProgressOf(job(['READY', 'FAILED']))).toEqual({ ready: 1, failed: 1, total: 2, settled: true })
  })
})
