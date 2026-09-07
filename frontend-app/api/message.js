import { get, post, put, del } from '@/utils/request.js'

const MSG_BASE = '/auth-message'

// ==================== 短信验证码 ====================

/**
 * 发送短信验证码
 * POST /auth-message/api/sms/send
 * @param {string} phone  手机号
 * @param {string} type   验证码类型：login / register / reset-password / bind
 */
export function sendSmsCode(phone, type = 'login') {
  return post(`${MSG_BASE}/api/sms/send`, { phone, type })
}

/**
 * 校验短信验证码
 * POST /auth-message/api/sms/verify
 * @param {string} phone 手机号
 * @param {string} code  验证码
 * @param {string} type  验证码类型
 */
export function verifySmsCode(phone, code, type = 'login') {
  return post(`${MSG_BASE}/api/sms/verify`, { phone, code, type })
}

// ==================== 消息发送 & 查询 ====================

export function sendMessage(data) {
  return post(`${MSG_BASE}/api/message/send`, data)
}

export function getInbox(receiver, limit) {
  return get(`${MSG_BASE}/api/message/inbox/${receiver}`, { limit })
}

export function getUnreadCount(receiver) {
  return get(`${MSG_BASE}/api/message/unread-count/${receiver}`)
}

export function markAsRead(messageId) {
  return put(`${MSG_BASE}/api/message/${messageId}/read`)
}

export function getMessageDetail(messageId) {
  return get(`${MSG_BASE}/api/message/${messageId}`)
}

/**
 * 分页查询消息列表
 * @param {object} params { keyword, messageType, status, page, size, ... }
 */
export function getMessageList(params = {}) {
  return get(`${MSG_BASE}/api/message/list`, params)
}

/**
 * 删除消息
 * @param {string} messageId 消息 ID
 */
export function deleteMessage(messageId) {
  return del(`${MSG_BASE}/api/message/${messageId}`)
}
