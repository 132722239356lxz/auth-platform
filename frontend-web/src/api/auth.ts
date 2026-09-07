import { get, post, put, del } from '@/utils/request'
import type {
  LoginRequest, LoginResponse, RefreshTokenRequest,
  UserRegisterRequest, UserRegisterResponse,
  ExtendedUserInfoResponse, PermissionsResponse,
  ClientInfo, ClientRequest, SecretResetRequest,
  SubsystemInfo, SubsystemRequest, SubsystemNavItem,
  SubsystemTokenResponse,
  AuthorizationRecord, RevokeLogRequest, RevokeLogResponse, DashboardStats,
  RequestLogEntry, PageResult, TokenInfo, ClientPageQuery, SubsystemTokenPageQuery, AuditPageQuery,
  ApiResponse
} from '@/types'

const AUTH_BASE = '/auth-server'
const SYSTEM_BASE = '/system-server'

// ==================== 认证接口 ====================

/** 密码登录 */
export function login(data: LoginRequest): Promise<ApiResponse<LoginResponse>> {
  return post(`${AUTH_BASE}/api/auth/login`, data)
}

/** 刷新 Token */
export function refreshTokenApi(data: RefreshTokenRequest): Promise<ApiResponse<LoginResponse>> {
  return post(`${AUTH_BASE}/api/auth/refresh`, data)
}

/** 用户注册 */
export function registerUser(data: UserRegisterRequest): Promise<ApiResponse<UserRegisterResponse>> {
  return post(`${AUTH_BASE}/api/register`, data)
}

/**
 * 初始化 Session（SSO 跳转前调用）
 * Axios 自动携带 Bearer token → 后端验证 → 创建 HttpSession → 浏览器存储 AUTH_SESSION cookie
 * 调用后 window.open /oauth2/authorize 即可携带 session 认证信息
 */
export function initSession(): Promise<ApiResponse<{ sessionId: string; principal: string }>> {
  return post(`${AUTH_BASE}/api/auth/init-session`)
}

// ==================== OAuth2 Token 交换（SSO 登录用） ====================

/**
 * 授权码换 Token（服务端代理，不暴露 client_secret）
 *
 * 安全说明：本方法调用后端 /api/auth/exchange-code 端点，由服务端持有 client_secret
 * 并代为请求 /oauth2/token，前端不接触任何密钥。
 *
 * 此前存在的 exchangeToken / oauth2RefreshToken 会在前端拼接 client_secret 做 Basic 认证，
 * 由于 VITE_ 变量会随构建产物公开，等同于把客户端密钥发布到公网，故已移除。
 * Token 刷新统一走服务端代理接口 /api/auth/refresh（见 utils/request.ts）。
 */
export async function exchangeCode(code: string, state: string, redirectUri: string): Promise<TokenInfo> {
  const res: any = await post(`${AUTH_BASE}/api/auth/exchange-code`, { code, state, redirectUri })
  return res.data  // ApiResponse<TokenInfo>.data → TokenInfo
}

// ==================== OIDC 用户信息 ====================

/** 获取扩展用户信息（含权限） */
export function getExtendedUserInfo(): Promise<ApiResponse<ExtendedUserInfoResponse>> {
  return get(`${AUTH_BASE}/api/userinfo/extended`)
}

/** 获取用户权限列表 */
export function getUserPermissions(): Promise<ApiResponse<PermissionsResponse>> {
  return get(`${AUTH_BASE}/api/userinfo/permissions`)
}

// ==================== 客户端管理 (已迁移至 system-server) ====================

export function getClients(): Promise<ApiResponse<ClientInfo[]>> {
  return get(`${SYSTEM_BASE}/api/clients`)
}

/** 查询当前用户门户可见的客户端列表(无需 system:client:list 管理权限) */
export function getPortalClients(): Promise<ApiResponse<ClientInfo[]>> {
  return get(`${SYSTEM_BASE}/api/clients/portal`)
}

/** 分页查询客户端 */
export function getClientsPage(params: ClientPageQuery): Promise<ApiResponse<PageResult<ClientInfo>>> {
  return get(`${SYSTEM_BASE}/api/clients/page`, params)
}

export function getClientById(id: string): Promise<ApiResponse<ClientInfo>> {
  return get(`${SYSTEM_BASE}/api/clients/${id}`)
}

export function createClient(data: ClientRequest): Promise<ApiResponse<ClientInfo>> {
  return post(`${SYSTEM_BASE}/api/clients`, data)
}

export function updateClient(id: string, data: ClientRequest): Promise<ApiResponse<ClientInfo>> {
  return put(`${SYSTEM_BASE}/api/clients/${id}`, data)
}

export function deleteClient(id: string): Promise<ApiResponse<void>> {
  return del(`${SYSTEM_BASE}/api/clients/${id}`)
}

export function resetClientSecret(id: string, data: SecretResetRequest): Promise<ApiResponse<ClientInfo>> {
  return put(`${SYSTEM_BASE}/api/clients/${id}/secret`, data)
}

export function toggleClientStatus(id: string, enabled: boolean): Promise<ApiResponse<ClientInfo>> {
  const url = enabled
    ? `${SYSTEM_BASE}/api/clients/${id}/enable`
    : `${SYSTEM_BASE}/api/clients/${id}/disable`
  return put(url)
}

export function getCryptoInfo(): Promise<ApiResponse<{ currentAlgorithm: string; supportedAlgorithms: string }>> {
  return get(`${SYSTEM_BASE}/api/clients/crypto-info`)
}

// ==================== 子系统 Token 管理 (已迁移至 system-server) ====================

/** 分页查询子系统 Token */
export function getSubsystemTokensPage(params: SubsystemTokenPageQuery): Promise<ApiResponse<PageResult<SubsystemTokenResponse>>> {
  return get(`${SYSTEM_BASE}/api/subsystem/tokens/page`, params)
}

