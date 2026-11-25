import { describe, expect, it } from 'vitest'
import { ApiError } from '@/api/errors'
import { createMockApi, pdfPlaceholder, type MockDeps } from '@/api/mock'
import { sizeFromMediaBox, sizeFromName } from '@/api/mock/files'
import { FAIL_TIF_MARKER, RUN_MS, TIF_BUILD_MS } from '@/api/mock/jobs'
import { DEMO_TILT_DEG } from '@/api/mock/layout'
import type { NestRequest } from '@/api/types'

class MemStorage {
  data = new Map<string, string>()
  getItem(k: string) {
    return this.data.get(k) ?? null
  }
  setItem(k: string, v: string) {
    this.data.set(k, v)
  }
}

function setup(extra: Partial<MockDeps> = {}) {
  const clock = { t: 1_700_000_000_000 }
  let n = 0
  const storage = new MemStorage()
  const deps: MockDeps = {
    now: () => clock.t,
    delay: () => Promise.resolve(),
    storage,
    readImage: async () => ({ width: 600, height: 400 }),
    makeId: () => `id-${++n}`,
    objectUrl: () => 'blob:local',
    ...extra,
  }
  return { api: createMockApi(deps), clock, storage, deps }
}

const pdf = (name: string, body = '%PDF-1.4') => new File([body], name, { type: 'application/pdf' })

function request(fileIds: string[], patch: Partial<NestRequest> = {}): NestRequest {
  return {
    mode: 'ORTHOGONAL',
    sheetWidthMm: 570,
    marginMm: 5,
    gapMm: 3,
    maxSheetLengthMm: 1000,
    allowRotateGlobal: true,
    drawCutLines: false,
    items: fileIds.map((fileId) => ({ fileId, quantity: 20, allowRotate: true })),
    ...patch,
  }
}

describe('demo file reading (spec 5.8)', () => {
  it('reads cm size from a name like nhan-13x4.pdf', () => {
    expect(sizeFromName('nhan-13x4.pdf')).toEqual({ widthMm: 130, heightMm: 40 })
    expect(sizeFromName('logo 2,5x1.5.pdf')).toEqual({ widthMm: 25, heightMm: 15 })
    expect(sizeFromName('khong-co-kich-thuoc.pdf')).toBeNull()
  })

  it('reads the MediaBox in points', () => {
    expect(sizeFromMediaBox('<< /MediaBox [0 0 595.28 841.89] >>')).toEqual({ widthMm: 210, heightMm: 297 })
    expect(sizeFromMediaBox('/MediaBox [0 0 0 0]')).toBeNull()
    expect(sizeFromMediaBox('nothing')).toBeNull()
  })

  it('uploads a PDF with size from the name, falls back to MediaBox then default', async () => {
    const { api } = setup()
    const progress: number[] = []
    const a = await api.uploadFile(pdf('nhan-13x4.pdf'), (p) => progress.push(p))
    expect(a).toMatchObject({ widthMm: 130, heightMm: 40, type: 'PDF', trimmed: false, pageCount: 1 })
    expect(progress.at(-1)).toBe(100)
    expect(a.previewUrl.startsWith('data:image/svg+xml')).toBe(true)
    const b = await api.uploadFile(pdf('a4.pdf', '%PDF /MediaBox [0 0 595.28 841.89]'))
    expect(b).toMatchObject({ widthMm: 210, heightMm: 297 })
    const c = await api.uploadFile(pdf('lạ.pdf'))
    expect(c).toMatchObject({ widthMm: 100, heightMm: 100 })
  })

  it('reads image pixels at 96 DPI', async () => {
    const { api } = setup()
    const img = await api.uploadFile(new File(['x'], 'anh.png', { type: 'image/png' }))
    expect(img).toMatchObject({ type: 'IMAGE', widthMm: 158.75, heightMm: 105.83, previewUrl: 'blob:local' })
    expect(api.getPreviewUrl(img.id)).toBe('blob:local')
  })

  it('rejects other formats, huge files and unreadable images with the API codes', async () => {
    const { api } = setup({ readImage: () => Promise.reject(new Error('x')) })
    await expect(api.uploadFile(new File(['x'], 'a.ai'))).rejects.toMatchObject({
      code: 'UNSUPPORTED_FORMAT',
    })
    const big = new File(['x'], 'big.pdf')
    Object.defineProperty(big, 'size', { value: 60 * 1024 * 1024 })
    await expect(api.uploadFile(big)).rejects.toMatchObject({ code: 'FILE_TOO_LARGE' })
    await expect(api.uploadFile(new File(['x'], 'a.jpg'))).rejects.toMatchObject({ code: 'SIZE_UNREADABLE' })
  })

  it('placeholder keeps the aspect ratio', () => {
    const url = decodeURIComponent(pdfPlaceholder(300, 50))
    expect(url).toContain('width="360"')
    expect(url).toContain('height="60"')
  })
})

