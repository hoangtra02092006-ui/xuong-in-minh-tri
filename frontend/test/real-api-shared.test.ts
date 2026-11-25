import { afterEach, describe, expect, it, vi } from 'vitest'
import axios, { AxiosError, AxiosHeaders } from 'axios'
import { http } from '@/api/http'
import { blobErrorToApiError, createRealApi } from '@/api/real'
import { fallbackFileName, fileNameFromDisposition } from '@/api/service'
import type { Placement } from '@/api/types'
import { pieceShape, rotationText, ticks } from '@/shared/layout-geometry'
import { readJson, removeKey, writeJson } from '@/shared/storage'
import { dismissToast, showToast, TOAST_MS, toast, useToasts } from '@/shared/toast'
import {
  cmInputValue,
  cmmToMm,
  formatAreaCm2,
  formatCmFixed,
  formatSizeCm,
  mmToCmm,
  ptToMm,
  pxToMm,
} from '@/shared/units'

const cfg = { baseUrl: 'https://in.dreamate.me', useMock: false }

afterEach(() => vi.restoreAllMocks())

describe('real API service', () => {
  const api = createRealApi(cfg)

  it('builds URLs for previews and downloads', () => {
    expect(api.isDemo).toBe(false)
    expect(api.getPreviewUrl('a b')).toBe('https://in.dreamate.me/api/v1/files/a%20b/preview')
    expect(api.sheetDownloadUrl('j1', 0, 'tif')).toBe(
      'https://in.dreamate.me/api/v1/nesting/jobs/j1/sheets/0/tif',
    )
    expect(api.zipDownloadUrl('j1', 'cut')).toBe(
      'https://in.dreamate.me/api/v1/nesting/jobs/j1/export.zip?format=cut',
    )
  })

  it('uploads one file per request in the "files" field and reports progress', async () => {
    const uploaded = { id: 'f', originalName: 'a.pdf' }
    const post = vi.spyOn(http, 'post').mockImplementation(async (_url, body, config) => {
      expect((body as FormData).getAll('files')).toHaveLength(1)
      config?.onUploadProgress?.({ loaded: 50, total: 100 } as never)
      return { data: [uploaded] }
    })
    const progress: number[] = []
    await expect(api.uploadFile(new File(['x'], 'a.pdf'), (p) => progress.push(p))).resolves.toBe(uploaded)
    expect(post.mock.calls[0][0]).toBe('/files')
    expect(progress).toEqual([50])
    post.mockResolvedValueOnce({ data: [] })
    await expect(api.uploadFile(new File(['x'], 'a.pdf'))).rejects.toMatchObject({ code: 'INTERNAL_ERROR' })
  })

  it('creates, reads and prioritizes jobs on the contract paths', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { jobId: 'j' } })
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: { jobId: 'j', status: 'DONE' } })
    await api.createJob({} as never)
    expect(post).toHaveBeenCalledWith('/nesting/jobs', {})
    await api.getJob('j')
    expect(get).toHaveBeenCalledWith('/nesting/jobs/j')
    await api.prioritizeTif('j', 2)
    expect(post).toHaveBeenLastCalledWith('/nesting/jobs/j/sheets/2/tif/prioritize')
  })

  it('downloads a sheet as a blob with the server file name', async () => {
    const createUrl = vi.fn(() => 'blob:1')
    Object.assign(URL, { createObjectURL: createUrl, revokeObjectURL: vi.fn() })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    vi.spyOn(axios, 'get').mockResolvedValue({
      data: new Blob(['%PDF']),
      headers: { 'content-disposition': 'attachment; filename="minh-tri-j-tam01-57x100cm.pdf"' },
    })
    await api.downloadSheet('j', 0, 'pdf')
    expect(createUrl).toHaveBeenCalled()
    expect(click).toHaveBeenCalled()
  })

  it('reads a JSON error body out of a blob response', async () => {
    const config = { headers: new AxiosHeaders() }
    const body = new Blob([JSON.stringify({ error: { code: 'TIFF_TOO_LARGE', message: 'Giảm DPI' } })])
    const err = new AxiosError('x', 'ERR', config, null, {
      status: 422,
      data: body,
      statusText: '',
      headers: {},
      config,
    })
    vi.spyOn(axios, 'get').mockRejectedValue(err)
    await expect(api.downloadSheet('j', 0, 'tif')).rejects.toMatchObject({ code: 'TIFF_TOO_LARGE' })
    const bad = new AxiosError('x', 'ERR', config, null, {
      status: 500,
      data: new Blob(['<html>']),
      statusText: '',
      headers: {},
      config,
    })
    expect((await blobErrorToApiError(bad)).code).toBe('INTERNAL_ERROR')
    expect((await blobErrorToApiError(new Error('x'))).code).toBe('INTERNAL_ERROR')
  })

  it('file names', () => {
    expect(fileNameFromDisposition('attachment; filename="a.tif"')).toBe('a.tif')
    expect(fileNameFromDisposition("attachment; filename*=UTF-8''t%E1%BA%A5m.pdf")).toBe('tấm.pdf')
    expect(fileNameFromDisposition(undefined)).toBeNull()
    expect(fileNameFromDisposition('inline')).toBeNull()
    expect(fallbackFileName('abcdefghij', 0, 'cut')).toBe('minh-tri-abcdefgh-tam01-cat.pdf')
    expect(fallbackFileName('abcdefghij', 9, 'tif')).toBe('minh-tri-abcdefgh-tam10.tif')
    expect(fallbackFileName('abcdefghij', 1, 'pdf')).toBe('minh-tri-abcdefgh-tam02.pdf')
  })
})

