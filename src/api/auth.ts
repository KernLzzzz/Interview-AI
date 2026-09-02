import { request } from './request'
import type { LoginResult } from '@/types'

export interface LoginPayload {
  username: string
  password: string
}

export interface RegisterPayload {
  username: string
  email: string
  password: string
}

/** 后端 UserVO（/users/profile 返回） */
export interface UserProfile {
  id: number
  username: string
  email: string
  nickname?: string
  phone?: string
  avatar?: string
  status?: number
}

/** 修改个人资料入参 */
export interface ProfilePayload {
  email: string
  phone?: string
  nickname?: string
  avatar?: string
}

/** 修改密码入参 */
export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
}

/** 用户登录 */
export function loginApi(payload: LoginPayload): Promise<LoginResult> {
  return request<LoginResult>({ url: '/users/login', method: 'POST', data: payload })
}

/** 用户注册（后端固定授予 candidate 角色） */
export function registerApi(payload: RegisterPayload): Promise<UserProfile> {
  return request<UserProfile>({ url: '/users/register', method: 'POST', data: payload })
}

/** 用户登出：删除 Redis 会话，token 立即失效 */
export function logoutApi(): Promise<null> {
  return request<null>({ url: '/users/logout', method: 'POST' })
}

/** 我的信息（需登录） */
export function profileApi(): Promise<UserProfile> {
  return request<UserProfile>({ url: '/users/profile', method: 'GET' })
}

/** 修改个人资料（用户名不可改） */
export function updateProfileApi(payload: ProfilePayload): Promise<UserProfile> {
  return request<UserProfile>({ url: '/users/profile', method: 'PUT', data: payload })
}

/** 修改密码（成功后后端删除该用户全部会话，强制重登） */
export function changePasswordApi(payload: ChangePasswordPayload): Promise<null> {
  return request<null>({ url: '/users/password', method: 'PUT', data: payload })
}
