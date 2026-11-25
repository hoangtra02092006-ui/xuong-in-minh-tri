import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createMockApi } from '@/api/mock'
import type { ApiService } from '@/api/service'
import type { NestJob, Sheet, TifStatus } from '@/api/types'
import ResultView from '@/features/nesting/ResultView.vue'
import { buildRoutes } from '@/router'
import { setApi } from '@/stores/api-provider'
import { useToasts } from '@/shared/toast'

function sheet(index: number, status: TifStatus, sizeBytes: number | null = null): Sheet {
  return {
    index,
    widthMm: 570,
    lengthMm: 1000,
    fillRate: 0.8,
    placements: [
      {
        fileId: 'f1',
        label: 'nhan-20x18.pdf',
        categoryIndex: 0,
        xMm: 5,
        yMm: 5,
        wMm: 200,
        hMm: 180,
        rotated: false,
        angleDeg: 0,
        sourceWMm: 200,
        sourceHMm: 180,
      },
      {
        fileId: 'f1',
        label: 'nhan-20x18.pdf',
        categoryIndex: 0,
        xMm: 300,
        yMm: 5,
        wMm: 220,
        hMm: 210,
        rotated: false,
        angleDeg: 15,
        sourceWMm: 200,
        sourceHMm: 180,
      },
    ],
    tif: {
      status,
      sizeBytes,
      error:
        status === 'FAILED' ? { code: 'TIFF_TOO_LARGE', message: 'Tấm quá lớn, giảm DPI xuống 200.' } : null,
    },
  }
}

function doneJob(statuses: TifStatus[]): NestJob {
  return {
    jobId: 'job-1',
    status: 'DONE',
    progress: 100,
    createdAt: '2026-11-25T00:00:00Z',
    result: {
      sheets: statuses.map((s, i) => sheet(i, s, s === 'READY' ? 5.2 * 1024 * 1024 : null)),
      stats: {
        totalSheets: statuses.length,
        totalLengthMm: 1000 * statuses.length,
        totalShapeAreaMm2: 72000,
        usedAreaMm2: 570000,
        fillRate: 0.8,
        savedVsIndividualPct: 61.4,
        totalPieces: 2 * statuses.length,
        byType: [
          {
            fileId: 'f1',
            label: 'nhan-20x18.pdf',
            categoryIndex: 0,
            pieces: 2,
            widthMm: 200,
            heightMm: 180,
            shapeAreaMm2: 72000,
            lengthMm: 1000 * statuses.length,
          },
        ],
      },
    },
  }
}

function fakeApi(job: NestJob): ApiService {
  const base = createMockApi({ storage: null, delay: () => Promise.resolve() })
  return {
    ...base,
    getJob: vi.fn(async () => job),
    prioritizeTif: vi.fn(async () => undefined),
  }
}

async function mountResult(api: ApiService, jobId = 'job-1') {
  setApi(api)
  const router = createRouter({ history: createMemoryHistory(), routes: buildRoutes(() => true) })
  await router.push(`/nest/result/${jobId}`)
  const wrapper = mount(ResultView, { props: { jobId }, global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

beforeEach(() => {
  setActivePinia(createPinia())
  useToasts().items.splice(0)
  vi.useFakeTimers()
})
afterEach(() => vi.useRealTimers())

describe('result screen', () => {
  it('FAILED job shows "Không ghép được" with the message and the next action', async () => {
    const failed: NestJob = {
      jobId: 'job-1',
      status: 'FAILED',
      progress: 100,
      createdAt: '',
      error: {
        code: 'INVALID_REQUEST',
        message: 'Chiều dài tối đa mỗi file (3 cm) nhỏ hơn hình cao nhất (17 cm).',
      },
    }
    const { wrapper, router } = await mountResult(fakeApi(failed))
    expect(wrapper.text()).toContain('Không ghép được')
    expect(wrapper.text()).toContain('nhỏ hơn hình cao nhất')
    const action = wrapper.findAll('a').find((a) => a.text() === 'Sửa tham số và thử lại')
    expect(action?.attributes('href')).toBe(router.resolve({ name: 'settings' }).href)
  })

  it('running job shows the spinner text, never a blank screen', async () => {
    const { wrapper } = await mountResult(
      fakeApi({ jobId: 'job-1', status: 'RUNNING', progress: 40, createdAt: '' }),
    )
    expect(wrapper.text()).toContain('Đang tính toán bố trí…')
  })

  it('missing job shows "Không tìm thấy lần ghép này"', async () => {
    const api = createMockApi({ storage: null })
    const { wrapper } = await mountResult(api, 'mat-roi')
    expect(wrapper.text()).toContain('Không tìm thấy lần ghép này')
    expect(wrapper.text()).toContain('Có thể máy chủ đã khởi động lại')
  })

  it('renders every TIF status and the overall progress line', async () => {
    const { wrapper } = await mountResult(fakeApi(doneJob(['READY', 'BUILDING', 'QUEUED', 'FAILED'])))
    const text = wrapper.text()
    expect(text).toContain('Đang chuẩn bị file TIF: 1/4 tấm sẵn sàng')
    expect(text).toContain('TIF 5,2 MB')
    expect(text).toContain('Đang dựng…')
    expect(text).toContain('Đang chờ')
    expect(text).toContain('Lỗi · Thử lại')
    const failedBtn = wrapper.findAll('button').find((b) => b.text().includes('Lỗi · Thử lại'))!
    expect(failedBtn.attributes('title')).toBe('Tấm quá lớn, giảm DPI xuống 200.')
    expect(text).toContain('Tải tất cả TIF (1/4)')
    // Stats and per-design table
    expect(text).toContain('61,4')
    expect(text).toContain('Từng mẫu ăn mấy mét')
    expect(text).toContain('Tấm 4')
    // Preview draws the free rotation angle on the tilted piece.
    expect(wrapper.find('rect[transform^="rotate(-15"]').exists()).toBe(true)
  })

  it('queued and failed TIF buttons are not locked: they prioritize the sheet', async () => {
    const api = fakeApi(doneJob(['READY', 'BUILDING', 'QUEUED', 'FAILED']))
    const { wrapper } = await mountResult(api)
    const queued = wrapper.findAll('button').find((b) => b.text().includes('Đang chờ'))!
    expect(queued.attributes('disabled')).toBeUndefined()
    await queued.trigger('click')
    await flushPromises()
    expect(api.prioritizeTif).toHaveBeenCalledWith('job-1', 2)
    expect(wrapper.text()).toContain('tự tải khi xong')
    const failed = wrapper.findAll('button').find((b) => b.text().includes('Lỗi · Thử lại'))!
    await failed.trigger('click')
    await flushPromises()
    expect(api.prioritizeTif).toHaveBeenCalledWith('job-1', 3)
  })

  it('all TIFs ready: shows the done line; demo blocks downloads with the spec toast', async () => {
    const { wrapper } = await mountResult(fakeApi(doneJob(['READY', 'READY'])))
    expect(wrapper.text()).not.toContain('Đang chuẩn bị file TIF')
    const pdfBtn = wrapper.findAll('button').find((b) => b.text() === 'PDF')!
    await pdfBtn.trigger('click')
    await flushPromises()
    expect(useToasts().items.at(-1)?.text).toContain('Chế độ demo không xuất được PDF thật')
    const zip = wrapper.findAll('a').find((a) => a.text().includes('Tải tất cả TIF'))!
    await zip.trigger('click')
    expect(useToasts().items.length).toBeGreaterThanOrEqual(2)
  })
})
