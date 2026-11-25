// Demo mode (spec 5.8): replaces the whole API layer with in-browser code, same schema.
import { ApiError } from '../errors'
import type { ApiService } from '../service'
import type { UploadedFile } from '../types'
import { browserImageSize, readMockFile, type ImageSizeReader } from './files'
import { layoutShelves, validateRequest } from './layout'
import { jobView, newJobState, prioritize, type MockJobState } from './jobs'
import { formatCm } from '@/shared/units'

const JOBS_KEY = 'xuong-in.demo.jobs'
const FILES_KEY = 'xuong-in.demo.files'
/** Keep storage small: only the latest demo jobs survive a reload. */
const MAX_STORED_JOBS = 5

export interface MockDeps {
  now?: () => number
  delay?: (ms: number) => Promise<void>
  storage?: Pick<Storage, 'getItem' | 'setItem'> | null
  readImage?: ImageSizeReader
  makeId?: () => string
  objectUrl?: (file: File) => string
}

function safeStorage(): Pick<Storage, 'getItem' | 'setItem'> | null {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage
  } catch {
    return null
  }
}

function randomId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
  return `demo-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

/** PDF previews in demo mode: an outline with the size, right aspect ratio (no PDF renderer). */
export function pdfPlaceholder(widthMm: number, heightMm: number): string {
  const scale = 360 / Math.max(widthMm, heightMm)
  const w = Math.max(24, Math.round(widthMm * scale))
  const h = Math.max(24, Math.round(heightMm * scale))
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">` +
    `<rect x="1" y="1" width="${w - 2}" height="${h - 2}" fill="#eef4f3" stroke="#0f766e" stroke-width="2" stroke-dasharray="6 4"/>` +
    `<text x="50%" y="50%" fill="#0f766e" font-family="sans-serif" font-size="${Math.min(40, h / 3)}" ` +
    `text-anchor="middle" dominant-baseline="middle">PDF ${formatCm(widthMm)}x${formatCm(heightMm)}</text></svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

export function createMockApi(deps: MockDeps = {}): ApiService {
  const now = deps.now ?? (() => Date.now())
  const delay = deps.delay ?? ((ms: number) => new Promise<void>((r) => setTimeout(r, ms)))
  const storage = deps.storage === undefined ? safeStorage() : deps.storage
  const readImage = deps.readImage ?? browserImageSize
  const makeId = deps.makeId ?? randomId
  const objectUrl = deps.objectUrl ?? ((f: File) => URL.createObjectURL(f))

  function load<T>(key: string, fallback: T): T {
    try {
      const raw = storage?.getItem(key)
      return raw ? (JSON.parse(raw) as T) : fallback
    } catch {
      return fallback
    }
  }
  function save(key: string, value: unknown): void {
    try {
      storage?.setItem(key, JSON.stringify(value))
    } catch {
      // Storage full or blocked: demo keeps working in memory only.
    }
  }

  const files = new Map<string, UploadedFile>(
    Object.entries(load<Record<string, UploadedFile>>(FILES_KEY, {})),
  )
  const jobs = new Map<string, MockJobState>(Object.entries(load<Record<string, MockJobState>>(JOBS_KEY, {})))

  const persistFiles = () => save(FILES_KEY, Object.fromEntries(files))
  const persistJobs = () => {
    const latest = [...jobs.values()].sort((a, b) => b.createdAt - a.createdAt).slice(0, MAX_STORED_JOBS)
    save(JOBS_KEY, Object.fromEntries(latest.map((j) => [j.jobId, j])))
  }

  function findJob(jobId: string): MockJobState {
    const job = jobs.get(jobId)
    if (!job) {
      throw new ApiError({
        code: 'JOB_NOT_FOUND',
        message: `Không tìm thấy lần ghép ${jobId}. Có thể máy chủ đã khởi động lại.`,
      })
    }
    return job
  }

  const demoDownload = () =>
    Promise.reject(
      new ApiError({
        code: 'INVALID_REQUEST',
        message: 'Chế độ demo không xuất được file thật.',
      }),
    )

  return {
    isDemo: true,

    async uploadFile(file, onProgress) {
      for (const p of [20, 55, 85]) {
        onProgress?.(p)
        await delay(120)
      }
      const id = makeId()
      const isPdf = /\.pdf$/i.test(file.name)
      const uploaded = await readMockFile(file, id, '', readImage)
      uploaded.previewUrl = isPdf ? pdfPlaceholder(uploaded.widthMm, uploaded.heightMm) : objectUrl(file)
      files.set(id, uploaded)
      persistFiles()
      onProgress?.(100)
      return { ...uploaded }
    },

    getPreviewUrl(fileId) {
      return files.get(fileId)?.previewUrl ?? ''
    },

    async createJob(request) {
      await delay(150)
      validateRequest(request, files)
      const jobId = makeId()
      let state: MockJobState
      try {
        state = newJobState(jobId, now(), layoutShelves(request, files), null)
      } catch (err) {
        const body = err instanceof ApiError ? { code: err.code, message: err.message } : null
        state = newJobState(jobId, now(), null, body ?? { code: 'NESTING_FAILED', message: String(err) })
      }
      jobs.set(jobId, state)
      persistJobs()
      return jobView(state, now())
    },

    async getJob(jobId) {
      const view = jobView(findJob(jobId), now())
      persistJobs()
      return view
    },

    async prioritizeTif(jobId, sheetIndex) {
      prioritize(findJob(jobId), sheetIndex, now())
      persistJobs()
    },

    sheetDownloadUrl(jobId, sheetIndex, format) {
      return `#demo-${jobId}-${sheetIndex}-${format}`
    },

    zipDownloadUrl(jobId, format) {
      return `#demo-${jobId}-${format}`
    },

    downloadSheet: demoDownload,
  }
}
