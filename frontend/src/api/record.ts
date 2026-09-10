import { request } from './request'
import type { InterviewRecord, AnswerQuestion, EvaluationTask, InterviewContext } from '@/types'

/** 后端 InterviewRecord 原始结构 */
export interface RecordVO {
  id: number
  userId: number
  scenarioId: number
  interviewMode?: 'text' | 'voice' | 'video'
  contextData?: string
  questionData?: string   // JSON: [{id, content, difficulty}]
  status: string          // pending/ongoing/completed/cancelled
  score: number | null
  duration?: number       // 秒
  mediaFileId?: number
  mediaDuration?: number
  answerData?: string
  aiFeedback?: string
  startedAt?: string
  completedAt?: string
  createdAt?: string
  updatedAt?: string
}

const STATUS_MAP_TO_ZH: Record<string, InterviewRecord['status']> = {
  pending: '待开始',
  ongoing: '进行中',
  completed: '已完成',
  cancelled: '已取消'
}

/** 后端 RecordVO → 前端 InterviewRecord（scenarioName 由调用方传入） */
export function voToRecord(vo: RecordVO, scenarioName: string): InterviewRecord {
  let questionData: AnswerQuestion[] | undefined
  let interviewContext: InterviewContext | undefined
  if (vo.questionData) {
    try {
      questionData = JSON.parse(vo.questionData)
    } catch {
      questionData = []
    }
  }
  if (vo.contextData) {
    try { interviewContext = JSON.parse(vo.contextData) as InterviewContext } catch { interviewContext = undefined }
  }
  return {
    id: vo.id,
    userId: vo.userId,
    scenarioId: vo.scenarioId,
    scenarioName,
    interviewMode: vo.interviewMode ?? 'text',
    interviewContext,
    questionData,
    status: STATUS_MAP_TO_ZH[vo.status] ?? '待开始',
    score: vo.score ?? null,
    duration: vo.duration != null ? Math.round(vo.duration / 60) : undefined,
    mediaFileId: vo.mediaFileId,
    mediaDuration: vo.mediaDuration,
    answerData: vo.answerData,
    aiFeedback: vo.aiFeedback,
    startedAt: vo.startedAt,
    completedAt: vo.completedAt,
    createdAt: vo.createdAt ?? ''
  }
}

/** 面试记录列表（admin 全部 / 其他人自己的，后端按权限返回） */
export function listRecordsApi(): Promise<RecordVO[]> {
  return request<RecordVO[]>({ url: '/records', method: 'GET' })
}

/** 创建面试（pending） */
export function createRecordApi(
  scenarioId: number,
  interviewMode: 'text' | 'voice' | 'video',
  context?: Partial<InterviewContext>
): Promise<RecordVO> {
  return request<RecordVO>({ url: '/records', method: 'POST', data: { scenarioId, interviewMode, ...context } })
}

/** 开始面试（pending → ongoing，后端抽题写 questionData） */
export function startRecordApi(id: number): Promise<RecordVO> {
  return request<RecordVO>({ url: `/records/${id}/start`, method: 'POST' })
}

/** 提交答案（ongoing → completed，触发异步 AI 评测） */
export function submitRecordApi(
  id: number,
  answers: Array<{ questionId: number; answer: string }>,
  media?: { mediaFileId: number; mediaDuration: number }
): Promise<EvaluationTask> {
  return request<EvaluationTask>({ url: `/records/${id}/submit`, method: 'POST', data: { answers, ...media } })
}

export interface EvaluationMetrics {
  sampleSize: number
  success: number
  failed: number
  inFlight: number
  successRate: number
  averageLatencyMs: number
  p95LatencyMs: number
  retryRate: number
  remoteAiTasks: number
  localTasks: number
}

export function getLatestEvaluationApi(recordId: number): Promise<EvaluationTask> {
  return request<EvaluationTask>({ url: `/evaluations/records/${recordId}/latest`, method: 'GET' })
}

export function getEvaluationApi(taskId: number): Promise<EvaluationTask> {
  return request<EvaluationTask>({ url: `/evaluations/${taskId}`, method: 'GET' })
}

export function getEvaluationMetricsApi(): Promise<EvaluationMetrics> {
  return request<EvaluationMetrics>({ url: '/evaluations/metrics', method: 'GET' })
}

/** 取消面试（pending → cancelled） */
export function cancelRecordApi(id: number): Promise<null> {
  return request<null>({ url: `/records/${id}/cancel`, method: 'POST' })
}

/** 面试官/管理员人工评分（覆盖 AI 分，评语 merge 进 aiFeedback） */
export function scoreRecordApi(id: number, data: { score: number; comment?: string }): Promise<null> {
  return request<null>({ url: `/admin/records/${id}/score`, method: 'POST', data })
}
