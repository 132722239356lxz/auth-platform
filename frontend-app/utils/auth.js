/** Token 本地存储管理 (UniApp 版) */

const ACCESS_TOKEN_KEY = 'auth_access_token'
const REFRESH_TOKEN_KEY = 'auth_refresh_token'
const TOKEN_EXPIRE_KEY = 'auth_token_expire'
const USER_INFO_KEY = 'auth_user_info'

export function getAccessToken() {
  return uni.getStorageSync(ACCESS_TOKEN_KEY) || ''
}

export function getRefreshToken() {
  return uni.getStorageSync(REFRESH_TOKEN_KEY) || ''
}

export function setTokens(accessToken, refreshToken, expiresIn) {
  uni.setStorageSync(ACCESS_TOKEN_KEY, accessToken)
  if (refreshToken) {
    uni.setStorageSync(REFRESH_TOKEN_KEY, refreshToken)
  }
  if (expiresIn) {
    const expireTime = Date.now() + expiresIn * 1000
    uni.setStorageSync(TOKEN_EXPIRE_KEY, String(expireTime))
  }
}

export function clearTokens() {
  uni.removeStorageSync(ACCESS_TOKEN_KEY)
  uni.removeStorageSync(REFRESH_TOKEN_KEY)
  uni.removeStorageSync(TOKEN_EXPIRE_KEY)
  uni.removeStorageSync(USER_INFO_KEY)
}

export function isTokenExpired() {
  const expireTime = uni.getStorageSync(TOKEN_EXPIRE_KEY)
  if (!expireTime) return true
  return Date.now() > Number(expireTime) - 60000
}

export function setUserInfo(userInfo) {
  uni.setStorageSync(USER_INFO_KEY, JSON.stringify(userInfo))
}

export function getUserInfoFromStorage() {
  const raw = uni.getStorageSync(USER_INFO_KEY)
  return raw ? JSON.parse(raw) : null
}

/** 解析 JWT payload */
export function parseJwt(token) {
  try {
    const base64Url = token.split('.')[1]
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')

    // UniApp 环境兼容的 base64 解码
    let jsonPayload
    // #ifdef H5
    jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    )
    // #endif
    // #ifndef H5
    jsonPayload = base64ToJson(base64)
    // #endif

    return JSON.parse(jsonPayload)
  } catch (e) {
    return null
  }
}

/** 非 H5 环境的 base64 解码 */
function base64ToJson(base64) {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'
  let output = ''
  let str = base64.replace(/=+$/, '')
  for (let bc = 0, bs = 0, i = 0; i < str.length; i++) {
    const buffer = chars.indexOf(str[i])
    if (buffer === -1) continue
    bs = bc % 4 ? bs * 64 + buffer : buffer
    if (bc++ % 4) output += String.fromCharCode(255 & bs >> (-2 * bc & 6))
  }
  return decodeURIComponent(escape(output))
}
