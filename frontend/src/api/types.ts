// API contract types (spec section 6, design 4a). All lengths are millimetres.
// Only the coordinator changes this file.

export type ErrorCode =
  | 'FILE_TOO_LARGE'
  | 'UNSUPPORTED_FORMAT'
  | 'SIZE_UNREADABLE'
  | 'ITEM_WIDER_THAN_SHEET'
  | 'INVALID_REQUEST'
  | 'JOB_NOT_FOUND'
  | 'FILE_NOT_FOUND'
  | 'JOB_NOT_READY'
  | 'NESTING_FAILED'
  | 'TIFF_TOO_LARGE'
  | 'INTERNAL_ERROR'
  | 'NETWORK_ERROR'

export interface ApiErrorBody {
  code: ErrorCode
  message: string
  details?: Record<string, unknown>
}

export interface UploadedFile {
  id: string
  originalName: string
  widthMm: number
  heightMm: number
  sourceWidthMm: number
  sourceHeightMm: number
  trimmed: boolean
  type: 'PDF' | 'IMAGE'
  pageCount: number
  /** Relative path, prefix with the API base URL. */
  previewUrl: string
}

export type NestingMode = 'ORTHOGONAL' | 'FREE' | 'TRUE_SHAPE'

export interface NestItemRequest {
  fileId: string
  quantity: number
  allowRotate: boolean
  widthMm?: number
  heightMm?: number
}

export interface NestRequest {
  mode: NestingMode
  sheetWidthMm: number
  marginMm: number
  gapMm: number
  maxSheetLengthMm: number | null
  allowRotateGlobal: boolean
  drawCutLines: boolean
  items: NestItemRequest[]
}

export type JobStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'

export interface Placement {
  fileId: string
  label: string
  categoryIndex: number
  /** Bottom-left of the bounding box, origin at the sheet's bottom-left corner. */
  xMm: number
  yMm: number
  /** Bounding box after rotation. */
  wMm: number
  hMm: number
  rotated: boolean
  /** Free rotation in degrees, counter-clockwise; non-zero only in TRUE_SHAPE. */
  angleDeg: number
  /** Size before rotation. */
  sourceWMm: number
  sourceHMm: number
}

export type TifStatus = 'QUEUED' | 'BUILDING' | 'READY' | 'FAILED'

export interface SheetTif {
  status: TifStatus
  sizeBytes: number | null
  error: ApiErrorBody | null
}

export interface Sheet {
  index: number
  widthMm: number
  lengthMm: number
  fillRate: number
  placements: Placement[]
  /** Background TIF pre-build state (design 4a). */
  tif: SheetTif
}

export interface TypeStats {
  fileId: string
  label: string
  categoryIndex: number
  pieces: number
  widthMm: number
  heightMm: number
  shapeAreaMm2: number
  lengthMm: number
}

export interface NestStats {
  totalSheets: number
  totalLengthMm: number
  totalShapeAreaMm2: number
  usedAreaMm2: number
  fillRate: number
  savedVsIndividualPct: number
  totalPieces: number
  byType: TypeStats[]
}

export interface NestJob {
  jobId: string
  status: JobStatus
  progress: number
  createdAt: string
  finishedAt?: string
  result?: { sheets: Sheet[]; stats: NestStats }
  error?: ApiErrorBody
}

export type ExportFormat = 'pdf' | 'tif' | 'cut'

export interface Health {
  status: 'UP'
  startedAt: string
  heapUsedMb: number
  heapMaxMb: number
}
