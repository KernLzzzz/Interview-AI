import { request } from './request'
import type { Question } from '@/types'

/** 后端题目 VO（列表/详情返回，屏蔽 expectedAnswer/keywords 答案字段） */
export interface QuestionVO {
  id: number
  scenarioId: number
  content: string
  type: string          // technical / behavioral / hr
  difficulty: number    // 1-3
  sortOrder?: number
  status: number        // 1-启用 0-停用
  createdAt: string
  updatedAt?: string
}

/** 后端创建/更新入参（QuestionDTO，无 status → 新建恒启用） */
export interface QuestionPayload {
  scenarioId: number
  content: string
  type: string          // technical / behavioral / hr
  difficulty: number
  expectedAnswer?: string
  keywords?: string
}

// 题型：后端类别 ↔ 前端中文
const TYPE_MAP_TO_ZH: Record<string, Question['type']> = {
  technical: '技术',
  behavioral: '行为',
  hr: 'HR面'
}
const TYPE_MAP_TO_EN: Record<Question['type'], string> = {
  '技术': 'technical',
  '行为': 'behavioral',
  'HR面': 'hr'
}

/** 后端 QuestionVO → 前端 Question */
export function voToQuestion(vo: QuestionVO): Question {
  return {
    id: vo.id,
    scenarioId: vo.scenarioId,
    content: vo.content,
    type: TYPE_MAP_TO_ZH[vo.type] ?? '技术',
    difficulty: vo.difficulty as Question['difficulty'],
    status: vo.status === 1 ? '启用' : '停用',
    createTime: vo.createdAt
  }
}

/** 前端 Question → 后端 QuestionDTO */
export function questionToPayload(q: Question): QuestionPayload {
  return {
    scenarioId: q.scenarioId,
    content: q.content,
    type: TYPE_MAP_TO_EN[q.type],
    difficulty: q.difficulty
  }
}

/** 题目列表（全量，客户端筛选/分页） */
export function listQuestionsApi(): Promise<QuestionVO[]> {
  return request<QuestionVO[]>({ url: '/questions', method: 'GET' })
}

/** 题目详情 */
export function getQuestionApi(id: number): Promise<QuestionVO> {
  return request<QuestionVO>({ url: `/questions/${id}`, method: 'GET' })
}

/** 新增题目（管理端） */
export function createQuestionApi(payload: QuestionPayload): Promise<QuestionVO> {
  return request<QuestionVO>({ url: '/questions', method: 'POST', data: payload })
}

/** 修改题目（管理端） */
export function updateQuestionApi(id: number, payload: QuestionPayload): Promise<QuestionVO> {
  return request<QuestionVO>({ url: `/questions/${id}`, method: 'PUT', data: payload })
}

/** 删除题目（管理端） */
export function deleteQuestionApi(id: number): Promise<null> {
  return request<null>({ url: `/questions/${id}`, method: 'DELETE' })
}
