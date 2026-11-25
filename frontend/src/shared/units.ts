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

/** File size for download buttons: "5,2 MB". */
export function formatBytes(bytes: number): string {
  if (bytes < 1024 * 1024) return `${formatInt(Math.max(1, bytes / 1024))} KB`
  return `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(bytes / (1024 * 1024))} MB`
}
