import { request } from './request'

export interface UploadedFile {
  fileId: number
  url: string
  originalName: string
}

/** 与后端 app.file.max-size 保持一致（见 application.yml） */
const MAX_UPLOAD_BYTES = 200 * 1024 * 1024

export function uploadInterviewMedia(
  file: File,
  mode: 'voice' | 'video',
  onProgress?: (percent: number) => void
): Promise<UploadedFile> {
  // 本地预检：超限时立刻给出可读原因，不必等把几十 MB 传完才被服务端拒绝
  if (file.size > MAX_UPLOAD_BYTES) {
    const actual = (file.size / 1024 / 1024).toFixed(1)
    const limit = Math.round(MAX_UPLOAD_BYTES / 1024 / 1024)
    return Promise.reject(new Error(`录制文件 ${actual}MB，超过 ${limit}MB 上限，请缩短面试时长`))
  }
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
