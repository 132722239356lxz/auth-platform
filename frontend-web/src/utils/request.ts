import axios, { type AxiosInstance, type InternalAxiosRequestConfig, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import NProgress from 'nprogress'
import { getAccessToken, getRefreshToken, setTokens, clearTokens, parseJwt, isTokenExpired } from './auth'
import { logRequest, logResponse } from './devLog'
import type { ApiResponse, LoginResponse } from '@/types'

NProgress.configure({ showSpinner: false })

/** 是否正在刷新 Token */
let isRefreshing = false
/** 等待刷新完成的请求队列（带 resolve 回调） */
let pendingRequests: Array<{
  resolve: (token: string) => void
  reject: (error: any) => void
}> = []

/** 提前刷新阈值：token 剩余有效时间少于此值（秒）时主动刷新 */
const REFRESH_AHEAD_SECONDS = 300 // 5 分钟

/**
 * 登录初始化进行中的全局标记。
 * 在登录后拉取用户信息/菜单期间，若后端返回 401，禁止清除 token（避免刚登录成功又被踢回登录页）。
 */
export function isLoginInitializing(): boolean {
  return !!(window as any).__loginInitializing
}

export function setLoginInitializing(val: boolean) {
  ;(window as any).__loginInitializing = val
}

const service: AxiosInstance = axios.create({
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

// ==================== 请求拦截器 ====================
service.interceptors.request.use(
  async (config: InternalAxiosRequestConfig) => {
    NProgress.start()
    ;(config as any).startTime = Date.now()

    // FormData 上传时删除默认的 application/json，让浏览器自动设置 multipart/form-data
    if (config.data instanceof FormData) {
      delete (config.headers as any)['Content-Type']
    }

    let token = getAccessToken()

    // 如果当前有 token，且不是刷新/登录接口，检查是否快过期
    if (token && !config.url?.includes('/api/auth/refresh') && !config.url?.includes('/api/auth/login')) {
      if (isTokenExpired(REFRESH_AHEAD_SECONDS)) {
        // Token 快过期了，先刷新再发送请求
        try {
          const newToken = await refreshTokenInternal()
          token = newToken
        } catch {
          // 刷新失败，token 可能完全过期，让请求带旧 token 去，由响应 401 处理
        }
      }
    }

    if (token && !config.headers.Authorization) {
      config.headers.Authorization = `Bearer ${token}`
    }
    // auth-flow 接口需要 X-User 头
    if (config.url?.includes('/auth-flow/')) {
      const jwt = token ? parseJwt(token) : null
      if (jwt?.sub) {
        config.headers['X-User'] = jwt.sub
      }
    }
    logRequest(config)
    return config
  },
  error => {
    NProgress.done()
    return Promise.reject(error)
  }
)

// ==================== 响应拦截器 ====================
service.interceptors.response.use(
  async (response: AxiosResponse) => {
    NProgress.done()
    const duration = (response.config as any).startTime
      ? Date.now() - (response.config as any).startTime
      : undefined
    logResponse(response, duration)
    const data = response.data

    // OAuth2 token 响应直接返回
    if (response.config.url?.includes('/oauth2/token')) {
      return data
    }

    // 二进制文件直接返回
    if (response.config.responseType === 'blob') {
      return response
    }

    // 统一处理 ApiResponse / R 格式
    if (data && typeof data === 'object' && 'code' in data) {
      const code = data.code
      const msg = data.message || data.msg || '操作失败'

      if (code === 200) {
        return data
      }

      if (code === 401) {
        // 登录接口本身返回 401，说明用户名/密码错误，只显示错误，不触发 token 过期流程
        if (response.config.url?.includes('/api/auth/login')) {
          ElMessage.error(msg)
          return Promise.reject(new Error(msg))
        }
        // 尝试刷新 token 并重试原请求（未重试过且非刷新接口本身）
        if (!(response.config as any)._retry && !response.config.url?.includes('/api/auth/refresh')) {
          return retryWithNewToken(response.config)
        }
        // 已经重试过或刷新接口本身返回 401，说明 refresh_token 也过期了
        handleTokenExpired()
        return Promise.reject(new Error(msg))
      }

      if (code === 403) {
        ElMessage.error(`权限不足: ${msg}`)
        return Promise.reject(new Error(msg))
      }

      ElMessage.error(msg)
      return Promise.reject(new Error(msg))
    }

    return data
  },
  async error => {
    NProgress.done()
    const { response } = error

    if (response) {
      logResponse(response)
      switch (response.status) {
        case 401:
          // 登录接口的 HTTP 401 只显示登录失败，不触发 token 过期流程
          if (response.config.url?.includes('/api/auth/login')) {
            ElMessage.error(response.data?.message || '登录失败')
          } else if (!(response.config as any)._retry && !response.config.url?.includes('/api/auth/refresh')) {
            // 尝试刷新 token 并重试原请求
            return retryWithNewToken(response.config)
          } else {
            // 已经重试过或刷新接口本身返回 401，说明 refresh_token 也过期了
            handleTokenExpired()
          }
          break
        case 403:
          ElMessage.error('权限不足，无法访问')
          break
        case 404:
          ElMessage.error('请求资源不存在')
          break
        case 503:
          ElMessage.error(response.data?.message || '服务暂时不可用，请稍后重试')
          break
        case 500:
          ElMessage.error(response.data?.message || '服务器内部错误')
          break
        default:
          ElMessage.error(response.data?.message || `请求失败 (${response.status})`)
      }
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请检查网络')
    } else {
      ElMessage.error('网络异常，请检查连接')
    }

    return Promise.reject(error)
  }
)

/** 刷新 Token — 可复用的核心函数，返回新的 accessToken */
async function refreshTokenInternal(): Promise<string> {
  const storedRefreshToken = getRefreshToken()

  if (!storedRefreshToken) {
    throw new Error('No refresh token available')
  }

  // 已有刷新进行中，等待其完成
  if (isRefreshing) {
    return new Promise<string>((resolve, reject) => {
      pendingRequests.push({ resolve, reject })
    })
  }

  isRefreshing = true

  try {
    const res = await axios.post('/auth-server/api/auth/refresh', {
      refresh_token: storedRefreshToken
    })

    const data = res.data as ApiResponse<LoginResponse>
    if (data.code === 200 && data.data) {
      const { access_token, refresh_token: newRefreshToken, expires_in } = data.data
      setTokens(access_token, newRefreshToken, expires_in)

      // 通知所有等待中的请求
      const queue = pendingRequests.slice()
      pendingRequests = []
      queue.forEach(({ resolve }) => resolve(access_token))

      return access_token
    } else {
      const queue = pendingRequests.slice()
      pendingRequests = []
      queue.forEach(({ reject }) => reject(new Error('Token refresh failed')))
      throw new Error('Token refresh failed')
    }
  } catch (error) {
    const queue = pendingRequests.slice()
    pendingRequests = []
    queue.forEach(({ reject }) => reject(error))
    throw error
  } finally {
    isRefreshing = false
  }
}

/** 刷新 token 并重试原请求，返回重试后的响应 */
async function retryWithNewToken(config: InternalAxiosRequestConfig): Promise<any> {
  ;(config as any)._retry = true
  try {
    await refreshTokenInternal()
    // 用新 token 更新请求头并重试
    const newToken = getAccessToken()
    if (newToken) {
      config.headers.Authorization = `Bearer ${newToken}`
    }
    return service(config)
  } catch {
    // 登录初始化阶段不清除 token——刚登录成功就被踢回登录页是用户体验灾难
    if (isLoginInitializing()) {
      console.warn('[Request] 登录初始化中，跳过 token 清除')
      return Promise.reject(new Error('Login initialization request failed'))
    }
    // 刷新失败，说明 refresh_token 也过期了，跳转登录
    clearTokens()
    redirectToLogin()
    return Promise.reject(new Error('Token 刷新失败，请重新登录'))
  }
}

/** Token 过期处理 - 自动刷新或跳转登录（无重试，用于已重试过仍失败的情况） */
async function handleTokenExpired() {
  try {
    await refreshTokenInternal()
  } catch {
    // 登录初始化阶段不清除 token
    if (isLoginInitializing()) {
      console.warn('[Request] 登录初始化中，跳过 token 清除')
      return
    }
    clearTokens()
    redirectToLogin()
  }
}

/** 跳转到登录页 —— 管理后台与用户门户完全分离 */
function redirectToLogin() {
  clearTokens()
  const pathname = window.location.pathname
  // 根据当前路径前缀判断应该跳转到哪个登录页
  const loginPath = pathname.startsWith('/portal') ? '/login' : '/adminLogin'
  if (pathname === loginPath || pathname === '/adminLogin' ||
      pathname === '/login' || pathname.startsWith('/login/')) {
    return
  }
  ElMessageBox.confirm('登录状态已过期，是否重新登录？', '提示', {
    confirmButtonText: '重新登录',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    window.location.href = loginPath
  }).catch(() => {})
}

/** 通用请求方法 */
export function request<T = any>(config: AxiosRequestConfig): Promise<T> {
  return service(config) as Promise<T>
}

export function get<T = any>(url: string, params?: any, extraConfig?: any): Promise<T> {
  return request<T>({ url, method: 'GET', params, ...extraConfig })
}

export function post<T = any>(url: string, data?: any, extraConfig?: any): Promise<T> {
  console.log(url);
  return request<T>({ url, method: 'POST', data, ...extraConfig })
}

export function put<T = any>(url: string, data?: any, params?: any): Promise<T> {
  return request<T>({ url, method: 'PUT', data, params })
}

export function del<T = any>(url: string, params?: any): Promise<T> {
  return request<T>({ url, method: 'DELETE', params })
}

export default service
