// Demo-mode job simulator: a job runs for a few seconds, then TIFs are "built" one sheet at a
// time (design 4a). State is plain JSON so it survives a page reload via storage.
import type { ApiErrorBody, NestJob, Sheet, SheetTif } from '../types'
import type { MockLayout } from './layout'

export const RUN_MS = 3000
export const TIF_BUILD_MS = 2500
/** A design whose name contains this makes its sheets' first TIF build fail (reachable FAILED case). */
export const FAIL_TIF_MARKER = 'loi-tif'

export interface MockJobState {
  jobId: string
  createdAt: number
  runMs: number
  layout: MockLayout | null
  error: ApiErrorBody | null
  /** Sheets waiting to be built, in build order. */
  queue: number[]
  current: { index: number; startedAt: number } | null
  /** Earliest time the next build may start. */
  clock: number
  ready: Record<number, number>
  failed: Record<number, ApiErrorBody>
  failOnce: number[]
  retried: number[]
}

export function newJobState(
  jobId: string,
  now: number,
  layout: MockLayout | null,
  error: ApiErrorBody | null,
): MockJobState {
  const sheets = layout?.sheets ?? []
  const failOnce = sheets
    .filter((s) => s.placements.some((p) => p.label.toLowerCase().includes(FAIL_TIF_MARKER)))
    .map((s) => s.index)
  return {
    jobId,
    createdAt: now,
    runMs: RUN_MS,
    layout,
    error,
    queue: sheets.map((s) => s.index),
    current: null,
    clock: now + RUN_MS,
    ready: {},
    failed: {},
    failOnce,
    retried: [],
  }
}

/** Fake TIF size, about 6 MB for a 57 x 100 cm sheet like the old app measured. */
export function tifSizeBytes(widthMm: number, lengthMm: number, fillRate: number): number {
  return Math.round(widthMm * lengthMm * 10.5 * (0.6 + 0.4 * fillRate))
}

function finish(s: MockJobState, index: number): void {
  if (s.failOnce.includes(index) && !s.retried.includes(index)) {
    s.failed[index] = {
      code: 'TIFF_TOO_LARGE',
      message: 'Tấm này quá lớn để dựng TIF với RAM hiện có. Hãy thử lại, hoặc giảm DPI xuống 200.',
      details: { suggestedDpi: 200 },
    }
    return
  }
  const sheet = s.layout?.sheets[index]
  s.ready[index] = sheet ? tifSizeBytes(sheet.widthMm, sheet.lengthMm, sheet.fillRate) : 0
}

/** Moves the simulation forward to `now`. Mutates and returns the state. */
export function advance(s: MockJobState, now: number): MockJobState {
  if (!s.layout || now < s.createdAt + s.runMs) return s
  for (;;) {
    if (!s.current) {
      const next = s.queue.shift()
      if (next === undefined) break
      s.current = { index: next, startedAt: s.clock }
    }
    const end = s.current.startedAt + TIF_BUILD_MS
    if (now < end) break
    finish(s, s.current.index)
    s.clock = end
    s.current = null
  }
  return s
}

/** Prioritize endpoint: queued sheet goes first; a failed sheet is retried first. */
export function prioritize(s: MockJobState, index: number, now: number): MockJobState {
  advance(s, now)
  if (s.failed[index]) {
    delete s.failed[index]
    s.retried.push(index)
    s.queue.unshift(index)
    if (!s.current) s.clock = Math.max(s.clock, now)
    return s
  }
  const pos = s.queue.indexOf(index)
  if (pos > 0) {
    s.queue.splice(pos, 1)
    s.queue.unshift(index)
  }
  return s
}

function tifOf(s: MockJobState, index: number): SheetTif {
  if (index in s.ready) return { status: 'READY', sizeBytes: s.ready[index], error: null }
  if (s.failed[index]) return { status: 'FAILED', sizeBytes: null, error: s.failed[index] }
  if (s.current?.index === index) return { status: 'BUILDING', sizeBytes: null, error: null }
  return { status: 'QUEUED', sizeBytes: null, error: null }
}

/** The job as GET /nesting/jobs/{id} would return it at time `now`. */
export function jobView(s: MockJobState, now: number): NestJob {
  advance(s, now)
  const createdAt = new Date(s.createdAt).toISOString()
  const base = { jobId: s.jobId, createdAt }
  const elapsed = now - s.createdAt
  if (elapsed < s.runMs) {
    if (elapsed < 300) return { ...base, status: 'PENDING', progress: 0 }
    return { ...base, status: 'RUNNING', progress: Math.min(95, Math.round((elapsed / s.runMs) * 100)) }
  }
  const finishedAt = new Date(s.createdAt + s.runMs).toISOString()
  if (!s.layout) {
    return { ...base, status: 'FAILED', progress: 100, finishedAt, error: s.error ?? undefined }
  }
  const sheets: Sheet[] = s.layout.sheets.map((sh) => ({ ...sh, tif: tifOf(s, sh.index) }))
  return { ...base, status: 'DONE', progress: 100, finishedAt, result: { sheets, stats: s.layout.stats } }
}
