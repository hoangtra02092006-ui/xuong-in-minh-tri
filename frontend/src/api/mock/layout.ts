// Demo-mode nesting (spec 5.8): a simple shelf layout in 1/100 mm integers that returns the
// real result schema. It is NOT the backend engine: no search, no hill climbing, lower fill.
import { ApiError } from '../errors'
import type { NestRequest, NestStats, Placement, Sheet, TypeStats, UploadedFile } from '../types'
import { CMM_PER_MM as U, cmmToMm as toMm, formatCm, mmToCmm as toU } from '@/shared/units'

/** Demo only: in TRUE_SHAPE every other rotatable piece is drawn tilted, to show angle rendering. */
export const DEMO_TILT_DEG = 15

interface Piece {
  fileId: string
  label: string
  categoryIndex: number
  w: number
  h: number
  rotate: boolean
  seq: number
}

interface Placed {
  piece: Piece
  x: number
  y: number
  bw: number
  bh: number
  rotated: boolean
  angle: number
}

export interface MockLayout {
  sheets: Omit<Sheet, 'tif'>[]
  stats: NestStats
}

function bboxForAngle(w: number, h: number, deg: number): { bw: number; bh: number } {
  const r = (deg * Math.PI) / 180
  const c = Math.abs(Math.cos(r))
  const s = Math.abs(Math.sin(r))
  return { bw: Math.ceil(w * c + h * s), bh: Math.ceil(w * s + h * c) }
}

/** Sync checks done before a job exists (spec 6.3). Throws ApiError like the backend. */
export function validateRequest(req: NestRequest, files: Map<string, UploadedFile>): void {
  if (req.items.length === 0) {
    throw new ApiError({ code: 'INVALID_REQUEST', message: 'Danh sách hình không được rỗng.' })
  }
  if (!(req.sheetWidthMm > 0)) {
    throw new ApiError({ code: 'INVALID_REQUEST', message: 'Khổ ngang phải lớn hơn 0.' })
  }
  const usable = req.sheetWidthMm - 2 * req.marginMm
  for (const item of req.items) {
    const file = files.get(item.fileId)
    if (!file) {
      throw new ApiError({
        code: 'FILE_NOT_FOUND',
        message: 'Không tìm thấy file đã tải lên. Có thể máy chủ đã dọn file cũ, hãy nạp lại.',
        details: { fileId: item.fileId },
      })
    }
    const w = item.widthMm ?? file.widthMm
    const h = item.heightMm ?? file.heightMm
    const rotate = req.allowRotateGlobal && item.allowRotate
    const needed = rotate ? Math.min(w, h) : w
    if (usable > 0 && needed > usable) {
      const tail = rotate ? '(đã tính cả trường hợp xoay).' : 'và không được phép xoay.'
      throw new ApiError({
        code: 'ITEM_WIDER_THAN_SHEET',
        message: `Hình "${file.originalName}" ${formatCm(w)}x${formatCm(h)} cm rộng hơn khổ ${formatCm(req.sheetWidthMm)} cm ${tail}`,
        details: { fileId: file.id, label: file.originalName },
      })
    }
  }
}

function expandPieces(req: NestRequest, files: Map<string, UploadedFile>): Piece[] {
  const pieces: Piece[] = []
  req.items.forEach((item, categoryIndex) => {
    const file = files.get(item.fileId) as UploadedFile
    for (let k = 0; k < item.quantity; k++) {
      pieces.push({
        fileId: item.fileId,
        label: file.originalName,
        categoryIndex,
        w: toU(item.widthMm ?? file.widthMm),
        h: toU(item.heightMm ?? file.heightMm),
        rotate: req.allowRotateGlobal && item.allowRotate,
        seq: k,
      })
    }
  })
  return pieces
}

/** Orientation for a shelf layout: long side across the roll when it fits (shorter shelves). */
function orient(p: Piece, usable: number, mode: NestRequest['mode']): Omit<Placed, 'x' | 'y'> {
  if (mode === 'TRUE_SHAPE' && p.rotate && p.seq % 2 === 1) {
    const b = bboxForAngle(p.w, p.h, DEMO_TILT_DEG)
    if (b.bw <= usable) return { piece: p, ...b, rotated: false, angle: DEMO_TILT_DEG }
  }
  const turn = p.rotate && p.h > p.w && p.h <= usable
  return turn
    ? { piece: p, bw: p.h, bh: p.w, rotated: true, angle: 0 }
    : { piece: p, bw: p.w, bh: p.h, rotated: false, angle: 0 }
}

