import { request } from './request'
import type { User } from '@/types'

/** 后端管理端用户 VO（不含角色、不含密码） */
export interface AdminUserVO {
  id: number
  username: string
  email: string
  nickname?: string
  phone?: string
  avatar?: string
  status: number       // 1-正常 0-禁用
  createdAt: string
}

/** 后端分页结构（MyBatis-Plus IPage） */
export interface AdminUserPage {
  records: AdminUserVO[]
  total: number
  size: number
  current: number
  pages: number
}

/** 后端角色（Role） */
export interface Role {
  id: number
  name: string
  code: string
  description?: string
}

/** 后端 AdminUserVO → 前端 User */
export function voToUser(vo: AdminUserVO): User {
  return {
    id: vo.id,
    username: vo.username,
    email: vo.email,
    nickname: vo.nickname,
    phone: vo.phone,
    avatar: vo.avatar,
    status: vo.status === 1 ? '正常' : '禁用',
    createdAt: vo.createdAt
  }
}

/** 用户列表（全量拉取，客户端筛选/分页/排序） */
export function listUsersApi(current = 1, size = 1000): Promise<AdminUserPage> {
  return request<AdminUserPage>({ url: '/admin/users', method: 'GET', params: { current, size } })
}

/** 角色列表（分配角色弹窗勾选项） */
export function listRolesApi(): Promise<Role[]> {
  return request<Role[]>({ url: '/admin/roles', method: 'GET' })
}

/** 给用户分配角色（先删后插，覆盖式） */
export function assignRolesApi(userId: number, roleIds: number[]): Promise<null> {
  return request<null>({ url: `/admin/users/${userId}/role`, method: 'PUT', data: roleIds })
}
