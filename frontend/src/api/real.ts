import axios, { isAxiosError } from 'axios'
import { readBlobText } from '@/shared/blob'
import { saveBlob } from '@/shared/download'
import { apiConfig, apiUrl, type ApiConfig } from './config'
import { ApiError, toApiError } from './errors'
import { http } from './http'
import { fallbackFileName, fileNameFromDisposition, type ApiService } from './service'
import type { ExportFormat, NestJob, NestRequest, UploadedFile } from './types'

/** Error bodies of blob requests arrive as Blob; read them back into the uniform error. */
export async function blobErrorToApiError(err: unknown): Promise<ApiError> {
  if (isAxiosError(err) && err.response?.data instanceof Blob) {
    try {
      const data: unknown = JSON.parse(await readBlobText(err.response.data))
      return toApiError(Object.assign(err, { response: { ...err.response, data } }))
    } catch {
      return toApiError(Object.assign(err, { response: { ...err.response, data: null } }))
    }
  }
  return toApiError(err)
}

export function createRealApi(config: ApiConfig = apiConfig): ApiService {
  const base = '/api/v1'
  const sheetPath = (jobId: string, i: number, format: ExportFormat) =>
    `${base}/nesting/jobs/${encodeURIComponent(jobId)}/sheets/${i}/${format}`

  return {
    isDemo: false,

    async uploadFile(file, onProgress) {
      const form = new FormData()
      form.append('files', file)
      const res = await http.post<UploadedFile[]>('/files', form, {
        onUploadProgress: (e) => {
          if (onProgress && e.total) onProgress(Math.round((e.loaded / e.total) * 100))
        },
      })
      const uploaded = res.data[0]
      if (!uploaded) {
        throw new ApiError({ code: 'INTERNAL_ERROR', message: 'Máy chủ không trả về thông tin file.' })
      }
      return uploaded
    },

    getPreviewUrl(fileId) {
      return apiUrl(`${base}/files/${encodeURIComponent(fileId)}/preview`, config)
    },

    async createJob(request: NestRequest) {
      return (await http.post<NestJob>('/nesting/jobs', request)).data
    },

    async getJob(jobId) {
      return (await http.get<NestJob>(`/nesting/jobs/${encodeURIComponent(jobId)}`)).data
    },

    async prioritizeTif(jobId, sheetIndex) {
      await http.post(`/nesting/jobs/${encodeURIComponent(jobId)}/sheets/${sheetIndex}/tif/prioritize`)
    },

    sheetDownloadUrl(jobId, sheetIndex, format) {
      return apiUrl(sheetPath(jobId, sheetIndex, format), config)
    },

    zipDownloadUrl(jobId, format) {
      return apiUrl(`${base}/nesting/jobs/${encodeURIComponent(jobId)}/export.zip?format=${format}`, config)
    },

    async downloadSheet(jobId, sheetIndex, format) {
      try {
        // Long timeout: a sheet is built on demand (spec 6.5: old 30 s timeout cut files short).
        const res = await axios.get<Blob>(apiUrl(sheetPath(jobId, sheetIndex, format), config), {
          responseType: 'blob',
          timeout: 30 * 60_000,
        })
        const name =
          fileNameFromDisposition(res.headers['content-disposition'] as string | undefined) ??
          fallbackFileName(jobId, sheetIndex, format)
        saveBlob(res.data, name)
      } catch (err) {
        throw await blobErrorToApiError(err)
      }
    },
  }
}