describe('demo nesting job', () => {
  async function uploaded(names: string[]) {
    const ctx = setup()
    const ids: string[] = []
    for (const n of names) ids.push((await ctx.api.uploadFile(pdf(n))).id)
    return { ...ctx, ids }
  }

  it('runs PENDING → RUNNING → DONE over a few seconds with the real schema', async () => {
    const { api, clock, ids } = await uploaded(['nhan-13x4.pdf', 'nhan-20x18.pdf', 'nhan-5x7.pdf'])
    const created = await api.createJob(request(ids))
    expect(created.status).toBe('PENDING')
    clock.t += RUN_MS / 2
    const running = await api.getJob(created.jobId)
    expect(running.status).toBe('RUNNING')
    expect(running.progress).toBeGreaterThan(0)
    clock.t += RUN_MS
    const done = await api.getJob(created.jobId)
    expect(done.status).toBe('DONE')
    expect(done.finishedAt).toBeDefined()
    const { sheets, stats } = done.result!
    expect(stats.totalPieces).toBe(60)
    expect(sheets.reduce((s, sh) => s + sh.placements.length, 0)).toBe(60)
    expect(stats.totalSheets).toBe(sheets.length)
    expect(sheets.every((s) => s.lengthMm <= 1000 && s.fillRate > 0 && s.fillRate <= 1)).toBe(true)
    // Invariant 11: rows add up exactly to the total, none is zero.
    const sum = stats.byType.reduce((s, t) => s + Math.round(t.lengthMm * 100), 0)
    expect(sum).toBe(Math.round(stats.totalLengthMm * 100))
    expect(stats.byType.every((t) => t.lengthMm > 0)).toBe(true)
    expect(stats.fillRate).toBeGreaterThan(0)
    expect(stats.fillRate).toBeLessThanOrEqual(1)
    expect(stats.savedVsIndividualPct).toBeGreaterThan(0)
  })

  it('keeps every piece inside the sheet margins and apart by the gap', async () => {
    const { api, clock, ids } = await uploaded(['nhan-13x4.pdf', 'nhan-20x18.pdf', 'nhan-4x17.pdf'])
    const { jobId } = await api.createJob(request(ids))
    clock.t += RUN_MS
    const sheets = (await api.getJob(jobId)).result!.sheets
    for (const s of sheets) {
      for (const p of s.placements) {
        expect(p.xMm).toBeGreaterThanOrEqual(5)
        expect(p.yMm).toBeGreaterThanOrEqual(5)
        expect(p.xMm + p.wMm).toBeLessThanOrEqual(565 + 1e-6)
        expect(p.yMm + p.hMm).toBeLessThanOrEqual(s.lengthMm - 5 + 1e-6)
      }
      for (let i = 0; i < s.placements.length; i++) {
        for (let j = i + 1; j < s.placements.length; j++) {
          const a = s.placements[i]
          const b = s.placements[j]
          const apart =
            a.xMm + a.wMm + 3 <= b.xMm + 1e-6 ||
            b.xMm + b.wMm + 3 <= a.xMm + 1e-6 ||
            a.yMm + a.hMm + 3 <= b.yMm + 1e-6 ||
            b.yMm + b.hMm + 3 <= a.yMm + 1e-6
          expect(apart).toBe(true)
        }
      }
    }
  })

  it('respects allowRotate=false and the global switch (invariant 8)', async () => {
    const { api, clock, ids } = await uploaded(['nhan-4x17.pdf'])
    const req = request(ids)
    req.items[0].allowRotate = false
    const { jobId } = await api.createJob(req)
    clock.t += RUN_MS
    const all = (await api.getJob(jobId)).result!.sheets.flatMap((s) => s.placements)
    expect(all.every((p) => !p.rotated && p.wMm === 40 && p.hMm === 170)).toBe(true)
  })

  it('TRUE_SHAPE demo tilts some pieces and reports the source size', async () => {
    const { api, clock, ids } = await uploaded(['nhan-13x4.pdf'])
    const { jobId } = await api.createJob(request(ids, { mode: 'TRUE_SHAPE' }))
    clock.t += RUN_MS
    const all = (await api.getJob(jobId)).result!.sheets.flatMap((s) => s.placements)
    const tilted = all.filter((p) => p.angleDeg === DEMO_TILT_DEG)
    expect(tilted.length).toBeGreaterThan(0)
    expect(tilted[0]).toMatchObject({ sourceWMm: 130, sourceHMm: 40, rotated: false })
    expect(tilted[0].wMm).toBeGreaterThan(130)
  })

  it('builds TIFs one sheet at a time: QUEUED → BUILDING → READY', async () => {
    const { api, clock, ids } = await uploaded(['nhan-20x18.pdf'])
    const req = request(ids)
    req.items[0].quantity = 30
    const { jobId } = await api.createJob(req)
    clock.t += RUN_MS + 10
    let sheets = (await api.getJob(jobId)).result!.sheets
    expect(sheets.length).toBeGreaterThanOrEqual(3)
    expect(sheets.map((s) => s.tif.status)).toEqual(['BUILDING', ...sheets.slice(1).map(() => 'QUEUED')])
    clock.t += TIF_BUILD_MS
    sheets = (await api.getJob(jobId)).result!.sheets
    expect(sheets[0].tif).toMatchObject({ status: 'READY', error: null })
    expect(sheets[0].tif.sizeBytes).toBeGreaterThan(0)
    expect(sheets[1].tif.status).toBe('BUILDING')

    // Prioritize the last sheet: it is built right after the current one.
    const last = sheets.length - 1
    await api.prioritizeTif(jobId, last)
    clock.t += TIF_BUILD_MS
    sheets = (await api.getJob(jobId)).result!.sheets
    expect(sheets[1].tif.status).toBe('READY')
    expect(sheets[last].tif.status).toBe('BUILDING')
    clock.t += TIF_BUILD_MS * sheets.length
    sheets = (await api.getJob(jobId)).result!.sheets
    expect(sheets.every((s) => s.tif.status === 'READY')).toBe(true)
  })

  it('a design named with the marker fails its first TIF build; retry succeeds', async () => {
    const { api, clock, ids } = await uploaded([`${FAIL_TIF_MARKER}-10x10.pdf`])
    const { jobId } = await api.createJob(request(ids, { maxSheetLengthMm: null }))
    clock.t += RUN_MS + TIF_BUILD_MS
    let tif = (await api.getJob(jobId)).result!.sheets[0].tif
    expect(tif.status).toBe('FAILED')
    expect(tif.error?.code).toBe('TIFF_TOO_LARGE')
    clock.t += 10_000
    await api.prioritizeTif(jobId, 0)
    tif = (await api.getJob(jobId)).result!.sheets[0].tif
    expect(tif.status).toBe('BUILDING')
    clock.t += TIF_BUILD_MS
    tif = (await api.getJob(jobId)).result!.sheets[0].tif
    expect(tif.status).toBe('READY')
  })

  it('sync error: design wider than the sheet (spec 6.3 message)', async () => {
    const { api, ids } = await uploaded(['loi-qua-kho-60x59.pdf'])
    const err = await api.createJob(request(ids)).catch((e: unknown) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect(err).toMatchObject({ code: 'ITEM_WIDER_THAN_SHEET' })
    expect((err as ApiError).message).toBe(
      'Hình "loi-qua-kho-60x59.pdf" 60x59 cm rộng hơn khổ 57 cm (đã tính cả trường hợp xoay).',
    )
    const req = request(ids)
    req.items[0].allowRotate = false
    await expect(api.createJob(req)).rejects.toThrow('và không được phép xoay.')
    await expect(api.createJob(request(['nope']))).rejects.toMatchObject({ code: 'FILE_NOT_FOUND' })
    await expect(api.createJob(request([]))).rejects.toMatchObject({ code: 'INVALID_REQUEST' })
  })

  it('engine error arrives as a FAILED job with the spec message', async () => {
    const { api, clock, ids } = await uploaded(['nhan-4x17.pdf'])
    const req = request(ids, { maxSheetLengthMm: 30 })
    req.items[0].allowRotate = false
    const { jobId } = await api.createJob(req)
    clock.t += RUN_MS
    const job = await api.getJob(jobId)
    expect(job.status).toBe('FAILED')
    expect(job.error?.code).toBe('INVALID_REQUEST')
    expect(job.error?.message).toContain('Chiều dài tối đa mỗi file (3 cm) nhỏ hơn hình cao nhất (17 cm)')
    const margin = await api.createJob(request(ids, { marginMm: 300 }))
    clock.t += RUN_MS
    expect((await api.getJob(margin.jobId)).error?.message).toContain('Lề biên quá lớn')
  })

  it('unknown job → JOB_NOT_FOUND; jobs survive a reload through storage', async () => {
    const { api, clock, ids, deps } = await uploaded(['nhan-5x7.pdf'])
    await expect(api.getJob('x')).rejects.toMatchObject({ code: 'JOB_NOT_FOUND' })
    await expect(api.prioritizeTif('x', 0)).rejects.toMatchObject({ code: 'JOB_NOT_FOUND' })
    const { jobId } = await api.createJob(request(ids))
    clock.t += RUN_MS
    const reloaded = createMockApi(deps)
    expect((await reloaded.getJob(jobId)).status).toBe('DONE')
    expect(reloaded.getPreviewUrl(ids[0])).toContain('data:image/svg+xml')
  })

  it('downloads are blocked in demo mode', async () => {
    const { api } = setup()
    expect(api.isDemo).toBe(true)
    expect(api.sheetDownloadUrl('j', 0, 'tif')).toContain('#demo')
    expect(api.zipDownloadUrl('j', 'pdf')).toContain('#demo')
    await expect(api.downloadSheet('j', 0, 'pdf')).rejects.toBeInstanceOf(ApiError)
  })

  it('works without storage and with a broken storage', async () => {
    const api = createMockApi({
      storage: null,
      delay: () => Promise.resolve(),
      readImage: async () => ({ width: 96, height: 96 }),
      objectUrl: () => 'blob:x',
    })
    const f = await api.uploadFile(new File(['x'], 'a.png'))
    expect(f.widthMm).toBe(25.4)
    const broken = {
      getItem: () => '{not json',
      setItem: () => {
        throw new Error('quota')
      },
    }
    const api2 = createMockApi({ storage: broken, delay: () => Promise.resolve() })
    await expect(api2.uploadFile(pdf('nhan-5x7.pdf'))).resolves.toMatchObject({ widthMm: 50 })
  })
})
