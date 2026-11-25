import type { UploadedFile } from '@/api/types'

/** An uploaded file as the API returns it (sizes in mm). */
export function file(id: string, w: number, h: number, type: 'PDF' | 'IMAGE' = 'PDF'): UploadedFile {
  return {
    id,
    originalName: `${id}.${type === 'PDF' ? 'pdf' : 'png'}`,
    widthMm: w,
    heightMm: h,
    sourceWidthMm: w,
    sourceHeightMm: h,
    trimmed: false,
    type,
    pageCount: 1,
    previewUrl: `/api/v1/files/${id}/preview`,
  }
}
