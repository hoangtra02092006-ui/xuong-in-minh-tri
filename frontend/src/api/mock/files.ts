// Demo-mode file reading (spec 5.8): sizes are read in the browser, no trimming.
import { ApiError } from '../errors'
import type { UploadedFile } from '../types'
import { readBlobText } from '@/shared/blob'
import { cmToMm, parseDecimal, ptToMm, pxToMm } from '@/shared/units'

export const MAX_FILE_BYTES = 50 * 1024 * 1024
/** Used when a PDF has neither a size in its name nor a readable MediaBox. */
export const DEFAULT_PDF_SIZE_MM = 100
const ACCEPTED = /\.(pdf|png|jpe?g)$/i

export interface PixelSize {
  width: number
  height: number
}

/** Reads natural pixel size of an image file; injectable because jsdom cannot decode images. */
export type ImageSizeReader = (file: File) => Promise<PixelSize>

export const browserImageSize: ImageSizeReader = (file) =>
  new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(url)
      resolve({ width: img.naturalWidth, height: img.naturalHeight })
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('decode'))
    }
    img.src = url
  })

/** "nhan-13x4.pdf" → 130 x 40 mm. Sizes in the name are centimetres (shop naming habit). */
export function sizeFromName(name: string): { widthMm: number; heightMm: number } | null {
  const m = /(\d+(?:[.,]\d+)?)\s*x\s*(\d+(?:[.,]\d+)?)/i.exec(name.replace(/\.[^.]+$/, ''))
  if (!m) return null
  const w = parseDecimal(m[1])
  const h = parseDecimal(m[2])
  if (!w || !h || w <= 0 || h <= 0) return null
  return { widthMm: cmToMm(w), heightMm: cmToMm(h) }
}

/** Looks for "/MediaBox [x0 y0 x1 y1]" in the first 64 KB (spec 5.8). */
export function sizeFromMediaBox(head: string): { widthMm: number; heightMm: number } | null {
  const m = /\/MediaBox\s*\[\s*([\d.-]+)\s+([\d.-]+)\s+([\d.-]+)\s+([\d.-]+)\s*\]/.exec(head)
  if (!m) return null
  const w = Math.abs(Number(m[3]) - Number(m[1]))
  const h = Math.abs(Number(m[4]) - Number(m[2]))
  if (!(w > 0) || !(h > 0)) return null
  return { widthMm: ptToMm(w), heightMm: ptToMm(h) }
}

export async function readMockFile(
  file: File,
  id: string,
  previewUrl: string,
  readImage: ImageSizeReader,
): Promise<UploadedFile> {
  if (!ACCEPTED.test(file.name)) {
    throw new ApiError({
      code: 'UNSUPPORTED_FORMAT',
      message: 'Chỉ nhận file PDF, PNG hoặc JPG.',
    })
  }
  if (file.size > MAX_FILE_BYTES) {
    throw new ApiError({
      code: 'FILE_TOO_LARGE',
      message: 'File lớn hơn 50 MB, hãy giảm dung lượng rồi thử lại.',
    })
  }
  const isPdf = /\.pdf$/i.test(file.name)
  let size: { widthMm: number; heightMm: number }
  if (isPdf) {
    size = sizeFromName(file.name) ??
      sizeFromMediaBox(await readBlobText(file.slice(0, 64 * 1024))) ?? {
        widthMm: DEFAULT_PDF_SIZE_MM,
        heightMm: DEFAULT_PDF_SIZE_MM,
      }
  } else {
    try {
      const px = await readImage(file)
      size = { widthMm: pxToMm(px.width), heightMm: pxToMm(px.height) }
    } catch {
      throw new ApiError({
        code: 'SIZE_UNREADABLE',
        message: 'Không đọc được kích thước ảnh. Hãy nhập tay kích thước trong bảng.',
      })
    }
  }
  return {
    id,
    originalName: file.name,
    widthMm: size.widthMm,
    heightMm: size.heightMm,
    sourceWidthMm: size.widthMm,
    sourceHeightMm: size.heightMm,
    trimmed: false,
    type: isPdf ? 'PDF' : 'IMAGE',
    pageCount: 1,
    previewUrl,
  }
}
