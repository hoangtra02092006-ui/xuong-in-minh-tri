import { describe, expect, it } from 'vitest'
import { isResized, resizeLocked } from '@/shared/aspect'
import {
  buildNestRequest,
  DEFAULT_PARAMS,
  estimateMinLengthMm,
  hasInvalidSize,
  itemsWiderThanSheet,
  newItem,
  nonNestableItems,
  normalizeQuantity,
  orderTotals,
  validateParams,
} from '@/shared/order'
import { cmToMm, parseDecimal } from '@/shared/units'
import { file } from './helpers'

describe('locked aspect ratio (spec 5.4)', () => {
  const original = { widthMm: 130, heightMm: 40 }

  it('computes the other side from the original file size', () => {
    expect(resizeLocked(original, 'width', 65)).toEqual({ widthMm: 65, heightMm: 20 })
    expect(resizeLocked(original, 'height', 80)).toEqual({ widthMm: 260, heightMm: 80 })
  })

  it('does not drift after many edits back and forth', () => {
    const o = { widthMm: 50.8, heightMm: 33.87 }
    let size = { ...o }
    const inputs = ['3,3', '7.77', '1,23', '9,99', '2,5', '6,1', '4,44', '8.8', '3,33', '5,08']
    for (let round = 0; round < 50; round++) {
      for (const [i, raw] of inputs.entries()) {
        const next = resizeLocked(o, i % 2 ? 'height' : 'width', cmToMm(parseDecimal(raw) as number))
        if (next) size = next
      }
    }
    // Ratio still that of the file (within 0.01 mm rounding), not of earlier rounded values.
    expect(Math.abs(size.heightMm - (size.widthMm * o.heightMm) / o.widthMm)).toBeLessThanOrEqual(0.01)
    // Back to the original width: height is exactly the original again.
    expect(resizeLocked(o, 'width', 50.8)).toEqual(o)
    // Each result depends only on the last edit, never on accumulated values.
    expect(resizeLocked(o, 'width', 50.8 * 2)).toEqual({ widthMm: 101.6, heightMm: 67.74 })
  })

  it('ignores empty, zero, negative or unreadable values', () => {
    expect(resizeLocked(original, 'width', null)).toBeNull()
    expect(resizeLocked(original, 'width', 0)).toBeNull()
    expect(resizeLocked(original, 'height', -2)).toBeNull()
    expect(resizeLocked(original, 'height', Number.NaN)).toBeNull()
    expect(resizeLocked({ widthMm: 0, heightMm: 10 }, 'width', 5)).toBeNull()
    expect(resizeLocked(original, 'width', 0.001)).toBeNull()
  })

  it('detects resized items', () => {
    expect(isResized(original, original)).toBe(false)
    expect(isResized(original, { widthMm: 65, heightMm: 20 })).toBe(true)
  })
})

describe('settings validation (spec 5.5 wording)', () => {
  const v = (patch: Partial<typeof DEFAULT_PARAMS>) => validateParams({ ...DEFAULT_PARAMS, ...patch })

  it('defaults are valid and converted to mm', () => {
    expect(v({}).parsed).toEqual({ sheetWidthMm: 570, marginMm: 5, gapMm: 3, maxSheetLengthMm: 1000 })
  })

  it('accepts comma and dot', () => {
    expect(v({ marginCm: '0.5', gapCm: '0,25' }).parsed?.gapMm).toBe(2.5)
  })

  it('sheet width errors', () => {
    expect(v({ sheetWidthCm: '0' }).errors.sheetWidthCm).toBe('Khổ ngang phải lớn hơn 0.')
    expect(v({ sheetWidthCm: '' }).errors.sheetWidthCm).toBe('Khổ ngang phải lớn hơn 0.')
    expect(v({ sheetWidthCm: '1200' }).errors.sheetWidthCm).toBe(
      'Khổ ngang lớn bất thường, hãy kiểm tra lại đơn vị.',
    )
  })

  it('margin and gap errors', () => {
    expect(v({ marginCm: '-1' }).errors.marginCm).toBe('Lề biên không được âm.')
    expect(v({ marginCm: '30' }).errors.marginCm).toBe('Lề biên quá lớn so với khổ ngang.')
    expect(v({ marginCm: '' }).errors.marginCm).toBeTruthy()
    expect(v({ gapCm: '-0,1' }).errors.gapCm).toBe('Khoảng cách không được âm.')
    expect(v({ gapCm: 'x' }).errors.gapCm).toBeTruthy()
    expect(v({ gapCm: 'x' }).parsed).toBeNull()
  })

  it('max length: empty or <= 0 means unlimited, text is an error', () => {
    expect(v({ maxLengthCm: '' }).parsed?.maxSheetLengthMm).toBeNull()
    expect(v({ maxLengthCm: '0' }).parsed?.maxSheetLengthMm).toBeNull()
    expect(v({ maxLengthCm: 'abc' }).errors.maxLengthCm).toBeTruthy()
  })
})

