import { get, post, put, del } from '@/utils/request'
import type {
  R, MessageSendRequest, MessageBroadcastRequest, MessageTemplateRequest,
  MessageTemplateVO, MessageTemplateQueryParams, MessageTemplatePageResult,
  MessageInfo, MessageQueryParams, MessagePageResult,
  SubsystemEvent, SubsystemIncident, SubsystemIncidentReport,
  SubsystemIncidentStats, SubsystemIncidentQuery,
  PortalMessageSendRequest
} from '@/types'

const MSG_BASE = '/auth-message'

// ==================== 消息发送 ====================

export function sendMessage(data: MessageSendRequest): Promise<R<string>> {
  return post(`${MSG_BASE}/api/message/send`, data)
}

export function broadcastMessage(data: MessageBroadcastRequest): Promise<R<string>> {
  return post(`${MSG_BASE}/api/message/broadcast`, data)
}

// ==================== 用户门户消息（普通用户自服务）====================

const PORTAL_MSG_BASE = `${MSG_BASE}/api/portal/message`

/** 发送站内信 */
export function sendPortalMessage(data: PortalMessageSendRequest): Promise<R<string>> {
  return post(`${PORTAL_MSG_BASE}/send`, {
    messageType: 'USER_MESSAGE',
    ...data,
    channels: ['IN_APP']
  } as MessageSendRequest)
}

/** 查询收件箱 */
export function getPortalInbox(limit?: number): Promise<R<MessageInfo[]>> {
  return get(`${PORTAL_MSG_BASE}/inbox`, { limit: limit || 50 })
}

/** 未读消息数 */
export function getPortalUnreadCount(): Promise<R<number>> {
  return get(`${PORTAL_MSG_BASE}/unread-count`)
}

/** 标记已读 */
export function markPortalMessageRead(messageId: string): Promise<R<void>> {
  return put(`${PORTAL_MSG_BASE}/${messageId}/read`)
}

// ==================== 消息查询 ====================

/** 分页条件查询消息列表 */
export function queryMessages(params: MessageQueryParams): Promise<R<MessagePageResult>> {
  return get(`${MSG_BASE}/api/message/list`, params)
}

/** 查询消息详情 */
export function getMessageDetail(messageId: string): Promise<R<MessageInfo>> {
  return get(`${MSG_BASE}/api/message/${messageId}`)
}

/** 收件箱(按接收人查询) */
export function getInbox(receiver: string, limit?: number): Promise<R<MessageInfo[]>> {
  return get(`${MSG_BASE}/api/message/inbox/${receiver}`, { limit: limit || 50 })
}

/** 未读消息数 */
export function getUnreadCount(receiver: string): Promise<R<number>> {
  return get(`${MSG_BASE}/api/message/unread-count/${receiver}`)
}

/** 标记已读 */
export function markMessageRead(messageId: string): Promise<R<void>> {
  return put(`${MSG_BASE}/api/message/${messageId}/read`)
}

/** 删除消息 */
export function deleteMessage(messageId: string): Promise<R<void>> {
  return del(`${MSG_BASE}/api/message/${messageId}`)
}

// ==================== 消息模板 ====================

/** 创建消息模板 */
export function createMessageTemplate(data: MessageTemplateRequest): Promise<R<number>> {
  return post(`${MSG_BASE}/api/message/template`, data)
}

/** 分页条件查询模板列表（联表统计使用次数） */
export function getMessageTemplates(params?: MessageTemplateQueryParams): Promise<R<MessageTemplatePageResult>> {
  return get(`${MSG_BASE}/api/message/template/list`, params)
}

/** 更新消息模板 */
export function updateMessageTemplate(templateCode: string, data: Partial<MessageTemplateVO>): Promise<R<void>> {
  return put(`${MSG_BASE}/api/message/template/${templateCode}`, data)
}

/** 删除消息模板 */
export function deleteMessageTemplate(templateCode: string): Promise<R<void>> {
  return del(`${MSG_BASE}/api/message/template/${templateCode}`)
}

// ==================== 子系统事件 ====================

/** 查询子系统事件列表 */
export function getSubsystemEvents(subsystem: string, limit?: number): Promise<R<SubsystemEvent[]>> {
  return get(`${MSG_BASE}/api/message/subsystem/${subsystem}/events`, { limit: limit || 50 })
}

/** 事件推送 */
export function pushEvent(eventType: string, title: string, content: string,
  sourceSystem: string, targetSubsystems: string[]): Promise<R<string>> {
  return post(`${MSG_BASE}/api/message/event/push`, null, {
    params: { eventType, title, content, sourceSystem, targetSubsystems }
  })
}

/** 查询指定业务的消息 */
export function getMessagesByBusinessId(businessId: string): Promise<R<MessageInfo[]>> {
  return get(`${MSG_BASE}/api/message/business/${businessId}`)
}

// ==================== 子系统异常反馈 ====================

/** 子系统异常上报 */
export function reportIncident(data: SubsystemIncidentReport): Promise<R<number>> {
  return post(`${MSG_BASE}/api/subsystem/incident/report`, data)
}

/** 分页查询异常列表 */
export function queryIncidents(params: SubsystemIncidentQuery): Promise<R<{ total: number; list: SubsystemIncident[] }>> {
  return get(`${MSG_BASE}/api/subsystem/incident/list`, params)
}

/** 异常统计(红点) */
export function getIncidentStats(): Promise<R<SubsystemIncidentStats>> {
  return get(`${MSG_BASE}/api/subsystem/incident/stats`)
}

/** 标记已处理 */
export function resolveIncident(id: number, resolver?: string, note?: string): Promise<R<void>> {
  return put(`${MSG_BASE}/api/subsystem/incident/${id}/resolve`, null, { resolver: resolver || 'admin', note })
}

/** 忽略异常 */
export function ignoreIncident(id: number, resolver?: string, note?: string): Promise<R<void>> {
  return put(`${MSG_BASE}/api/subsystem/incident/${id}/ignore`, null, { resolver: resolver || 'admin', note })
}

/** 删除异常记录 */
export function deleteIncident(id: number): Promise<R<void>> {
  return del(`${MSG_BASE}/api/subsystem/incident/${id}`)
}
