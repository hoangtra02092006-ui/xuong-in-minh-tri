import { isAxiosError } from 'axios'
import type { ApiErrorBody } from './types'

/** Error thrown by every API call; the UI branches on `code`, never parses `message`. */
export class ApiError extends Error {
  readonly code: ApiErrorBody['code']
  readonly details: Record<string, unknown>

  constructor(body: ApiErrorBody) {
    super(body.message)
    this.name = 'ApiError'
    this.code = body.code
    this.details = body.details ?? {}
  }
}

function isErrorBody(data: unknown): data is { error: ApiErrorBody } {
  if (typeof data !== 'object' || data === null || !('error' in data)) return false
  const e = (data as { error: unknown }).error
  return typeof e === 'object' && e !== null && 'code' in e && 'message' in e
}

/** Normalizes any thrown value into ApiError (spec 6: NETWORK_ERROR / INTERNAL_ERROR fallbacks). */
export function toApiError(err: unknown): ApiError {
  if (err instanceof ApiError) return err
  if (isAxiosError(err)) {
    if (!err.response) {
      return new ApiError({
        code: 'NETWORK_ERROR',
        message:
          'Không kết nối được tới máy chủ. Hãy kiểm tra backend đã chạy chưa, hoặc thử lại sau ít phút.',
      })
    }
    if (isErrorBody(err.response.data)) return new ApiError(err.response.data.error)
    return new ApiError({ code: 'INTERNAL_ERROR', message: `Máy chủ trả về lỗi ${err.response.status}.` })
  }
  return new ApiError({ code: 'INTERNAL_ERROR', message: 'Có lỗi không mong muốn. Hãy thử lại.' })
}
