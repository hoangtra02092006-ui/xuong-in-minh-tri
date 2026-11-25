import { describe, expect, it } from 'vitest'
import { AxiosError, AxiosHeaders } from 'axios'
import { apiUrl, resolveApiConfig } from '@/api/config'
import { ApiError, toApiError } from '@/api/errors'

function axiosErr(status?: number, data?: unknown): AxiosError {
  const config = { headers: new AxiosHeaders() }
  const response = status ? { status, data, statusText: '', headers: {}, config } : undefined
  return new AxiosError('x', 'ERR', config, null, response)
}

describe('resolveApiConfig', () => {
  it('demo mode when no backend URL', () => {
    expect(resolveApiConfig({})).toEqual({ baseUrl: '', useMock: true })
  })
  it('real backend when URL set, trailing slash removed', () => {
    expect(resolveApiConfig({ VITE_API_BASE_URL: 'https://in.dreamate.me/' })).toEqual({
      baseUrl: 'https://in.dreamate.me',
      useMock: false,
    })
  })
  it('explicit flag wins', () => {
    expect(resolveApiConfig({ VITE_API_BASE_URL: 'https://x', VITE_USE_MOCK: 'true' }).useMock).toBe(true)
    expect(resolveApiConfig({ VITE_USE_MOCK: 'false' }).useMock).toBe(false)
  })
  it('builds absolute URLs for relative paths', () => {
    const cfg = { baseUrl: 'https://in.dreamate.me', useMock: false }
    expect(apiUrl('/api/v1/files/1/preview', cfg)).toBe('https://in.dreamate.me/api/v1/files/1/preview')
    expect(apiUrl('api/v1/x', cfg)).toBe('https://in.dreamate.me/api/v1/x')
  })
})

describe('toApiError', () => {
  it('no response becomes NETWORK_ERROR', () => {
    expect(toApiError(axiosErr()).code).toBe('NETWORK_ERROR')
  })
  it('uniform body is kept as is', () => {
    const e = toApiError(
      axiosErr(422, {
        error: { code: 'ITEM_WIDER_THAN_SHEET', message: 'Hình rộng', details: { fileId: 'a' } },
      }),
    )
    expect(e.code).toBe('ITEM_WIDER_THAN_SHEET')
    expect(e.message).toBe('Hình rộng')
    expect(e.details).toEqual({ fileId: 'a' })
  })
  it('unknown body becomes INTERNAL_ERROR with status', () => {
    const e = toApiError(axiosErr(502, '<html>'))
    expect(e.code).toBe('INTERNAL_ERROR')
    expect(e.message).toContain('502')
  })
  it('passes ApiError through and wraps anything else', () => {
    const original = new ApiError({ code: 'JOB_NOT_FOUND', message: 'x' })
    expect(toApiError(original)).toBe(original)
    expect(toApiError(new Error('boom')).code).toBe('INTERNAL_ERROR')
  })
})
