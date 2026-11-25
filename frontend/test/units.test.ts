import { describe, expect, it } from 'vitest'
import {
  cmToMm,
  formatBytes,
  formatCm,
  formatInt,
  formatM,
  formatPercent,
  parseDecimal,
} from '@/shared/units'
import { categoryColor } from '@/shared/category-colors'

describe('parseDecimal', () => {
  it('accepts comma and dot', () => {
    expect(parseDecimal('2,5')).toBe(2.5)
    expect(parseDecimal('2.5')).toBe(2.5)
    expect(parseDecimal(' 57 ')).toBe(57)
  })
  it('rejects empty and text', () => {
    expect(parseDecimal('')).toBeNull()
    expect(parseDecimal('abc')).toBeNull()
    expect(parseDecimal('2,5cm')).toBeNull()
  })
})

describe('formatting (Vietnamese style)', () => {
  it('cm drops trailing zeros and uses decimal comma', () => {
    expect(formatCm(570)).toBe('57')
    expect(formatCm(3)).toBe('0,3')
    expect(formatCm(338.7)).toBe('33,87')
  })
  it('metres always 2 decimals, percent 1 decimal, thousands dot', () => {
    expect(formatM(4782)).toBe('4,78')
    expect(formatPercent(0.92453)).toBe('92,5')
    expect(formatInt(25200)).toBe('25.200')
  })
  it('converts cm to mm with 0.01 mm rounding', () => {
    expect(cmToMm(0.3)).toBe(3)
    expect(cmToMm(3.3333)).toBe(33.33)
  })
  it('formats file sizes', () => {
    expect(formatBytes(5.2 * 1024 * 1024)).toBe('5,2 MB')
    expect(formatBytes(2048)).toBe('2 KB')
  })
})

describe('categoryColor', () => {
  it('cycles through 8 colours', () => {
    expect(categoryColor(0)).toBe(categoryColor(8))
    expect(categoryColor(1)).not.toBe(categoryColor(0))
  })
})