/** Runs the demo layout. Engine-level problems throw ApiError (they become a FAILED job). */
export function layoutShelves(req: NestRequest, files: Map<string, UploadedFile>): MockLayout {
  const W = toU(req.sheetWidthMm)
  const M = toU(req.marginMm)
  const G = toU(req.gapMm)
  const usable = W - 2 * M
  if (usable <= 0) {
    throw new ApiError({
      code: 'INVALID_REQUEST',
      message: 'Lề biên quá lớn so với khổ ngang: không còn vùng nào để xếp hình.',
    })
  }
  const maxInner = req.maxSheetLengthMm === null ? Infinity : toU(req.maxSheetLengthMm) - 2 * M

  const oriented = expandPieces(req, files).map((p) => orient(p, usable, req.mode))
  const tallest = Math.max(...oriented.map((o) => o.bh))
  if (tallest > maxInner) {
    throw new ApiError({
      code: 'INVALID_REQUEST',
      message: `Chiều dài tối đa mỗi file (${formatCm(req.maxSheetLengthMm ?? 0)} cm) nhỏ hơn hình cao nhất (${formatCm(toMm(tallest))} cm). Hãy tăng giới hạn chiều dài.`,
    })
  }
  oriented.sort(
    (a, b) => b.bh - a.bh || a.piece.categoryIndex - b.piece.categoryIndex || a.piece.seq - b.piece.seq,
  )

  // First-fit shelves on the current sheet; a rotatable piece may also try its other orientation
  // to fill the space left at the end of a taller shelf.
  interface Shelf {
    y: number
    h: number
    x: number
  }
  const sheetsPlaced: Placed[][] = [[]]
  const shelvesBySheet: Shelf[][] = [[]]
  const variants = (o: Omit<Placed, 'x' | 'y'>) =>
    o.piece.rotate && o.angle === 0 && o.bh <= usable
      ? [o, { ...o, bw: o.bh, bh: o.bw, rotated: !o.rotated }]
      : [o]
  for (const o of oriented) {
    let placed = false
    for (let si = 0; si < shelvesBySheet.length && !placed; si++) {
      for (const s of shelvesBySheet[si]) {
        const v = variants(o).find((c) => s.x + c.bw <= usable && c.bh <= s.h)
        if (v) {
          sheetsPlaced[si].push({ ...v, x: M + s.x, y: M + s.y })
          s.x += v.bw + G
          placed = true
          break
        }
      }
    }
    if (placed) continue
    let shelves = shelvesBySheet[shelvesBySheet.length - 1]
    const last = shelves[shelves.length - 1]
    let y = last ? last.y + last.h + G : 0
    if (y + o.bh > maxInner) {
      sheetsPlaced.push([])
      shelves = []
      shelvesBySheet.push(shelves)
      y = 0
    }
    shelves.push({ y, h: o.bh, x: o.bw + G })
    sheetsPlaced[sheetsPlaced.length - 1].push({ ...o, x: M, y: M + y })
  }

  const sheets = sheetsPlaced.map((placed, index) => {
    const length = Math.max(...placed.map((p) => p.y + p.bh)) + M
    const boxArea = placed.reduce((s, p) => s + p.bw * p.bh, 0)
    const placements: Placement[] = placed.map((p) => ({
      fileId: p.piece.fileId,
      label: p.piece.label,
      categoryIndex: p.piece.categoryIndex,
      xMm: toMm(p.x),
      yMm: toMm(p.y),
      wMm: toMm(p.bw),
      hMm: toMm(p.bh),
      rotated: p.rotated,
      angleDeg: p.angle,
      sourceWMm: toMm(p.piece.w),
      sourceHMm: toMm(p.piece.h),
    }))
    return {
      index,
      widthMm: toMm(W),
      lengthMm: toMm(length),
      fillRate: Math.round((boxArea / (W * length)) * 10_000) / 10_000,
      placements,
      boxArea,
      lengthU: length,
    }
  })

  const totalLengthU = sheets.reduce((s, sh) => s + sh.lengthU, 0)
  const individualU = oriented.reduce((s, o) => s + o.bh + G, 2 * M - G)
  const unionArea = sheets.reduce((s, sh) => s + sh.boxArea, 0)
  const usedArea = sheets.reduce((s, sh) => s + W * sh.lengthU, 0)

  const byType = splitLengthByType(req, files, totalLengthU)
  const totalShapeU2 = byType.reduce((s, t) => s + t.shapeU2, 0)

  const stats: NestStats = {
    totalSheets: sheets.length,
    totalLengthMm: toMm(totalLengthU),
    totalShapeAreaMm2: Math.round(totalShapeU2 / (U * U)),
    usedAreaMm2: Math.round(usedArea / (U * U)),
    fillRate: Math.round((unionArea / usedArea) * 10_000) / 10_000,
    savedVsIndividualPct: Math.round((1 - totalLengthU / individualU) * 1000) / 10,
    totalPieces: oriented.length,
    byType: byType.map((t) => t.stats),
  }
  return {
    sheets: sheets.map((s) => ({
      index: s.index,
      widthMm: s.widthMm,
      lengthMm: s.lengthMm,
      fillRate: s.fillRate,
      placements: s.placements,
    })),
    stats,
  }
}

/**
 * Length per design (spec 9): total length shared by own shape area, largest remainder in
 * 1/100 mm so the rows add up exactly to the total (invariant 11).
 */
export function splitLengthByType(
  req: NestRequest,
  files: Map<string, UploadedFile>,
  totalLengthU: number,
): { stats: TypeStats; shapeU2: number }[] {
  const rows = req.items.map((item, categoryIndex) => {
    const file = files.get(item.fileId) as UploadedFile
    const w = item.widthMm ?? file.widthMm
    const h = item.heightMm ?? file.heightMm
    return { item, file, categoryIndex, w, h, shapeU2: toU(w) * toU(h) * item.quantity }
  })
  const total = rows.reduce((s, r) => s + r.shapeU2, 0)
  const exact = rows.map((r) => (total > 0 ? (totalLengthU * r.shapeU2) / total : 0))
  const floors = exact.map(Math.floor)
  let rest = totalLengthU - floors.reduce((s, v) => s + v, 0)
  const order = exact
    .map((v, i) => ({ i, frac: v - Math.floor(v) }))
    .sort((a, b) => b.frac - a.frac || a.i - b.i)
  for (const { i } of order) {
    if (rest <= 0) break
    floors[i] += 1
    rest -= 1
  }
  return rows.map((r, i) => ({
    shapeU2: r.shapeU2,
    stats: {
      fileId: r.file.id,
      label: r.file.originalName,
      categoryIndex: r.categoryIndex,
      pieces: r.item.quantity,
      widthMm: r.w,
      heightMm: r.h,
      shapeAreaMm2: Math.round(r.shapeU2 / (U * U)),
      lengthMm: toMm(floors[i]),
    },
  }))
}
