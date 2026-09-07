import { getAccessToken, getRefreshToken, isTokenExpired, clearTokens, parseJwt } from './auth.js'

/** 网关基础地址 - H5 通过代理，其他环境直连 */
// #ifdef H5
const BASE_URL = ''
// #endif
// #ifndef H5
const BASE_URL = 'http://127.0.0.1:8080'
// #endif

/** 请求超时时间 */
const TIMEOUT = 30000

/** 是否正在刷新 Token */
let isRefreshing = false

/** 通用请求方法 */
function request(options) {
  return new Promise((resolve, reject) => {
    const token = getAccessToken()
    const header = {
      'Content-Type': 'application/json',
      ...options.header
    }

    if (token) {
      header['Authorization'] = `Bearer ${token}`
    }

    // auth-flow 接口需要 X-User 头
    if (options.url && options.url.includes('/auth-flow/')) {
      const jwt = token ? parseJwt(token) : null
      if (jwt && jwt.sub) {
        header['X-User'] = jwt.sub
      }
    }

    const url = options.url.startsWith('http') ? options.url : `${BASE_URL}${options.url}`

    uni.request({
      url,
      method: options.method || 'GET',
      data: options.data,
      params: options.params,
      header,
      timeout: TIMEOUT,
      success: (res) => {
        const data = res.data

        // OAuth2 token 响应直接返回
        if (options.url && options.url.includes('/oauth2/token')) {
          resolve(data)
          return
        }

        // 统一处理响应
        if (data && typeof data === 'object' && 'code' in data) {
          const code = data.code
          const msg = data.message || data.msg || '操作失败'

          if (code === 200) {
            resolve(data)
            return
          }

          if (code === 401) {
            handleTokenExpired()
            reject(new Error(msg))
            return
          }

          if (code === 403) {
            uni.showToast({ title: `权限不足: ${msg}`, icon: 'none' })
            reject(new Error(msg))
            return
          }

          uni.showToast({ title: msg, icon: 'none' })
          reject(new Error(msg))
          return
        }

        resolve(data)
      },
      fail: (err) => {
        if (err.errMsg && err.errMsg.includes('timeout')) {
          uni.showToast({ title: '请求超时', icon: 'none' })
        } else {
          uni.showToast({ title: '网络异常', icon: 'none' })
        }
        reject(err)
      }
    })
  })
}

/** Token 过期处理 */
function handleTokenExpired() {
  if (isRefreshing) return
  isRefreshing = true
  clearTokens()
  isRefreshing = false

  uni.showModal({
    title: '提示',
    content: '登录状态已过期，是否重新登录？',
    confirmText: '重新登录',
    success: (res) => {
      if (res.confirm) {
        uni.reLaunch({ url: '/pages/login/index' })
      }
    }
  })
}

/** GET 请求 */
export function get(url, params, extraHeader) {
  return request({ url, method: 'GET', params, header: extraHeader })
}

/** POST 请求 */
export function post(url, data, extraHeader) {
  return request({ url, method: 'POST', data, header: extraHeader })
}

/** PUT 请求 */
export function put(url, data, params) {
  return request({ url, method: 'PUT', data, params })
}

/** DELETE 请求 */
export function del(url, params) {
  return request({ url, method: 'DELETE', params })
}

export default { request, get, post, put, del }
