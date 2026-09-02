import { get, post, put, del } from '@/utils/request'
import type {
  ApiResponse, CurrentUserResponse, UserInfo, RoleInfo, RoleRequest, MenuInfo, MenuRequest,
  PageResult, PageQuery, RolePageQuery, UserPageQuery, DeptInfo, DeptRequest, DashboardStats,
  UserCreateRequest, UserUpdateRequest, BatchAssignRoleRequest, DictTypeInfo, DictDataInfo,
  NoticeInfo, NoticePageQuery, ServiceHealth, AiProvider, AiProviderRequest, AiProviderQueryParams,
  ProviderTestResponse, AiRoutingInfo, AiRoutingRequest, AiRoutingQuery, AiProviderOption
} from '@/types'

const SYSTEM_BASE = '/system-server'

// ==================== 当前用户 ====================

export function getCurrentUser(): Promise<ApiResponse<CurrentUserResponse>> {
  return get(`${SYSTEM_BASE}/api/users/current`)
}

export function getCurrentUserMenus(): Promise<ApiResponse<MenuInfo[]>> {
  return get(`${SYSTEM_BASE}/api/users/current/menus`)
}

/** 当前用户更新个人资料（昵称/邮箱/手机/头像） */
export function updateCurrentProfile(data: { nickname?: string; email?: string; phone?: string; avatar?: string }): Promise<ApiResponse<UserInfo>> {
  return put(`${SYSTEM_BASE}/api/users/current/profile`, data)
}

/** 当前用户修改密码 */
export function changeCurrentPassword(data: { oldPassword: string; newPassword: string }): Promise<ApiResponse<void>> {
  return put(`${SYSTEM_BASE}/api/users/current/password`, data)
}

// ==================== 用户管理 ====================

export function getUsers(): Promise<ApiResponse<UserInfo[]>> {
  return get(`${SYSTEM_BASE}/api/users`)
}

/** 分页查询用户(支持多条件) */
export function getUsersPage(params: UserPageQuery): Promise<ApiResponse<PageResult<UserInfo>>> {
  return get(`${SYSTEM_BASE}/api/users/page`, params)
}

export function getUserById(id: number): Promise<ApiResponse<UserInfo>> {
  return get(`${SYSTEM_BASE}/api/users/${id}`)
}

export function toggleUserStatus(id: number, enabled: boolean): Promise<ApiResponse<void>> {
  const url = enabled
    ? `${SYSTEM_BASE}/api/users/${id}/enable`
    : `${SYSTEM_BASE}/api/users/${id}/disable`
  return put(url)
}

export function deleteUser(id: number): Promise<ApiResponse<void>> {
  return del(`${SYSTEM_BASE}/api/users/${id}`)
}

export function createUser(data: UserCreateRequest): Promise<ApiResponse<UserInfo>> {
  return post(`${SYSTEM_BASE}/api/users`, data)
}

export function updateUser(id: number, data: UserUpdateRequest): Promise<ApiResponse<UserInfo>> {
  return put(`${SYSTEM_BASE}/api/users/${id}`, data)
}

export function getUserRoles(id: number): Promise<ApiResponse<string[]>> {
  return get(`${SYSTEM_BASE}/api/users/${id}/roles`)
}

export function assignUserRoles(id: number, roleIds: number[]): Promise<ApiResponse<void>> {
  return put(`${SYSTEM_BASE}/api/users/${id}/roles`, { roleIds })
}

/** 批量分配角色：将多个用户批量分配到指定角色 */
export function batchAssignRoles(data: BatchAssignRoleRequest): Promise<ApiResponse<number>> {
  return put(`${SYSTEM_BASE}/api/users/batch/roles`, data)
}

// ==================== 用户-应用(子系统)关联 ====================

export interface UserSubsystemVO {
  id: number
  clientId: string
  clientName: string
  scopes?: string
  visible: boolean
  grantedBy?: number | null
  grantedTime?: string
}

export interface SubsystemVO {
  id: string
  clientId: string
  clientName: string
  scopes?: string
}

/** 查询用户已分配的应用列表 */
export function getUserSubsystems(userId: number): Promise<ApiResponse<UserSubsystemVO[]>> {
  return get(`${SYSTEM_BASE}/api/system/user-subsystems`, { userId })
}

