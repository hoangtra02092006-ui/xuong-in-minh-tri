import type { ExportFormat, NestJob, NestRequest, UploadedFile } from './types'

/** Upload progress callback, 0..100. */
export type ProgressFn = (percent: number) => void

/**
 * Everything the screens need from the backend. Two implementations: the real one over
 * http.ts and the in-browser mock used in demo mode (spec 5.8). Both return the real schema.
 */
export interface ApiService {
  readonly isDemo: boolean
  /** One file per request (spec 5.4). */
  uploadFile(file: File, onProgress?: ProgressFn): Promise<UploadedFile>
  /** Preview image URL for an uploaded file id (result screen looks it up by fileId). */
  getPreviewUrl(fileId: string): string
  createJob(request: NestRequest): Promise<NestJob>
  getJob(jobId: string): Promise<NestJob>
  /** Moves a sheet to the front of the TIF build queue; also retries a FAILED sheet (design 4a). */
  prioritizeTif(jobId: string, sheetIndex: number): Promise<void>
  sheetDownloadUrl(jobId: string, sheetIndex: number, format: ExportFormat): string
  zipDownloadUrl(jobId: string, format: ExportFormat): string
  /** Fetches a built file (PDF / CẮT) so the UI can show a spinner and readable errors. */
  downloadSheet(jobId: string, sheetIndex: number, format: ExportFormat): Promise<void>
}

/** Fallback download name when the server sends no Content-Disposition (spec 6.5 scheme). */
export function fallbackFileName(jobId: string, sheetIndex: number, format: ExportFormat): string {
  const base = `minh-tri-${jobId.slice(0, 8)}-tam${String(sheetIndex + 1).padStart(2, '0')}`
  if (format === 'tif') return `${base}.tif`
  if (format === 'cut') return `${base}-cat.pdf`
  return `${base}.pdf`
}

/** Reads the file name from a Content-Disposition header. */
export function fileNameFromDisposition(header: string | undefined | null): string | null {
  if (!header) return null
  const star = /filename\*=UTF-8''([^;]+)/i.exec(header)
  if (star) return decodeURIComponent(star[1].trim())
  const plain = /filename="?([^";]+)"?/i.exec(header)
  return plain ? plain[1].trim() : null
}
