import { request } from './request'

export interface UploadedFile {
  fileId: number
  url: string
  originalName: string
}

export function uploadInterviewMedia(
  file: File,
  mode: 'voice' | 'video',
  onProgress?: (percent: number) => void
): Promise<UploadedFile> {
  const data = new FormData()
  data.append('file', file)
  return request<UploadedFile>({
    url: `/files/upload?module=interview-${mode === 'video' ? 'video' : 'audio'}`,
    method: 'POST',
    data,
    timeout: 180_000,
    onUploadProgress: event => {
      if (event.total) onProgress?.(Math.round(event.loaded * 100 / event.total))
    }
  })
}
