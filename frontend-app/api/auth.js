import { get, post, del } from '@/utils/request.js'

const AUTH_BASE = '/auth-server'
const MESSAGE_BASE = '/auth-message'

// ==================== 密码登录 & 短信验证码登录 ====================

/**
 * 密码登录 —— 手机号/用户名 + 密码
 * POST /auth-server/api/auth/login
 * @param {object}  data                   登录参数
 * @param {string}  [data.phone]           手机号（与 username 二选一）
 * @param {string}  [data.username]        用户名（与 phone 二选一）
 * @param {string}  data.password          密码（明文或 RSA 加密后的 Base64）
 * @param {string}  [data.loginType]       登录方式，默认 PASSWORD
 * @param {string}  [data.clientId]        客户端 ID，默认 admin-web
 * @returns {Promise<{code, data: {access_token, refresh_token, expires_in, user_info}}>}
 */
export function loginByPassword(data) {
  return post(`${AUTH_BASE}/api/auth/login`, {
    ...data,
    loginType: data.loginType || 'PASSWORD',
    clientId: data.clientId || 'admin-web',
  })
}

/**
 * 短信验证码登录 —— 手机号 + 短信验证码
 * POST /auth-server/api/auth/login
 * @param {string} phone    手机号
 * @param {string} smsCode  短信验证码
 * @param {string} clientId 客户端 ID，默认 admin-web
 */
export function loginBySmsCode(phone, smsCode, clientId = 'admin-web') {
  return post(`${AUTH_BASE}/api/auth/login`, {
    phone,
    smsCode,
    loginType: 'SMS',
    clientId
  })
}

// ==================== 短信验证码 ====================

/**
 * 发送短信验证码
 * POST /auth-message/api/sms/send
 * @param {string} phone 手机号
 * @param {string} type  验证码类型：login / register / reset-password
 */
export function sendSmsCode(phone, type = 'login') {
  return post(`${MESSAGE_BASE}/api/sms/send`, { phone, type })
}

// ==================== 注册 & 用户信息 ====================

/**
 * 用户自助注册
 * POST /auth-server/api/register
 * @param {object} data  { phone, username?, password, email?, nickname? }
 */
export function register(data) {
  return post(`${AUTH_BASE}/api/register`, data)
}

/**
 * 获取扩展用户信息（含权限列表）
 * GET /auth-server/api/userinfo/extended
 */
export function getExtendedUserInfo() {
  return get(`${AUTH_BASE}/api/userinfo/extended`)
}

/**
 * 获取 OIDC 标准用户信息
 * GET /auth-server/userinfo
 */
export function getOidcUserInfo() {
  return get(`${AUTH_BASE}/userinfo`)
}

// ==================== Token 管理 ====================

/**
 * 刷新 Token
 * POST /auth-server/api/auth/refresh
 * @param {string} refreshToken refresh_token 值
 */
export function refreshTokenApi(refreshToken) {
  return post(`${AUTH_BASE}/api/auth/refresh`, { refresh_token: refreshToken })
}

/**
 * 退出登录（撤销当前 Token）
 * POST /auth-server/api/auth/logout
 */
export function logout() {
  return post(`${AUTH_BASE}/api/auth/logout`)
}

// ==================== OAuth2 授权码模式 ====================

/** OAuth2 授权码换取 Token */
export function exchangeToken(code, redirectUri) {
  const clientId = 'admin-web'
  const clientSecret = 'secret'

  // 使用 URLSearchParams 构建表单数据
  const formData = `grant_type=authorization_code&code=${encodeURIComponent(code)}&redirect_uri=${encodeURIComponent(redirectUri)}`

  return post(`${AUTH_BASE}/oauth2/token?${formData}`, null, {
    'Content-Type': 'application/x-www-form-urlencoded',
    'Authorization': `Basic ${btoa(`${clientId}:${clientSecret}`)}`
  })
}

/** 构建 OAuth2 授权 URL */
export function buildAuthorizeUrl() {
  // #ifdef H5
  const gateway = ''  // H5 通过代理
  // #endif
  // #ifndef H5
  const gateway = 'http://127.0.0.1:8080'
  // #endif

  const clientId = 'admin-web'
  const redirectUri = 'http://127.0.0.1:5174/login/callback'
  const scopes = 'openid profile'
  const base = `${gateway}/auth-server`

  return `${base}/oauth2/authorize?response_type=code&client_id=${clientId}&scope=${encodeURIComponent(scopes)}&redirect_uri=${encodeURIComponent(redirectUri)}`
}

// ==================== 微信小程序专属接口 ====================

/**
 * 微信小程序静默登录 —— 通过 wx.login() 获取的 code 换取 session
 * POST /auth-server/api/auth/wechat-login
 * @param {string} code     wx.login() 返回的临时 code
 * @param {string} clientId 客户端 ID
 * @returns {Promise<{access_token, refresh_token, expires_in, openid}>}
 */
export function wechatLogin(code, clientId = 'wechat-miniapp') {
  return post(`${AUTH_BASE}/api/auth/wechat-login`, {
    code,
    clientId,
    loginType: 'WECHAT'
  })
}

/**
 * 微信手机号授权登录 —— 通过 getPhoneNumber 获取的加密数据解密并登录
 * POST /auth-server/api/auth/wechat-phone
 * @param {string} code          wx.login() 返回的临时 code（用于获取 session_key）
 * @param {string} encryptedData getPhoneNumber 返回的 encryptedData
 * @param {string} iv            getPhoneNumber 返回的 iv
 * @param {string} clientId      客户端 ID
 * @returns {Promise<{access_token, refresh_token, expires_in, phone}>}
 */
export function wechatPhoneLogin(code, encryptedData, iv, clientId = 'wechat-miniapp') {
  return post(`${AUTH_BASE}/api/auth/wechat-phone`, {
    code,
    encryptedData,
    iv,
    clientId,
    loginType: 'WECHAT_PHONE'
  })
}

/**
 * 微信小程序 —— 绑定已有账号的手机号
 * POST /auth-server/api/auth/wechat-bind
 * @param {string} code          wx.login() code
 * @param {string} encryptedData getPhoneNumber 返回的 encryptedData
 * @param {string} iv            getPhoneNumber 返回的 iv
 */
export function wechatBindPhone(code, encryptedData, iv) {
  return post(`${AUTH_BASE}/api/auth/wechat-bind`, {
    code,
    encryptedData,
    iv
  })
}

// ==================== 审计 & 统计 ====================

/** 获取门户概览统计 */
export function getAuditDashboard() {
  return get(`${AUTH_BASE}/api/audit/dashboard`)
}

/** 子系统 Token 统计 */
export function getSubsystemStats() {
  return get(`${AUTH_BASE}/api/subsystem/stats`)
}

/** 获取当前用户的授权记录列表 */
export function getMyAuthorizations(limit = 20, offset = 0) {
  return get(`${AUTH_BASE}/api/audit/authorizations/me`, { limit, offset })
}

/** 主动吊销某个 Token */
export function revokeMyToken(tokenId) {
  return del(`${AUTH_BASE}/api/audit/revoke/${tokenId}`)
}
