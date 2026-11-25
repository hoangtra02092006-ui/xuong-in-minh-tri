// Pure order logic shared by the order store and the step 1-2 screens (spec 5.4, 5.5).
import type { NestRequest, NestingMode, UploadedFile } from '@/api/types'
import { cmToMm, parseDecimal } from './units'

export interface OrderItem {
  file: UploadedFile
  /** Size used for nesting, after user edits (mm). */
  widthMm: number
  heightMm: number
  quantity: number
  allowRotate: boolean
}

/** Raw text of the settings form, kept as typed so a reload restores it exactly. */
export interface OrderParams {
  sheetWidthCm: string
  marginCm: string
  maxLengthCm: string
  gapCm: string
  mode: NestingMode
  allowRotateGlobal: boolean
  drawCutLines: boolean
}

export const DEFAULT_PARAMS: Readonly<OrderParams> = Object.freeze({
  sheetWidthCm: '57',
  marginCm: '0,5',
  maxLengthCm: '100',
  gapCm: '0,3',
  mode: 'ORTHOGONAL',
  allowRotateGlobal: true,
  drawCutLines: false,
})

/** Sheet widths above this are almost certainly a unit mistake (spec 5.5). */
export const MAX_SANE_SHEET_WIDTH_CM = 1000

export function newItem(file: UploadedFile): OrderItem {
  return { file, widthMm: file.widthMm, heightMm: file.heightMm, quantity: 1, allowRotate: true }
}

/** Quantity input: integer, empty or < 1 becomes 1. */
export function normalizeQuantity(raw: string | number): number {
  const value = typeof raw === 'number' ? raw : parseDecimal(raw)
  if (value === null || !Number.isFinite(value) || value < 1) return 1
  return Math.floor(value)
}

export type ParamField = 'sheetWidthCm' | 'marginCm' | 'maxLengthCm' | 'gapCm'
export type ParamErrors = Partial<Record<ParamField, string>>

export interface ParsedParams {
  sheetWidthMm: number
  marginMm: number
  gapMm: number
  maxSheetLengthMm: number | null
}

/** Validates the settings form; messages are the exact spec wording (spec 5.5). */
export function validateParams(p: OrderParams): { errors: ParamErrors; parsed: ParsedParams | null } {
  const errors: ParamErrors = {}
  const width = parseDecimal(p.sheetWidthCm)
  const margin = parseDecimal(p.marginCm)
  const gap = parseDecimal(p.gapCm)
  const maxLen = parseDecimal(p.maxLengthCm)

  if (width === null || width <= 0) errors.sheetWidthCm = 'Khổ ngang phải lớn hơn 0.'
  else if (width > MAX_SANE_SHEET_WIDTH_CM)
    errors.sheetWidthCm = 'Khổ ngang lớn bất thường, hãy kiểm tra lại đơn vị.'

  if (margin === null) errors.marginCm = 'Hãy nhập lề biên, ví dụ 0,5.'
  else if (margin < 0) errors.marginCm = 'Lề biên không được âm.'
  else if (width !== null && width > 0 && 2 * margin >= width)
    errors.marginCm = 'Lề biên quá lớn so với khổ ngang.'

  if (gap === null) errors.gapCm = 'Hãy nhập khoảng cách, ví dụ 0,3.'
  else if (gap < 0) errors.gapCm = 'Khoảng cách không được âm.'

  if (p.maxLengthCm.trim() !== '' && maxLen === null)
    errors.maxLengthCm = 'Nhập một số, hoặc để trống nếu không giới hạn.'

  if (Object.keys(errors).length > 0) return { errors, parsed: null }
  return {
    errors,
    parsed: {
      sheetWidthMm: cmToMm(width as number),
      marginMm: cmToMm(margin as number),
      gapMm: cmToMm(gap as number),
      maxSheetLengthMm: maxLen !== null && maxLen > 0 ? cmToMm(maxLen) : null,
    },
  }
}

export function hasInvalidSize(items: OrderItem[]): boolean {
  return items.some((i) => !(i.widthMm > 0) || !(i.heightMm > 0))
}

export interface OrderTotals {
  types: number
  pieces: number
  areaMm2: number
}

export function orderTotals(items: OrderItem[]): OrderTotals {
  return items.reduce(
    (t, i) => ({
      types: t.types + 1,
      pieces: t.pieces + i.quantity,
      areaMm2: t.areaMm2 + i.widthMm * i.heightMm * i.quantity,
    }),
    { types: 0, pieces: 0, areaMm2: 0 },
  )
}

/**
 * Theoretical minimum roll length (spec 17, design 4): total area / usable width.
 * Real nesting is always longer because of gaps and leftover space.
 */
export function estimateMinLengthMm(items: OrderItem[], parsed: ParsedParams | null): number | null {
  if (!parsed || items.length === 0) return null
  const usable = parsed.sheetWidthMm - 2 * parsed.marginMm
  if (usable <= 0) return null
  return orderTotals(items).areaMm2 / usable
}

/** Items whose narrowest allowed side is wider than the usable width (would get ITEM_WIDER_THAN_SHEET). */
export function itemsWiderThanSheet(
  items: OrderItem[],
  parsed: ParsedParams | null,
  allowRotateGlobal: boolean,
): OrderItem[] {
  if (!parsed) return []
  const usable = parsed.sheetWidthMm - 2 * parsed.marginMm
  return items.filter((i) => {
    const rotate = allowRotateGlobal && i.allowRotate
    const needed = rotate ? Math.min(i.widthMm, i.heightMm) : i.widthMm
    return needed > usable
  })
}

/**
 * Items that FREE / TRUE_SHAPE will treat as solid blocks (spec 17): images (no vector outline)
 * and resized designs. Returns an empty list in ORTHOGONAL mode.
 */
export function nonNestableItems(items: OrderItem[], mode: NestingMode): OrderItem[] {
  if (mode === 'ORTHOGONAL') return []
  return items.filter(
    (i) => i.file.type === 'IMAGE' || i.widthMm !== i.file.widthMm || i.heightMm !== i.file.heightMm,
  )
}

export function buildNestRequest(items: OrderItem[], p: OrderParams, parsed: ParsedParams): NestRequest {
  return {
    mode: p.mode,
    sheetWidthMm: parsed.sheetWidthMm,
    marginMm: parsed.marginMm,
    gapMm: parsed.gapMm,
    maxSheetLengthMm: parsed.maxSheetLengthMm,
    allowRotateGlobal: p.allowRotateGlobal,
    drawCutLines: p.drawCutLines,
    items: items.map((i) => ({
      fileId: i.file.id,
      quantity: i.quantity,
      allowRotate: i.allowRotate,
      widthMm: i.widthMm,
      heightMm: i.heightMm,
    })),
  }
}