/** 批量分配用户可见应用 */
export function assignUserSubsystems(userId: number, clientIds: string[]): Promise<ApiResponse<void>> {
  return put(`${SYSTEM_BASE}/api/system/user-subsystems/${userId}`, { clientIds })
}

/** 查询所有可分配的应用(子系统) */
export function listAllSubsystems(): Promise<ApiResponse<SubsystemVO[]>> {
  return get(`${SYSTEM_BASE}/api/system/user-subsystems/subsystems`)
}

// ==================== 角色管理 ====================

export function getRoles(): Promise<ApiResponse<RoleInfo[]>> {
  return get(`${SYSTEM_BASE}/api/roles`)
}

/** 分页查询角色 */
export function getRolesPage(params: RolePageQuery): Promise<ApiResponse<PageResult<RoleInfo>>> {
  return get(`${SYSTEM_BASE}/api/roles/page`, params)
}

export function getRoleById(id: number): Promise<ApiResponse<RoleInfo>> {
  return get(`${SYSTEM_BASE}/api/roles/${id}`)
}

export function createRole(data: RoleRequest): Promise<ApiResponse<RoleInfo>> {
  return post(`${SYSTEM_BASE}/api/roles`, data)
}

export function updateRole(id: number, data: RoleRequest): Promise<ApiResponse<RoleInfo>> {
  return put(`${SYSTEM_BASE}/api/roles/${id}`, data)
}

export function toggleRoleStatus(id: number, enabled: boolean): Promise<ApiResponse<void>> {
  const url = enabled
    ? `${SYSTEM_BASE}/api/roles/${id}/enable`
    : `${SYSTEM_BASE}/api/roles/${id}/disable`
  return put(url)
}

export function deleteRole(id: number): Promise<ApiResponse<void>> {
  return del(`${SYSTEM_BASE}/api/roles/${id}`)
}

export function assignRoleMenus(id: number, menuIds: number[]): Promise<ApiResponse<void>> {
  return put(`${SYSTEM_BASE}/api/roles/${id}/menus`, { menuIds })
}

// ==================== 菜单管理 ====================

export function getMenuTree(params?: { keyword?: string; menuType?: number; enabled?: boolean }): Promise<ApiResponse<MenuInfo[]>> {
  return get(`${SYSTEM_BASE}/api/menus/tree`, params)
}

export function getMenuById(id: number): Promise<ApiResponse<MenuInfo>> {
  return get(`${SYSTEM_BASE}/api/menus/${id}`)
}

export function createMenu(data: MenuRequest): Promise<ApiResponse<MenuInfo>> {
  return post(`${SYSTEM_BASE}/api/menus`, data)
}

export function updateMenu(id: number, data: MenuRequest): Promise<ApiResponse<MenuInfo>> {
  return put(`${SYSTEM_BASE}/api/menus/${id}`, data)
}

export function toggleMenuStatus(id: number, enabled: boolean): Promise<ApiResponse<void>> {
  const url = enabled
    ? `${SYSTEM_BASE}/api/menus/${id}/enable`
    : `${SYSTEM_BASE}/api/menus/${id}/disable`
  return put(url)
}

export function deleteMenu(id: number): Promise<ApiResponse<void>> {
  return del(`${SYSTEM_BASE}/api/menus/${id}`)
}

// ==================== 部门管理 ====================

export function getDeptTree(params?: { keyword?: string; enabled?: boolean }): Promise<ApiResponse<DeptInfo[]>> {
  return get(`${SYSTEM_BASE}/api/depts/tree`, params)
}

export function getDepts(): Promise<ApiResponse<DeptInfo[]>> {
  return get(`${SYSTEM_BASE}/api/depts`)
}

export function getDeptById(id: number): Promise<ApiResponse<DeptInfo>> {
  return get(`${SYSTEM_BASE}/api/depts/${id}`)
}

export function createDept(data: DeptRequest): Promise<ApiResponse<DeptInfo>> {
  return post(`${SYSTEM_BASE}/api/depts`, data)
}

export function updateDept(id: number, data: DeptRequest): Promise<ApiResponse<DeptInfo>> {
  return put(`${SYSTEM_BASE}/api/depts/${id}`, data)
}

export function deleteDept(id: number): Promise<ApiResponse<void>> {
  return del(`${SYSTEM_BASE}/api/depts/${id}`)
}

