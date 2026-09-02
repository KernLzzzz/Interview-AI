import { request } from './request'

/** 近 7 天单日趋势点 */
export interface TrendPoint {
  date: string       // yyyy-MM-dd
  count: number      // 当天创建的面试数
  avgScore: number | null
}

/** 最近一条面试记录（仪表盘展示用） */
export interface RecentRecord {
  id: number
  scenarioId: number
  status: string     // pending/ongoing/completed/cancelled
  score: number | null
  createdAt: string
}

/** 仪表盘概览（GET /stats/overview，按角色分数据） */
export interface StatsOverview {
  total: number
  pending: number
  ongoing: number
  completed: number
  cancelled: number
  avgScore: number | null
  passRate: number | null
  completedToday: number
  totalCandidates: number
  trend: TrendPoint[]
  recent: RecentRecord[]
}

export function getStatsApi(): Promise<StatsOverview> {
  return request<StatsOverview>({ url: '/stats/overview', method: 'GET' })
}
