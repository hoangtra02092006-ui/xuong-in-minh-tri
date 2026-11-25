import axios from 'axios'
import { apiConfig } from './config'
import { toApiError } from './errors'

/** Shared axios instance: base URL, 120 s timeout, every error normalized to ApiError. */
export const http = axios.create({ baseURL: `${apiConfig.baseUrl}/api/v1`, timeout: 120_000 })

http.interceptors.response.use(
  (res) => res,
  (err) => Promise.reject(toApiError(err)),
)
