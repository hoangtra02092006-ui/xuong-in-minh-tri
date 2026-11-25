import { apiConfig } from './config'
import { createMockApi } from './mock'
import { createRealApi } from './real'
import type { ApiService } from './service'

/** The one API the app uses: mock in demo mode (spec 5.8), real backend otherwise. */
export const api: ApiService = apiConfig.useMock ? createMockApi() : createRealApi()

export { ApiError } from './errors'
export type { ApiService } from './service'
