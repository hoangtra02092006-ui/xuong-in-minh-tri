import { api as defaultApi } from '@/api'
import type { ApiService } from '@/api/service'

let current: ApiService = defaultApi

/** The API the stores call; tests swap in a mock with controlled time. */
export function getApi(): ApiService {
  return current
}

export function setApi(next: ApiService): void {
  current = next
}
