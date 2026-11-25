// Decides backend URL and demo mode once (spec 5.8, 13).
export interface ApiConfig {
  baseUrl: string
  useMock: boolean
}

export function resolveApiConfig(env: { VITE_API_BASE_URL?: string; VITE_USE_MOCK?: string }): ApiConfig {
  const baseUrl = (env.VITE_API_BASE_URL ?? '').trim().replace(/\/+$/, '')
  const flag = (env.VITE_USE_MOCK ?? '').trim().toLowerCase()
  const useMock = flag === 'true' ? true : flag === 'false' ? false : baseUrl === ''
  return { baseUrl, useMock }
}

export const apiConfig = resolveApiConfig(import.meta.env)

/** Absolute URL for a backend path such as previewUrl or a download link. */
export function apiUrl(path: string, config: ApiConfig = apiConfig): string {
  return `${config.baseUrl}${path.startsWith('/') ? path : `/${path}`}`
}