describe('toast', () => {
  it('auto hides after 4 seconds and can be dismissed', () => {
    vi.useFakeTimers()
    const state = useToasts()
    state.items.splice(0)
    toast.success('ok')
    toast.info('i')
    const id = toast.error('e')
    expect(state.items.map((t) => t.kind)).toEqual(['success', 'info', 'error'])
    dismissToast(id)
    expect(state.items).toHaveLength(2)
    dismissToast(999)
    vi.advanceTimersByTime(TOAST_MS)
    expect(state.items).toHaveLength(0)
    showToast('info', 'x')
    vi.useRealTimers()
  })
})

describe('safe storage', () => {
  it('reads, writes and never throws', () => {
    writeJson(() => localStorage, 'k', { a: 1 })
    expect(readJson(() => localStorage, 'k')).toEqual({ a: 1 })
    removeKey(() => localStorage, 'k')
    expect(readJson(() => localStorage, 'k')).toBeNull()
    const broken = () => {
      throw new Error('blocked')
    }
    expect(readJson(broken as never, 'k')).toBeNull()
    expect(() => writeJson(broken as never, 'k', 1)).not.toThrow()
    expect(() => removeKey(broken as never, 'k')).not.toThrow()
  })
})

describe('layout preview geometry', () => {
  const p: Placement = {
    fileId: 'f',
    label: 'a.pdf',
    categoryIndex: 0,
    xMm: 5,
    yMm: 5,
    wMm: 200,
    hMm: 180,
    rotated: false,
    angleDeg: 0,
    sourceWMm: 200,
    sourceHMm: 180,
  }

  it('flips the y axis: ySvg = length − y − h', () => {
    const s = pieceShape(p, 1000, '20x18')
    expect(s).toMatchObject({ x: 5, y: 815, w: 200, h: 180, transform: '', label: '20x18' })
  })

  it('tilted piece draws the source rectangle rotated around the box centre', () => {
    const s = pieceShape({ ...p, wMm: 240, hMm: 220, angleDeg: 30 }, 1000, 'x')
    expect(s.w).toBe(200)
    expect(s.h).toBe(180)
    expect(s.cx).toBe(125)
    expect(s.transform).toBe(`rotate(-30 125 ${1000 - 5 - 110})`)
  })

  it('small pieces get no label', () => {
    expect(pieceShape({ ...p, wMm: 40, hMm: 20 }, 1000, 'x').label).toBeNull()
  })

  it('ruler ticks every 10 cm; the vertical one counts from the bottom', () => {
    expect(ticks(250).map((t) => t.label)).toEqual(['0', '10', '20'])
    expect(ticks(250, true)[0]).toEqual({ pos: 250, label: '0' })
  })

  it('rotation text', () => {
    expect(rotationText(p)).toBe('Giữ nguyên hướng')
    expect(rotationText({ ...p, rotated: true })).toBe('Đã xoay 90 độ')
    expect(rotationText({ ...p, angleDeg: 22.5 })).toBe('Xoay 22,5 độ')
  })
})

describe('more unit helpers', () => {
  it('converts points, pixels and engine units', () => {
    expect(ptToMm(72)).toBe(25.4)
    expect(pxToMm(600, 300)).toBe(50.8)
    expect(pxToMm(96)).toBe(25.4)
    expect(mmToCmm(12.345)).toBe(1235)
    expect(cmmToMm(1235)).toBe(12.35)
  })
  it('formats sizes, areas and input values the Vietnamese way', () => {
    expect(formatSizeCm(130, 40)).toBe('13 x 4 cm')
    expect(formatAreaCm2(2520000)).toBe('25.200')
    expect(formatCmFixed(3415.7, 1)).toBe('341,6')
    expect(cmInputValue(12005)).toBe('1200,5')
  })
})
