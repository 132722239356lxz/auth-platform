/** Token 本地存储管理 */
const ACCESS_TOKEN_KEY = 'auth_access_token'
const REFRESH_TOKEN_KEY = 'auth_refresh_token'
const TOKEN_EXPIRE_KEY = 'auth_token_expire'
const USER_INFO_KEY = 'auth_user_info'

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function setTokens(accessToken: string, refreshToken?: string, expiresIn?: number): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  if (refreshToken) {
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
  }
  if (expiresIn) {
    const expireTime = Date.now() + expiresIn * 1000
    localStorage.setItem(TOKEN_EXPIRE_KEY, String(expireTime))
  }
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.removeItem(TOKEN_EXPIRE_KEY)
  localStorage.removeItem(USER_INFO_KEY)
  localStorage.removeItem('permissions')
}

export function isTokenExpired(aheadSeconds: number = 60): boolean {
  const expireTime = localStorage.getItem(TOKEN_EXPIRE_KEY)
  // 没有记录过期时间时，不判定为已过期（无法判定）
  if (!expireTime) return false
  return Date.now() > Number(expireTime) - aheadSeconds * 1000
}

export function getTokenExpireTime(): number {
  const expireTime = localStorage.getItem(TOKEN_EXPIRE_KEY)
  return expireTime ? Number(expireTime) : 0
}

export function setUserInfo(userInfo: any): void {
  localStorage.setItem(USER_INFO_KEY, JSON.stringify(userInfo))
}

export function getUserInfoFromStorage(): any {
  const raw = localStorage.getItem(USER_INFO_KEY)
  return raw ? JSON.parse(raw) : null
}

/** 解析 JWT payload */
export function parseJwt(token: string): any {
  try {
    const base64Url = token.split('.')[1]
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    )
    return JSON.parse(jsonPayload)
  } catch {
    return null
  }
}