export function toggleDeptStatus(id: number, enabled: boolean): Promise<ApiResponse<void>> {
  const url = enabled
    ? `${SYSTEM_BASE}/api/depts/${id}/enable`
    : `${SYSTEM_BASE}/api/depts/${id}/disable`
  return put(url)
}

// ==================== 仪表盘 ====================

export function getDashboardStats(): Promise<ApiResponse<DashboardStats>> {
  return get(`${SYSTEM_BASE}/api/dashboard/stats`)
}

export function getDashboardServices(): Promise<ApiResponse<ServiceHealth[]>> {
  return get(`${SYSTEM_BASE}/api/dashboard/services`)
}

// ==================== 数据字典 ====================

export function getDictTypes(params?: { keyword?: string; enabled?: boolean }): Promise<ApiResponse<DictTypeInfo[]>> {
  return get(`${SYSTEM_BASE}/api/dicts/types`, params)
}

export function getDictDataByType(typeId: number): Promise<ApiResponse<DictDataInfo[]>> {
  return get(`${SYSTEM_BASE}/api/dicts/data/type/${typeId}`)
}

export function getDictDataByKey(dictType: string): Promise<ApiResponse<DictDataInfo[]>> {
  return get(`${SYSTEM_BASE}/api/dicts/data/key/${dictType}`)
}

export function createDictType(data: { dictName: string; dictType: string; description?: string }): Promise<ApiResponse<DictTypeInfo>> {
  return post(`${SYSTEM_BASE}/api/dicts/types`, data)
}

export function updateDictType(id: number, data: { dictName: string; dictType: string; description?: string }): Promise<ApiResponse<DictTypeInfo>> {
  return put(`${SYSTEM_BASE}/api/dicts/types/${id}`, data)
}

export function deleteDictType(id: number): Promise<ApiResponse<void>> {
  return del(`${SYSTEM_BASE}/api/dicts/types/${id}`)
}

export function createDictData(data: { typeId: number; dictLabel: string; dictValue: string; sortOrder?: number; cssClass?: string; listClass?: string; remark?: string }): Promise<ApiResponse<DictDataInfo>> {
  return post(`${SYSTEM_BASE}/api/dicts/data`, data)
}

export function updateDictData(id: number, data: { typeId: number; dictLabel: string; dictValue: string; sortOrder?: number; cssClass?: string; listClass?: string; remark?: string }): Promise<ApiResponse<DictDataInfo>> {
  return put(`${SYSTEM_BASE}/api/dicts/data/${id}`, data)
}

export function deleteDictData(id: number): Promise<ApiResponse<void>> {
  return del(`${SYSTEM_BASE}/api/dicts/data/${id}`)
}

// ==================== 系统公告 ====================

export function getPublishedNotices(limit?: number): Promise<ApiResponse<NoticeInfo[]>> {
  return get(`${SYSTEM_BASE}/api/notices/published`, { limit: limit || 10 })
}

export function getNoticesPage(params: NoticePageQuery): Promise<ApiResponse<PageResult<NoticeInfo>>> {
  return get(`${SYSTEM_BASE}/api/notices/page`, params)
}

export function createNotice(data: Partial<NoticeInfo>): Promise<ApiResponse<NoticeInfo>> {
  return post(`${SYSTEM_BASE}/api/notices`, data)
}

export function updateNotice(id: number, data: Partial<NoticeInfo>): Promise<ApiResponse<NoticeInfo>> {
  return put(`${SYSTEM_BASE}/api/notices/${id}`, data)
}

export function deleteNotice(id: number): Promise<ApiResponse<void>> {
  return del(`${SYSTEM_BASE}/api/notices/${id}`)
}

// ==================== AI 供应商管理 (主备故障切换) ====================

export function getAiProviders(params?: AiProviderQueryParams): Promise<ApiResponse<PageResult<AiProvider>>> {
  return get(`${SYSTEM_BASE}/api/ai-providers/page`, params)
}

export function getAiProviderById(id: number): Promise<ApiResponse<AiProvider>> {
  return get(`${SYSTEM_BASE}/api/ai-providers/${id}`)
}

export function createAiProvider(data: AiProviderRequest): Promise<ApiResponse<AiProvider>> {
  return post(`${SYSTEM_BASE}/api/ai-providers`, data)
}

export function updateAiProvider(id: number, data: AiProviderRequest): Promise<ApiResponse<AiProvider>> {
  return put(`${SYSTEM_BASE}/api/ai-providers/${id}`, data)
}

