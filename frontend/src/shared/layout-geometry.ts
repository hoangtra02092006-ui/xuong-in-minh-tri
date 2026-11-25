// Geometry for the layout preview SVG (spec 5.6 d). All values in mm, SVG y grows downwards.
import type { Placement } from '@/api/types'
import { mmToCm } from './units'

/** Ruler tick spacing: every 10 cm. */
export const TICK_MM = 100
/** Labels only on pieces at least this big (spec 5.6 d). */
export const LABEL_MIN_W_MM = 45
export const LABEL_MIN_H_MM = 22

export interface PieceShape {
  /** Rectangle of the design itself, before the transform. */
  x: number
  y: number
  w: number
  h: number
  cx: number
  cy: number
  /** SVG transform for free rotation, empty when none. */
  transform: string
  label: string | null
  fontSize: number
}

/** API origin is bottom-left (PDF); SVG is top-left: ySvg = length − y − h (spec 6.4). */
export function pieceShape(p: Placement, sheetLengthMm: number, labelText: string): PieceShape {
  const cx = p.xMm + p.wMm / 2
  const cy = sheetLengthMm - p.yMm - p.hMm / 2
  const tilted = p.angleDeg !== 0
  // Tilted pieces: draw the real source rectangle rotated around the box centre. API angles are
  // counter-clockwise in a y-up frame, which is a negative SVG rotation in a y-down frame.
  const w = tilted ? p.sourceWMm : p.wMm
  const h = tilted ? p.sourceHMm : p.hMm
  const showLabel = p.wMm >= LABEL_MIN_W_MM && p.hMm >= LABEL_MIN_H_MM
  return {
    x: cx - w / 2,
    y: cy - h / 2,
    w,
    h,
    cx,
    cy,
    transform: tilted ? `rotate(${-p.angleDeg} ${cx} ${cy})` : '',
    label: showLabel ? labelText : null,
    fontSize: Math.max(6, Math.min(16, w / 5, h / 2.4)),
  }
}

export interface Tick {
  pos: number
  label: string
}

/** Ticks along a length; `fromEnd` counts from the far end (vertical ruler counts bottom-up). */
export function ticks(lengthMm: number, fromEnd = false, format: (cm: number) => string = String): Tick[] {
  const out: Tick[] = []
  for (let mm = 0; mm <= lengthMm + 0.001; mm += TICK_MM) {
    out.push({ pos: fromEnd ? lengthMm - mm : mm, label: format(mmToCm(mm)) })
  }
  return out
}

/** Human text for a piece's rotation (tooltip). */
export function rotationText(p: Placement): string {
  if (p.angleDeg !== 0) {
    return `Xoay ${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(p.angleDeg)} độ`
  }
  return p.rotated ? 'Đã xoay 90 độ' : 'Giữ nguyên hướng'
}
