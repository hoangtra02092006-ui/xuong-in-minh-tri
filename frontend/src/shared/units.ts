// The only place where the UI converts units and formats numbers (invariant 7, spec 5.1).
// UI shows cm, API uses mm.

const viInt = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 })

/** Parses user input; accepts both "2,5" and "2.5". Returns null for empty or invalid text. */
export function parseDecimal(raw: string): number | null {
  const text = raw.trim().replace(/\s+/g, '').replace(',', '.')
  if (text === '' || !/^-?\d*\.?\d+$|^-?\d+\.$/.test(text)) return null
  const value = Number(text)
  return Number.isFinite(value) ? value : null
}

export function cmToMm(cm: number): number {
  return Math.round(cm * 10 * 100) / 100
}

export function mmToCm(mm: number): number {
  return mm / 10
}

/** cm without trailing zeros, Vietnamese decimal comma: 57, 0,3, 33,87. */
export function formatCm(mm: number, maxDecimals = 2): string {
  return new Intl.NumberFormat('vi-VN', { maximumFractionDigits: maxDecimals, useGrouping: true }).format(
    mmToCm(mm),
  )
}

/** Metres, always 2 decimals: 4,78. */
export function formatM(mm: number): string {
  return new Intl.NumberFormat('vi-VN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(
    mm / 1000,
  )
}

/** Percent from a 0..1 ratio, 1 decimal: 92,5. */
export function formatPercent(ratio: number): string {
  return new Intl.NumberFormat('vi-VN', { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(
    ratio * 100,
  )
}

/** Integer with Vietnamese thousands separator: 25.200. */
export function formatInt(value: number): string {
  return viInt.format(Math.round(value))
}

/** PDF points to mm (1 pt = 1/72 inch), rounded to 0.01 mm. */
export function ptToMm(pt: number): number {
  return roundMm((pt * 25.4) / 72)
}

/** Pixels at a given DPI to mm, rounded to 0.01 mm (browser images use 96 DPI). */
export function pxToMm(px: number, dpi = 96): number {
  return roundMm((px * 25.4) / dpi)
}

/** Rounds a length to 0.01 mm, the API precision. */
export function roundMm(mm: number): number {
  return Math.round(mm * 100) / 100
}

/** Engine unit: 1/100 mm integers (demo layout mirrors the backend so sums never drift). */
export const CMM_PER_MM = 100

export function mmToCmm(mm: number): number {
  return Math.round(mm * CMM_PER_MM)
}

export function cmmToMm(cmm: number): number {
  return Math.round(cmm) / CMM_PER_MM
}

/** Square cm from square mm. */
export function mm2ToCm2(mm2: number): number {
  return mm2 / 100
}

/** Area in cm² with thousands separator: "2.520". */
export function formatAreaCm2(mm2: number): string {
  return formatInt(mm2ToCm2(mm2))
}

/** "W x H cm" label used across tables, captions and tooltips. */
export function formatSizeCm(widthMm: number, heightMm: number): string {
  return `${formatCm(widthMm)} x ${formatCm(heightMm)} cm`
}

/** cm with a fixed number of decimals (byType length "341,6"). */
export function formatCmFixed(mm: number, decimals: number): string {
  return new Intl.NumberFormat('vi-VN', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(mmToCm(mm))
}

/** cm for a text input: no grouping so it parses back ("1200,5"). */
export function cmInputValue(mm: number): string {
  return new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2, useGrouping: false }).format(mmToCm(mm))
}

/** File size for download buttons: "5,2 MB". */
export function formatBytes(bytes: number): string {
  if (bytes < 1024 * 1024) return `${formatInt(Math.max(1, bytes / 1024))} KB`
  return `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(bytes / (1024 * 1024))} MB`
}
