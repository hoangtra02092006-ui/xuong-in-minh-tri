import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMockApi } from '@/api/mock'
import type { NestStats, TypeStats } from '@/api/types'
import ByTypeTable from '@/features/nesting/components/ByTypeTable.vue'
import { setApi } from '@/stores/api-provider'

function row(i: number, lengthMm: number): TypeStats {
  return {
    fileId: `f${i}`,
    label: `mau-${i}.pdf`,
    categoryIndex: i,
    pieces: 3,
    widthMm: 50,
    heightMm: 40,
    shapeAreaMm2: 6000,
    lengthMm,
  }
}

function stats(lengths: number[]): NestStats {
  const byType = lengths.map((l, i) => row(i, l))
  return {
    totalSheets: 1,
    totalLengthMm: lengths.reduce((a, b) => a + b, 0),
    totalShapeAreaMm2: 6000 * lengths.length,
    usedAreaMm2: 1,
    fillRate: 0.8,
    savedVsIndividualPct: 10,
    totalPieces: 3 * lengths.length,
    byType,
  }
}

const column = (w: ReturnType<typeof mount>, part: 'tbody' | 'tfoot', label: string) =>
  w.findAll(`${part} td[data-label="${label}"]`).map((td) => td.text())
const num = (s: string) => Number(s.replace(/\./g, '').replace(',', '.'))

describe('ByTypeTable (spec 5.6 b, invariant 11)', () => {
  beforeEach(() => setApi(createMockApi({ storage: null })))

  it('cm rows add up exactly to the Total row (review repro 499,8 vs 499,9)', () => {
    const w = mount(ByTypeTable, { props: { stats: stats([125.2, 69.2, 206.2, 202.2, 825.2, 3571.2]) } })
    const cm = column(w, 'tbody', 'Chiều dài (cm)')
    expect(cm).toEqual(['12,5', '6,9', '20,6', '20,2', '82,5', '357,2'])
    expect(column(w, 'tfoot', 'Chiều dài (cm)')).toEqual(['499,9'])
    const m = column(w, 'tbody', 'Chiều dài (m)')
    expect(Math.round(m.map(num).reduce((a, b) => a + b, 0) * 100) / 100).toBe(
      num(column(w, 'tfoot', 'Chiều dài (m)')[0]),
    )
  })

  it('a tiny design never shows 0,00 m', () => {
    const w = mount(ByTypeTable, { props: { stats: stats([4, 2000, 3000]) } })
    expect(column(w, 'tbody', 'Chiều dài (m)')).toEqual(['0,01', '2,00', '2,99'])
    expect(column(w, 'tbody', 'Chiều dài (cm)')).toEqual(['0,4', '200,0', '300,0'])
    expect(column(w, 'tfoot', 'Chiều dài (m)')).toEqual(['5,00'])
  })
})
