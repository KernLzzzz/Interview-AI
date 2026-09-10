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
    // 代理层错误（nginx 413/502/504）的响应体是 HTML，取不到统一返回体的 message，
    // 若直接落到最后的兜底，用户只能看到「网络错误」这种无法定位的提示。
    // 这里按状态码给出可读原因，避免把「文件太大」「服务重启」都混成一句话。
    if (!message) {
      if (error.code === 'ECONNABORTED') {
        return Promise.reject(new Error('请求超时，请检查网络后重试'))
      }
      if (status === 413) {
        return Promise.reject(new Error('上传内容超出服务端限制，请缩短时长或降低画质后重试'))
      }
      if (status === 502 || status === 503 || status === 504) {
        return Promise.reject(new Error('服务暂时不可用，请稍后重试'))
      }
      if (!error.response) {
        return Promise.reject(new Error('网络连接失败，请检查网络后重试'))
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