export function deleteAiProvider(id: number): Promise<ApiResponse<void>> {
  return del(`${SYSTEM_BASE}/api/ai-providers/${id}`)
}

export function testAiProvider(id: number): Promise<ApiResponse<ProviderTestResponse>> {
  return post(`${SYSTEM_BASE}/api/ai-providers/${id}/test`)
}

// ==================== AI 调用记录管理 (排查 AI 生成是否合理) ====================

export interface AiInvokeLogQuery {
  page?: number
  size?: number
  providerCode?: string
  modelName?: string
  userId?: string
  success?: boolean
  startTime?: string
  endTime?: string
}

export interface AiInvokeLog {
  id: number
  sessionId?: string
  userId?: string
  userName?: string
  providerCode?: string
  providerName?: string
  providerType?: string
  modelName?: string
  complexity?: string
  cached?: boolean
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  toolNames?: string[]
  toolCount?: number
  ragReferences?: string[]
  contextContent?: string
  userInput?: string
  aiOutput?: string
  elapsedMs?: number
  success?: boolean
  errorMsg?: string
  invokeTime?: string
  createTime?: string
}

export function getAiInvokeLogs(params: AiInvokeLogQuery): Promise<ApiResponse<PageResult<AiInvokeLog>>> {
  return post(`${SYSTEM_BASE}/api/ai-invoke-logs/page`, params)
}

export function getAiInvokeLogById(id: number): Promise<ApiResponse<AiInvokeLog>> {
  return get(`${SYSTEM_BASE}/api/ai-invoke-logs/${id}`)
}

export function deleteAiInvokeLog(id: number): Promise<ApiResponse<void>> {
  return del(`${SYSTEM_BASE}/api/ai-invoke-logs/${id}`)
}

export function clearAiInvokeLogs(): Promise<ApiResponse<void>> {
  return del(`${SYSTEM_BASE}/api/ai-invoke-logs/clear`)
}

export interface AiInvokeLogStats {
  total?: number
  success?: number
  cacheHit?: number
  totalTokens?: number
  avgElapsedMs?: number
  cacheHitRate?: number
  successRate?: number
}

/** AI 调用日志聚合统计（总调用/成功率/缓存命中率/总 token/平均耗时） */
export function getAiInvokeLogStats(): Promise<ApiResponse<AiInvokeLogStats>> {
  return get(`${SYSTEM_BASE}/api/ai-invoke-logs/stats`)
}

// ==================== AI 复杂度路由 ====================

export function getAiRoutings(params: AiRoutingQuery): Promise<ApiResponse<PageResult<AiRoutingInfo>>> {
  return get(`${SYSTEM_BASE}/api/ai-routings/page`, params)
}

export function getAiRoutingById(id: number): Promise<ApiResponse<AiRoutingInfo>> {
  return get(`${SYSTEM_BASE}/api/ai-routings/${id}`)
}

export function createAiRouting(data: AiRoutingRequest): Promise<ApiResponse<AiRoutingInfo>> {
  return post(`${SYSTEM_BASE}/api/ai-routings`, data)
}

export function updateAiRouting(id: number, data: AiRoutingRequest): Promise<ApiResponse<AiRoutingInfo>> {
  return put(`${SYSTEM_BASE}/api/ai-routings/${id}`, data)
}

export function deleteAiRouting(id: number): Promise<ApiResponse<void>> {
  return del(`${SYSTEM_BASE}/api/ai-routings/${id}`)
}

export function toggleAiRouting(id: number, enabled: boolean): Promise<ApiResponse<void>> {
  return put(`${SYSTEM_BASE}/api/ai-routings/${id}/toggle/${enabled}`)
}

/** 按供应商ID查询路由配置（通过分页接口） */
export function getRoutingByProvider(providerId: number): Promise<ApiResponse<PageResult<AiRoutingInfo>>> {
  return get(`${SYSTEM_BASE}/api/ai-routings/page`, { providerId, page: 1, pageSize: 50 })
}

/** 查询可绑定路由的供应商列表 */
export function getAvailableProvidersForRouting(excludeRoutingId?: number): Promise<ApiResponse<AiProviderOption[]>> {
  return get(`${SYSTEM_BASE}/api/ai-routings/available-providers`, { excludeRoutingId })
}
