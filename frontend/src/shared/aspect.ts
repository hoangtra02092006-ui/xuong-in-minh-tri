import { roundMm } from './units'

export type Side = 'width' | 'height'

export interface SizeMm {
  widthMm: number
  heightMm: number
}

/**
 * Locked aspect ratio (spec 5.4): the other side is computed from the ORIGINAL size read from
 * the file, never from the value currently shown, so repeated edits cannot drift.
 * Returns null when the value is empty, <= 0 or the original size is unusable.
 */
export function resizeLocked(original: SizeMm, side: Side, valueMm: number | null): SizeMm | null {
  if (valueMm === null || !Number.isFinite(valueMm) || valueMm <= 0) return null
  if (original.widthMm <= 0 || original.heightMm <= 0) return null
  const edited = roundMm(valueMm)
  if (edited <= 0) return null
  if (side === 'width') {
    return { widthMm: edited, heightMm: roundMm((edited * original.heightMm) / original.widthMm) }
  }
  return { widthMm: roundMm((edited * original.widthMm) / original.heightMm), heightMm: edited }
}

/** True when the current size differs from the size read from the file. */
export function isResized(original: SizeMm, current: SizeMm): boolean {
  return original.widthMm !== current.widthMm || original.heightMm !== current.heightMm
}
