// Display rounding for the "Từng mẫu ăn mấy mét" table (spec 5.6 b, 9; invariant 11).
// Rounding each row on its own lets the rows drift from the Total row (499,8 vs 499,9) and can
// show 0 for a tiny design. Here rows are apportioned in whole display units with the largest
// remainder method, so the rows add up exactly to the displayed total.
import { CMM_PER_MM, mmToCmm } from './units'

/** Display units: the cm column shows 1 decimal (1 mm), the m column 2 decimals (1 cm). */
export const CM_COLUMN_UNIT_MM = 1
export const M_COLUMN_UNIT_MM = 10

export interface Apportioned {
  /** Whole display units per row, same order as the input. */
  units: number[]
  /** Whole display units of the total; always equals the sum of `units`. */
  total: number
}

/**
 * Splits lengths (mm) into whole display units of `unitMm`.
 * - Σ units === total === round(Σ lengths / unit), so rows add up to the Total row.
 * - Every row flagged in `mustShow` (a design with pieces) gets at least one unit, taken from
 *   the largest rows, as long as the total has enough units for all of them.
 * Deterministic: ties go to the larger length, then to the earlier row.
 */
export function apportion(lengthsMm: number[], unitMm: number, mustShow?: boolean[]): Apportioned {
  // Integer 1/100 mm, like the engine, so remainders never suffer float drift.
  const cmm = lengthsMm.map((mm) => Math.max(0, mmToCmm(mm)))
  const unit = Math.round(unitMm * CMM_PER_MM)
  const sum = cmm.reduce((a, b) => a + b, 0)
  const total = Math.round(sum / unit)
  const units = cmm.map((v) => Math.floor(v / unit))
  const byLength = (a: number, b: number) => cmm[b] - cmm[a] || a - b

  let left = total - units.reduce((a, b) => a + b, 0)
  const byRemainder = cmm.map((_, i) => i).sort((a, b) => (cmm[b] % unit) - (cmm[a] % unit) || byLength(a, b))
  for (const i of byRemainder) {
    if (left <= 0) break
    units[i] += 1
    left -= 1
  }

  const flags = mustShow ?? cmm.map((v) => v > 0)
  const needy = cmm
    .map((_, i) => i)
    .filter((i) => flags[i] && units[i] === 0)
    .sort(byLength)
  for (const i of needy) {
    const donor = largestDonor(units, cmm)
    if (donor < 0) break
    units[donor] -= 1
    units[i] += 1
  }
  return { units, total }
}

/** Row with the most units (≥ 2, so it keeps at least one); ties go to the longer row. */
function largestDonor(units: number[], cmm: number[]): number {
  let best = -1
  for (let i = 0; i < units.length; i++) {
    if (units[i] < 2) continue
    if (best < 0 || units[i] > units[best] || (units[i] === units[best] && cmm[i] > cmm[best])) best = i
  }
  return best
}