export function getSubsystemTokens(params: {
  clientId?: string; username?: string; limit?: number; offset?: number
}): Promise<ApiResponse<PageResult<SubsystemTokenResponse>>> {
  return get(`${SYSTEM_BASE}/api/subsystem/tokens`, params)
}

export function getActiveSubsystemTokens(params: {
  clientId?: string; username?: string
}): Promise<ApiResponse<SubsystemTokenResponse[]>> {
  return get(`${SYSTEM_BASE}/api/subsystem/tokens/active`, params)
}

export function getSubsystemTokensByClient(clientId: string, params?: {
  limit?: number; offset?: number
}): Promise<ApiResponse<PageResult<SubsystemTokenResponse>>> {
  return get(`${SYSTEM_BASE}/api/subsystem/tokens/by-client/${clientId}`, params)
}

export function getSubsystemTokensByUser(username: string, params?: {
  limit?: number; offset?: number
}): Promise<ApiResponse<PageResult<SubsystemTokenResponse>>> {
  return get(`${SYSTEM_BASE}/api/subsystem/tokens/by-user/${username}`, params)
}

export function revokeSubsystemToken(id: number, reason?: number, remark?: string): Promise<ApiResponse<void>> {
  return post(`${SYSTEM_BASE}/api/subsystem/tokens/${id}/revoke`, null, { params: { reason, remark } })
}

export function getSubsystemStats(): Promise<ApiResponse<any>> {
  return get(`${SYSTEM_BASE}/api/subsystem/stats`)
}

// ==================== 授权审计 (已迁移至 system-server) ====================

export function getAuditDashboard(): Promise<ApiResponse<DashboardStats>> {
  return get(`${SYSTEM_BASE}/api/audit/dashboard`)
}

export function getAuditDashboardByClient(clientId: string): Promise<ApiResponse<any>> {
  return get(`${SYSTEM_BASE}/api/audit/dashboard/client/${clientId}`)
}

export function getAuthorizationsByClient(clientId: string): Promise<ApiResponse<AuthorizationRecord[]>> {
  return get(`${SYSTEM_BASE}/api/audit/authorizations/client/${clientId}`)
}

export function getAuthorizationsByUser(principalName: string): Promise<ApiResponse<AuthorizationRecord[]>> {
  return get(`${SYSTEM_BASE}/api/audit/authorizations/user/${principalName}`)
}

export function revokeToken(data: RevokeLogRequest): Promise<ApiResponse<void>> {
  return post(`${SYSTEM_BASE}/api/audit/revoke`, data)
}

export function getRevokeLogs(params?: {
  limit?: number; offset?: number
}): Promise<ApiResponse<PageResult<RevokeLogResponse>>> {
  return get(`${SYSTEM_BASE}/api/audit/revoke-logs`, params)
}

/** 分页查询审计吊销日志 */
export function getRevokeLogsPage(params: AuditPageQuery): Promise<ApiResponse<PageResult<RevokeLogResponse>>> {
  return get(`${SYSTEM_BASE}/api/audit/revoke-logs/page`, params)
}

export function getRevokeLogsByUser(userId: string): Promise<ApiResponse<RevokeLogResponse[]>> {
  return get(`${SYSTEM_BASE}/api/audit/revoke-logs/user/${userId}`)
}

export function getRevokeLogsByClient(clientId: string): Promise<ApiResponse<RevokeLogResponse[]>> {
  return get(`${SYSTEM_BASE}/api/audit/revoke-logs/client/${clientId}`)
}

// ==================== 请求日志 (已迁移至 system-server) ====================

export function getRequestLogs(params: {
  username?: string; requestUri?: string; httpMethod?: string
  httpStatus?: number; success?: boolean
  startTime?: string; endTime?: string
  page?: number; size?: number
}): Promise<ApiResponse<PageResult<RequestLogEntry>>> {
  return get(`${SYSTEM_BASE}/api/logs`, params)
}

export function getTraceLogs(traceId: string): Promise<ApiResponse<RequestLogEntry[]>> {
  return get(`${SYSTEM_BASE}/api/logs/trace/${traceId}`)
}

export function getLogsStats(): Promise<ApiResponse<any>> {
  return get(`${SYSTEM_BASE}/api/logs/stats`)
}

// ==================== 客户端子系统管理 ====================

/** 查询某客户端下的所有子系统 */
export function getSubsystemsByClient(clientId: string): Promise<ApiResponse<SubsystemInfo[]>> {
  return get(`${SYSTEM_BASE}/api/subsystems`, { clientId })
}

/** 门户首页：查询当前用户可见的子系统列表 */
export function getPortalSubsystems(): Promise<ApiResponse<SubsystemInfo[]>> {
  return get(`${SYSTEM_BASE}/api/subsystems/portal`)
}

/** 门户导航：获取当前用户可访问的子系统（含 OAuth2 授权端点信息） */
export function getSubsystemNav(): Promise<ApiResponse<SubsystemNavItem[]>> {
  return get(`${SYSTEM_BASE}/api/subsystem/nav`)
}

/** 创建子系统 */
export function createSubsystem(data: SubsystemRequest): Promise<ApiResponse<SubsystemInfo>> {
  return post(`${SYSTEM_BASE}/api/subsystems`, data)
}

/** 更新子系统 */
export function updateSubsystem(id: number, data: SubsystemRequest): Promise<ApiResponse<SubsystemInfo>> {
  return put(`${SYSTEM_BASE}/api/subsystems/${id}`, data)
}

/** 删除子系统 */
export function deleteSubsystem(id: number): Promise<ApiResponse<void>> {
  return del(`${SYSTEM_BASE}/api/subsystems/${id}`)
}