describe('order helpers', () => {
  const items = [newItem(file('a', 130, 40)), newItem(file('b', 200, 180, 'IMAGE'))]
  items[0].quantity = 10

  it('new items default to quantity 1 and rotation on', () => {
    expect(items[1]).toMatchObject({ quantity: 1, allowRotate: true, widthMm: 200, heightMm: 180 })
  })

  it('quantity: integer, empty or < 1 becomes 1', () => {
    expect(normalizeQuantity('')).toBe(1)
    expect(normalizeQuantity('0')).toBe(1)
    expect(normalizeQuantity('-4')).toBe(1)
    expect(normalizeQuantity('7,9')).toBe(7)
    expect(normalizeQuantity(12)).toBe(12)
  })

  it('totals: types, pieces, area', () => {
    expect(orderTotals(items)).toEqual({ types: 2, pieces: 11, areaMm2: 130 * 40 * 10 + 200 * 180 })
  })

  it('estimated minimum length = area / usable width', () => {
    const parsed = validateParams(DEFAULT_PARAMS).parsed
    expect(estimateMinLengthMm(items, parsed)).toBeCloseTo((52000 + 36000) / 560, 6)
    expect(estimateMinLengthMm([], parsed)).toBeNull()
    expect(estimateMinLengthMm(items, null)).toBeNull()
    expect(
      estimateMinLengthMm(items, { sheetWidthMm: 10, marginMm: 5, gapMm: 0, maxSheetLengthMm: null }),
    ).toBeNull()
  })

  it('items wider than the sheet consider rotation', () => {
    const wide = [newItem(file('w', 600, 590))]
    const parsed = validateParams(DEFAULT_PARAMS).parsed
    expect(itemsWiderThanSheet(wide, parsed, true)).toHaveLength(1)
    const tall = [newItem(file('t', 600, 100))]
    expect(itemsWiderThanSheet(tall, parsed, true)).toHaveLength(0)
    expect(itemsWiderThanSheet(tall, parsed, false)).toHaveLength(1)
    expect(itemsWiderThanSheet(tall, null, true)).toHaveLength(0)
  })

  it('non-nestable warning: images and resized designs, only in FREE / TRUE_SHAPE', () => {
    const list = [
      newItem(file('p', 100, 100)),
      newItem(file('i', 50, 50, 'IMAGE')),
      newItem(file('r', 100, 50)),
    ]
    list[2].widthMm = 50
    list[2].heightMm = 25
    expect(nonNestableItems(list, 'ORTHOGONAL')).toHaveLength(0)
    expect(nonNestableItems(list, 'TRUE_SHAPE').map((i) => i.file.id)).toEqual(['i', 'r'])
  })

  it('invalid size blocks continue', () => {
    const bad = [newItem(file('z', 0, 10))]
    expect(hasInvalidSize(bad)).toBe(true)
    expect(hasInvalidSize(items)).toBe(false)
  })

  it('builds the API request in mm', () => {
    const parsed = validateParams(DEFAULT_PARAMS).parsed!
    const req = buildNestRequest(items, DEFAULT_PARAMS, parsed)
    expect(req).toMatchObject({
      mode: 'ORTHOGONAL',
      sheetWidthMm: 570,
      marginMm: 5,
      gapMm: 3,
      maxSheetLengthMm: 1000,
      allowRotateGlobal: true,
      drawCutLines: false,
    })
    expect(req.items[0]).toEqual({ fileId: 'a', quantity: 10, allowRotate: true, widthMm: 130, heightMm: 40 })
  })
})
