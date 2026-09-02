import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { AuthUser, LoginForm, RegisterForm } from '@/types'
import { loginApi, registerApi, logoutApi, profileApi, updateProfileApi } from '@/api/auth'

// 后端角色编码 → 前端中文角色（登录返回的 roles[0]）
const ROLE_MAP: Record<string, AuthUser['role']> = {
  admin: '管理员',
  interviewer: '面试官',
  candidate: '候选人'
}

export const useAuthStore = defineStore('auth', () => {
  // ===================== State =====================
  const currentUser = ref<AuthUser | null>(null)
  const token = ref<string>('')
  const loading = ref(false)

  // ===================== Computed =====================
  const isLoggedIn = computed(() => !!token.value && !!currentUser.value)
  const userRole = computed(() => currentUser.value?.role ?? '')
  const username = computed(() => currentUser.value?.username ?? '')

  // ===================== 角色映射 =====================
  function mapRole(roles?: string[]): AuthUser['role'] {
    return ROLE_MAP[roles?.[0] ?? ''] ?? '候选人'
  }

  // ===================== 初始化（从本地存储恢复会话） =====================
  async function restoreSession() {
    const savedToken = localStorage.getItem('auth_token')
    if (!savedToken) return
    token.value = savedToken
    // 先用缓存同步恢复会话，保证路由守卫/首屏渲染不被异步校验拖慢而误跳登录页
    const cached = JSON.parse(localStorage.getItem('auth_user') || 'null') as AuthUser | null
    if (cached) currentUser.value = cached
    try {
      // 后台用真实接口校验 token 并刷新用户信息；token 失效由拦截器统一清会话跳登录
      const profile = await profileApi()
      currentUser.value = {
        id: profile.id,
        username: profile.username,
        email: profile.email,
        role: currentUser.value?.role ?? '候选人',
        avatar: profile.avatar || currentUser.value?.avatar,
        token: savedToken
      }
      localStorage.setItem('auth_user', JSON.stringify(currentUser.value))
    } catch {
      logout()
    }
  }

  // ===================== 注册 =====================
  async function register(form: RegisterForm): Promise<{ success: boolean; message: string }> {
    loading.value = true
    try {
      // 后端注册只收 username/email/password，角色固定 candidate
      await registerApi({ username: form.username, email: form.email, password: form.password })
      loading.value = false
      return { success: true, message: '注册成功，请登录' }
    } catch (e) {
      loading.value = false
      return { success: false, message: e instanceof Error ? e.message : '注册失败' }
    }
  }

  // ===================== 登录 =====================
  async function login(form: LoginForm): Promise<{ success: boolean; message: string }> {
    loading.value = true
    try {
      const data = await loginApi({ username: form.username, password: form.password })
      token.value = data.token
      localStorage.setItem('auth_token', data.token)

      // 拉取个人资料（含 email/avatar），组装前端用户信息
      const profile = await profileApi()
      const authUser: AuthUser = {
        id: data.userId,
        username: data.username,
        email: profile.email,
        role: mapRole(data.roles),
        avatar: profile.avatar,
        token: data.token
      }
      currentUser.value = authUser
      localStorage.setItem('auth_user', JSON.stringify(authUser))

      loading.value = false
      return { success: true, message: `欢迎回来，${data.username}！` }
    } catch (e) {
      loading.value = false
      return { success: false, message: e instanceof Error ? e.message : '登录失败' }
    }
  }

  // ===================== 修改个人资料 =====================
  async function updateProfile(data: { email: string; phone?: string; nickname?: string; avatar?: string }) {
    try {
      const profile = await updateProfileApi(data)
      if (currentUser.value) {
        currentUser.value = {
          ...currentUser.value,
          email: profile.email,
          avatar: profile.avatar ?? currentUser.value.avatar
        }
        localStorage.setItem('auth_user', JSON.stringify(currentUser.value))
      }
      return { success: true as const, message: '资料已更新' }
    } catch (e) {
      return { success: false as const, message: e instanceof Error ? e.message : '更新失败' }
    }
  }

  // ===================== 退出登录 =====================
  async function logout() {
    try {
      await logoutApi() // 后端删 Redis 会话；token 仍在 localStorage，能带上 Authorization
    } catch {
      // 登出失败不阻塞本地清理
    }
    currentUser.value = null
    token.value = ''
    localStorage.removeItem('auth_user')
    localStorage.removeItem('auth_token')
  }

  return {
    currentUser,
    token,
    loading,
    isLoggedIn,
    userRole,
    username,
    restoreSession,
    register,
    login,
    updateProfile,
    logout
  }
})
