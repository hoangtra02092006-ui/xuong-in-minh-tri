import { describe, expect, it } from 'vitest'
import { apportion, CM_COLUMN_UNIT_MM, M_COLUMN_UNIT_MM } from '@/shared/apportion'
import { formatCmFixed, formatM } from '@/shared/units'

const sum = (xs: number[]) => xs.reduce((a, b) => a + b, 0)

describe('apportion (byType display, invariant 11)', () => {
  // Review repro, 100 cm limit job: each row rounded alone showed
  // 12,5 + 6,9 + 20,6 + 20,2 + 82,5 + 357,1 = 499,8 while the Total row showed 499,9.
  const repro = [125.2, 69.2, 206.2, 202.2, 825.2, 3571.2]

  it('reproduces the drift when rows are rounded independently', () => {
    const shown = repro.map((mm) => Number(formatCmFixed(mm, 1).replace(',', '.')))
    expect(Math.round(sum(shown) * 10) / 10).toBe(499.8)
    expect(formatCmFixed(sum(repro), 1)).toBe('499,9')
  })

  it('cm column: rows add up to the total, the extra mm goes to the largest row', () => {
    const { units, total } = apportion(repro, CM_COLUMN_UNIT_MM)
    expect(units).toEqual([125, 69, 206, 202, 825, 3572])
    expect(total).toBe(4999)
    expect(sum(units)).toBe(total)
    expect(units.map((u) => formatCmFixed(u * CM_COLUMN_UNIT_MM, 1))).toEqual([
      '12,5',
      '6,9',
      '20,6',
      '20,2',
      '82,5',
      '357,2',
    ])
    expect(formatCmFixed(total * CM_COLUMN_UNIT_MM, 1)).toBe('499,9')
  })

  it('m column: largest remainders get the missing centimetres', () => {
    const { units, total } = apportion(repro, M_COLUMN_UNIT_MM)
    // floors 12,6,20,20,82,357 = 497; total 500 cm; remainders 9,2 > 6,2 > 5,2 (tie → longer row).
    expect(units).toEqual([12, 7, 21, 20, 83, 357])
    expect(total).toBe(500)
    expect(formatM(total * M_COLUMN_UNIT_MM)).toBe('5,00')
  })

  it('a tiny design never shows 0: one unit is taken from the largest row', () => {
    // 0,4 cm design: alone it rounds to 0,00 m.
    expect(formatM(4)).toBe('0,00')
    const { units, total } = apportion([4, 2000, 3000], M_COLUMN_UNIT_MM, [true, true, true])
    expect(units).toEqual([1, 200, 299])
    expect(total).toBe(500)
    expect(units.map((u) => formatM(u * M_COLUMN_UNIT_MM))).toEqual(['0,01', '2,00', '2,99'])
  })

  it('tiny design in the cm column too (0,04 cm)', () => {
    const { units } = apportion([0.4, 100, 100], CM_COLUMN_UNIT_MM)
    expect(units[0]).toBe(1)
    expect(sum(units)).toBe(200)
  })

  it('rows without pieces may stay at 0; empty input gives an empty result', () => {
    expect(apportion([0, 500], M_COLUMN_UNIT_MM, [false, true]).units).toEqual([0, 50])
    expect(apportion([], M_COLUMN_UNIT_MM)).toEqual({ units: [], total: 0 })
  })

  it('keeps the sum when the total is too small to give every design a unit', () => {
    const { units, total } = apportion([3, 3, 3], M_COLUMN_UNIT_MM)
    expect(total).toBe(1)
    expect(sum(units)).toBe(1)
  })

  it('is deterministic on ties (earlier row first)', () => {
    expect(apportion([5, 5], M_COLUMN_UNIT_MM).units).toEqual([1, 0])
    expect(apportion([5, 15, 15], M_COLUMN_UNIT_MM).units).toEqual([1, 1, 2])
  })

  it('property: random jobs always sum to the total and never show 0 for a design', () => {
    // Fixed-seed LCG so a failure is reproducible.
    let seed = 20261125
    const rnd = () => {
      seed = (seed * 1103515245 + 12345) % 2 ** 31
      return seed / 2 ** 31
    }
    for (let run = 0; run < 2000; run++) {
      const n = 1 + Math.floor(rnd() * 12)
      const lengths = Array.from({ length: n }, () => {
        const tiny = rnd() < 0.25
        return Math.round((tiny ? rnd() * 8 : rnd() * 5000) * 100) / 100 || 0.01
      })
      const totalMm = sum(lengths)
      for (const unit of [CM_COLUMN_UNIT_MM, M_COLUMN_UNIT_MM]) {
        const { units, total } = apportion(lengths, unit)
        expect(sum(units)).toBe(total)
        expect(Math.abs(total * unit - totalMm)).toBeLessThanOrEqual(unit / 2 + 1e-6)
        if (total >= n) expect(units.every((u) => u >= 1)).toBe(true)
        // Each row stays within one unit of its exact share (plus any floor unit moved).
        units.forEach((u, i) => expect(Math.abs(u * unit - lengths[i])).toBeLessThan(unit * (n + 1)))
      }
    }
  })
})
