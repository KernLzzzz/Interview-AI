import axios, { AxiosRequestConfig } from 'axios'

/** 后端统一返回体 { code, message, data }，code=200 表示成功 */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

const http = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截器：从 localStorage 取 token，携带 JWT
http.interceptors.request.use(config => {
  const token = localStorage.getItem('auth_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：解包统一返回体
http.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResult
    // HTTP 200 + code=200 → 直接返回业务数据
    if (res.code === 200) {
      return res.data as any
    }
    // HTTP 200 但业务失败（如密码错误）→ 抛错，交调用方提示，避免双弹 toast
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message
    // token 失效：清会话并回登录页（全局兜底）
    if (status === 401) {
      localStorage.removeItem('auth_token')
      localStorage.removeItem('auth_user')
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
    }
    return Promise.reject(new Error(message || '网络错误，请稍后重试'))
  }
)

/** 类型化请求：Promise 直接返回解包后的业务数据 */
export function request<T>(config: AxiosRequestConfig): Promise<T> {
  // 拦截器已解包统一返回体，这里强转业务数据类型
  return http.request<any, T>(config) as Promise<T>
}

export default http
