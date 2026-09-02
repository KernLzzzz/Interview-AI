import { request } from './request'

/** 后端场景（Scenario） */
export interface Scenario {
  id: number
  name: string
  description?: string
  techField?: string
  difficulty?: number      // 1-初级 2-中级 3-高级
  coverImage?: string
  questionCount?: number
  sortOrder?: number
  status?: number          // 1-上架 0-下架
  createdAt?: string
}

/** 场景列表（候选人选场景/管理端维护共用） */
export function listScenariosApi(): Promise<Scenario[]> {
  return request<Scenario[]>({ url: '/scenarios', method: 'GET' })
}

/** 新增场景（管理端） */
export function createScenarioApi(data: Partial<Scenario>): Promise<Scenario> {
  return request<Scenario>({ url: '/scenarios', method: 'POST', data })
}

/** 修改场景（管理端） */
export function updateScenarioApi(id: number, data: Partial<Scenario>): Promise<Scenario> {
  return request<Scenario>({ url: `/scenarios/${id}`, method: 'PUT', data })
}

/** 删除场景（管理端） */
export function deleteScenarioApi(id: number): Promise<null> {
  return request<null>({ url: `/scenarios/${id}`, method: 'DELETE' })
}
