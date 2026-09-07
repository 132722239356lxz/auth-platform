// ==================== 通用类型 ====================

/** 统一响应体 A (auth-server / system-server) */
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

/** 统一响应体 B (auth-flow / auth-message / ai-agent / log-server) */
export interface R<T = any> {
  code: number
  msg: string
  data: T
  success?: boolean
}

/** 分页查询参数（前端通用） */
export interface PageQuery {
  page?: number
  pageSize?: number
}

/** 分页结果（对齐后端 PageResponse: records/total/page/pageSize） */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}

/** 角色分页查询参数 */
export interface RolePageQuery extends PageQuery {
  keyword?: string
  enabled?: boolean
}

/** 用户分页查询参数 */
export interface UserPageQuery extends PageQuery {
  keyword?: string
  enabled?: boolean
  userType?: string
  deptId?: number
  roleId?: number
}

/** 客户端分页查询参数 */
export interface ClientPageQuery extends PageQuery {
  keyword?: string
  enabled?: boolean
}

/** 子系统 Token 分页查询参数 */
export interface SubsystemTokenPageQuery extends PageQuery {
  clientId?: string
  username?: string
  status?: string
  expired?: boolean
}

/** 审计日志分页查询参数 */
export interface AuditPageQuery extends PageQuery {
  clientId?: string
  username?: string
  revokeType?: number
}

/** 字典类型分页查询参数 */
export interface DictTypePageQuery extends PageQuery {
  keyword?: string
  enabled?: boolean
}

/** 公告分页查询参数 */
export interface NoticePageQuery extends PageQuery {
  keyword?: string
  noticeType?: string
  enabled?: boolean
}

// ==================== 认证 (V2.0 对齐) ====================

/** 登录请求 —— 支持手机号+密码 / 用户名+密码 / 短信验证码 */
export interface LoginRequest {
  /** 手机号（手机号登录时填写，短信验证码登录必填） */
  phone?: string
  /** 用户名（用户名登录时填写） */
  username?: string
  /** 密码，密码登录时必填（明文或 RSA 加密后的 Base64） */
  password?: string
  /** 短信验证码，短信验证码登录时填写 */
  smsCode?: string
  /** 登录方式: PASSWORD / SMS */
  loginType?: 'PASSWORD' | 'SMS'
  /** 客户端 ID */
  clientId?: string
  /** 租户 ID */
  tenantId?: string
}

/** 登录响应 — 后端返回 snake_case，前端内部转 camelCase */
export interface LoginResponse {
  access_token: string
  refresh_token: string
  token_type: string
  expires_in: number
  expires_at?: number
  scope: string
  permissions: string[]
  roles?: string[]
  user_info: {
    userId: string | number
    username: string
    nickname: string
    userType: string
    tenantId: string
  }
}

/** 刷新 Token 请求 */
export interface RefreshTokenRequest {
  refresh_token: string
}

/** 用户注册请求 */
export interface UserRegisterRequest {
  username?: string
  password: string
  nickname?: string
  email?: string
  phone?: string
  userType?: string
  tenantId?: string
}

/** 管理员创建用户请求 */
export interface UserCreateRequest {
  username: string
  password: string
  nickname?: string
  email?: string
  phone?: string
  userType?: string
  tenantId?: string
  deptId?: number
  roleIds?: number[]
}

/** 管理员修改用户请求 */
export interface UserUpdateRequest {
  username?: string
  password?: string
  nickname?: string
  email?: string
  phone?: string
  userType?: string
  deptId?: number
}

/** 用户注册响应 */
export interface UserRegisterResponse {
  id: number
  username: string
  nickname: string
  email: string | null
  phone: string | null
  userType: string
  tenantId: string
  enabled: boolean
  createTime: string
  lastLoginTime: string | null
}

/** 修改密码请求 */
export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

/** 更新个人资料请求 */
export interface UpdateProfileRequest {
  nickname?: string
  email?: string
  phone?: string
  avatar?: string
}

/** 意见反馈请求 */
export interface FeedbackRequest {
  content: string
  contact?: string
  type?: string
}

/** 意见反馈记录 */
export interface FeedbackRecord {
  id: number
  content: string
  contact: string | null
  type: string
  status: string
  createTime: string
}

/** 版本信息 */
export interface VersionInfo {
  version: string
  buildTime: string
  changelog: Array<{
    version: string
    date: string
    changes: string[]
  }>
}

/** 扩展用户信息响应 */
export interface ExtendedUserInfoResponse {
  userId: number
  username: string
  nickname: string
  email: string | null
  phone: string | null
  userType: string
  tenantId: string
  permissions: string[]
  accessToken: string
  tokenType: string
  expiresIn: number
  scope: string
  issuedAt: string
  expiresAt: string
}

/** 权限查询响应 */
export interface PermissionsResponse {
  username: string
  permissions: string[]
}

// ==================== 用户 / 角色 / 菜单 (V2.0 对齐) ====================

/** 当前用户响应 */
export interface CurrentUserResponse {
  username: string
  nickname: string
  userId: number
  tenantId: string
  userType: string
  deptId?: number
  deptName?: string
  roles: string[]
  permissions: string[]
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  email?: string | null
  phone?: string | null
  avatar?: string | null
  userType?: string
  tenantId?: string
  deptId?: number
  deptName?: string
  enabled: boolean
  roleCodes?: string[]
  roleNames?: string[]
  permissions?: string[]
  createTime?: string
  lastLoginTime?: string | null
}

/** 用户门户站内信发送请求 */
export interface PortalMessageSendRequest {
  messageType?: string
  title: string
  content: string
  receivers: string[]
  businessId?: string
}

/** 用户门户站内信查询参数 */
export interface PortalMessageQueryParams {
  limit?: number
}

/** 用户门户未读消息数 */
export interface PortalUnreadCount {
  count: number
}

/** 角色请求 */
export interface RoleRequest {
  roleCode: string
  roleName: string
  description?: string
  sortOrder?: number
  menuIds?: number[]
}

export interface RoleInfo {
  id: number
  roleCode: string
  roleName: string
  description?: string
  sortOrder?: number
  enabled: boolean
  menuIds?: number[]
  createTime?: string
  updateTime?: string
}

/** 菜单请求 */
export interface MenuRequest {
  parentId?: number
  menuName: string
  menuType: number
  path?: string
  component?: string
  permission?: string
  icon?: string
  sortOrder?: number
  isFrame?: boolean
  autoAssignRoleIds?: number[]
}

export interface MenuInfo {
  id: number
  parentId: number
  menuName: string
  menuType: number
  path?: string
  component?: string
  permission?: string
  icon?: string
  sortOrder?: number
  enabled: boolean
  isFrame?: boolean
  children?: MenuInfo[]
  createTime?: string
}

/** 部门请求 */
export interface DeptRequest {
  deptName: string
  deptCode: string
  parentId?: number
  leader?: string
  phone?: string
  email?: string
  sortOrder?: number
  enabled?: boolean
}

/** 部门信息 */
export interface DeptInfo {
  id: number
  parentId: number
  deptName: string
  deptCode: string
  leader?: string | null
  phone?: string | null
  email?: string | null
  sortOrder?: number
  enabled: boolean
  children?: DeptInfo[]
  createTime?: string
  updateTime?: string
}

/** 字典类型 */
export interface DictTypeInfo {
  id: number
  dictName: string
  dictType: string
  description?: string
  enabled: boolean
  createTime?: string
}

/** 字典数据 */
export interface DictDataInfo {
  id: number
  typeId: number
  dictLabel: string
  dictValue: string
  sortOrder?: number
  cssClass?: string
  listClass?: string
  enabled: boolean
  remark?: string
  createTime?: string
}

/** 系统公告 */
export interface NoticeInfo {
  id: number
  title: string
  content: string
  noticeType?: string
  priority?: number
  publisherId?: number
  publisherName?: string
  top?: boolean
  publishTime?: string
  expireTime?: string
  enabled: boolean
  readCount?: number
  createTime?: string
}

/** 服务健康状态 */
export interface ServiceHealth {
  name: string
  serviceId: string
  status: 'running' | 'down' | 'unknown'
  instanceCount: number
  port?: number
}

// ==================== OAuth2 客户端 (V2.0 对齐) ====================

/** 重定向地址项: 支持区分来源平台和标签 (兼容保留) */
export interface RedirectUriItem {
  uri: string
  platform: string   // web / mobile / miniapp / desktop
  label?: string     // 地址说明
}

/**
 * 客户端子系统条目（编辑时内联提交）
 * - id 为空 = 新增；非空 = 更新
 * - clientId 由后端从父客户端注入
 */
export interface ClientSubsystemItem {
  id?: number
  /** 平台标识: web / miniapp / app / desktop / admin */
  code: string
  name: string
  iconUrl?: string
  redirectUri?: string
  description?: string
  sortOrder?: number
  visiblePortal?: boolean
}

export interface ClientRequest {
  clientId: string
  clientSecret: string
  clientName: string
  scopes: string[]
  grantTypes: string[]
  /**
   * 子系统列表（一个客户端可挂多个子系统）
   * - 回调地址统一由子系统提供，客户端不再单独维护 redirectUris
   * - 后端会自动从 subsystem.redirectUri 聚合出 oauth2_registered_client.redirect_uris
   */
  subsystems?: ClientSubsystemItem[]
  authMethods: string[]
  tokenTtl?: number
  refreshTtl?: number
  enabled?: boolean
  clientSecretExpiresAt?: string | null
}

export interface ClientInfo {
  id: string
  clientId: string
  clientName: string
  clientIdIssuedAt: string
  clientSecretExpiresAt: string | null
  scopes: string[]
  grantTypes: string[]
  authMethods: string[]
  tokenTtl: number
  refreshTtl: number
  enabled: boolean
  requireAuthorizationConsent: boolean
  /** 子系统列表（替代原 redirectUris，每个子系统独立维护回调地址） */
  subsystems: SubsystemInfo[]
}

// ==================== 客户端子系统 (一对多) ====================

/** 子系统信息 */
export interface SubsystemInfo {
  id: number
  clientId: string
  clientName?: string
  /** 平台标识: web / miniapp / app / desktop / admin */
  code: string
  name: string
  iconUrl?: string
  redirectUri?: string
  description?: string
  sortOrder: number
  visiblePortal: boolean
  enabled?: boolean
  createTime?: string
  updateTime?: string
}

/** 子系统创建/更新请求 */
export interface SubsystemRequest {
  clientId: string
  /** 平台标识: web / miniapp / app / desktop / admin */
  code: string
  name: string
  iconUrl?: string
  redirectUri?: string
  description?: string
  sortOrder?: number
  visiblePortal?: boolean
}

/** 门户导航子系统项（含 OAuth2 授权所需信息） */
export interface SubsystemNavItem {
  subsystemId: number
  clientId: string
  clientName: string
  /** 平台标识: web / miniapp / app / desktop / admin */
  subsystemCode?: string
  subsystemName?: string
  ssoEnabled: boolean
  /** 授权端点，如 /auth-server/oauth2/authorize */
  authorizeEndpoint: string
  /** 客户端注册的所有回调地址 */
  redirectUris: string[]
  scopes: string[]
  iconUrl?: string
  /** 该子系统对应的 OAuth2 回调地址，用于构造 authorize 请求 */
  redirectUri?: string
  description?: string
  sortOrder?: number
}

/** 密钥重置请求 */
export interface SecretResetRequest {
  secret: string
}

/** 批量分配角色请求 */
export interface BatchAssignRoleRequest {
  userIds: number[]
  roleIds: number[]
}

// ==================== 子系统 Token (V2.0 对齐) ====================

export interface SubsystemTokenRequest {
  clientId: string
  userId?: number
  username: string
  accessToken: string
  refreshToken?: string
  tokenType?: string
  accessTokenExpiresAt?: string
  refreshTokenExpiresAt?: string
  issuedIp?: string
  userAgent?: string
}

export interface SubsystemTokenResponse {
  id: number
  clientId: string
  userId: number | null
  username: string
  accessTokenSnip: string
  refreshTokenSnip: string
  tokenType: string
  accessTokenExpiresAt: string
  refreshTokenExpiresAt: string
  status: string
  expired: boolean
  parentTokenId: number | null
  issuedIp: string
  createTime: string
  lastRefreshTime: string | null
  refreshCount: number
  revokeTime: string | null
  revokeReasonDesc: string | null
}

// ==================== 授权审计 (V2.0 对齐) ====================

export interface AuthorizationRecord {
  id: string
  clientId: string
  clientName: string
  principalName: string
  authorizationGrantType: string
  authorizedScopes: string[]
  accessTokenSnip: string
  accessTokenIssuedAt: string
  accessTokenExpiresAt: string
  accessTokenExpired: boolean
  refreshTokenSnip: string
  refreshTokenIssuedAt: string
  refreshTokenExpiresAt: string
  refreshTokenExpired: boolean
}

export interface RevokeLogRequest {
  userId: string
  clientId: string
  tokenType?: string
  revokeType: number
  remark?: string
}

export interface RevokeLogResponse {
  id: number
  userId: string
  clientId: string
  clientName: string
  tokenType: string
  tokenSnip: string
  revokeType: number
  revokeTypeDesc: string
  createTime: string
  remark: string | null
}

export interface DashboardStats {
  totalClients?: number
  totalUsers?: number
  totalAuthorizations?: number
  totalRevokeLogs?: number
  activeTokens?: number
  activeTokenCount?: number
  subsystemActiveTokenCount?: number
  systemActiveTokenCount?: number
  pendingApprovalCount?: number
  clientCount?: number
  userCount?: number
  roleCount?: number
  menuCount?: number
  deptCount?: number
  dictTypeCount?: number
  dictDataCount?: number
  authorizationCount?: number
  revokeLogCount?: number
  [key: string]: any
}

// ==================== 工作流 (V2.0 对齐) ====================

export interface NodeRequest {
  nodeName?: string
  nodeType?: string
  execMode?: string
  parallelGroup?: string
  parentNodeId?: number
  conditionExpression?: string
  onConditionFail?: string
  approverStrategy?: string
  approvers?: string
  approverRole?: string
  sortOrder?: number
  timeoutHours?: number
  countersign?: boolean
  rejectStrategy?: string
}

export interface WorkflowCreateRequest {
  definitionKey: string
  definitionName: string
  description?: string
  category?: string
  nodes: NodeRequest[]
}

export interface DefinitionDetail {
  id: number
  definitionKey: string
  definitionName: string
  description?: string
  category?: string
  version?: number
  status?: number
  createTime?: string
  nodes: DefinitionNode[]
}

export interface DefinitionNode {
  id: number
  nodeName: string
  nodeType: string
  execMode?: string
  parallelGroup?: string
  parentNodeId?: number
  conditionExpression?: string
  onConditionFail?: string
  approverStrategy?: string
  approvers?: string
  approverRole?: string
  sortOrder: number
  timeoutHours?: number
  countersign?: boolean
  rejectStrategy?: string
}

export interface WorkflowDefinition {
  id?: number
  definitionKey: string
  definitionName: string
  description?: string
  category?: string
  version?: number
  status?: number
  createTime?: string
}

export interface WorkflowInstance {
  id: number
  definitionId?: number
  definitionName?: string
  title: string
  applicant: string
  applyContent?: string
  status: string
  currentNodeName?: string
  currentNodeId?: number
  createTime?: string
  createdAt?: string
  updatedAt?: string
  currentApprover?: string
  finishTime?: string
}

export interface WorkflowTask {
  id: number
  nodeName?: string
  approver?: string
  status: string
  comment?: string
  createTime?: string
  approveTime?: string
}

export interface WorkflowDetail {
  definition?: WorkflowDefinition
  instance: WorkflowInstance
  nodes?: DefinitionNode[]
  tasks?: WorkflowTask[]
}

export interface ApprovalRequest {
  taskId: number
  action: 'APPROVE' | 'REJECT' | 'TRANSFER'
  comment?: string
  transferTo?: string
}

export interface ApprovalRecord {
  nodeName: string
  approver: string
  action: string
  comment: string
  approveTime: string
}

export interface MyApprovalRecord {
  taskId: number
  instanceId: number
  title: string
  applicant: string
  nodeName: string
  action: string
  comment?: string
  approveTime?: string
  instanceStatus: string
}

export interface WorkflowForm {
  id?: number
  formKey: string
  formName: string
  definitionKey: string
  schemaJson: string
  applyType?: string
  icon?: string
  sortOrder?: number
  status?: number
  createdBy?: string
  createTime?: string
  updateTime?: string
}

export interface FormFieldSchema {
  field: string
  label: string
  type: 'text' | 'textarea' | 'number' | 'select' | 'date'
  required?: boolean
  placeholder?: string
  maxlength?: number
  options?: { label: string; value: any }[]
}

// ==================== 消息 (V2.0 对齐) ====================

export interface MessageSendRequest {
  messageType: string
  title: string
  content: string
  channels?: string[]
  sourceSystem?: string
  sender?: string
  receivers?: string[]
  businessId?: string
  templateCode?: string
  templateVars?: Record<string, string>
}

export interface MessageBroadcastRequest {
  messageType: string
  title: string
  content: string
  targetSubsystems: string[]
  channels?: string[]
  sourceSystem?: string
  eventType?: string
  businessId?: string
  templateCode?: string
  templateVars?: Record<string, string>
  extraData?: Record<string, any>
}

export interface MessageTemplateRequest {
  templateCode: string
  templateName: string
  titleTemplate: string
  contentTemplate: string
  messageType?: string
  channels?: string
}

export interface MessageTemplateVO {
  id?: number
  templateCode: string
  templateName: string
  channel: string
  titleTemplate: string
  contentTemplate: string
  variables?: string
  status?: number
  createdBy?: string
  createTime?: string
  updateTime?: string
  usageCount?: number
  lastUsedTime?: string
}

export interface MessageTemplateQueryParams {
  keyword?: string
  templateCode?: string
  channel?: string
  status?: number
  createdBy?: string
  startTime?: string
  endTime?: string
  page?: number
  size?: number
}

export interface MessageTemplatePageResult {
  total: number
  list: MessageTemplateVO[]
}

export interface MessageInfo {
  messageId?: string
  messageType?: string
  title: string
  content: string
  channels?: string
  sourceSystem?: string
  sender?: string
  receivers?: string
  targetSubsystems?: string
  businessId?: string
  status?: string
  isRead?: boolean
  failReason?: string
  retryCount?: number
  templateCode?: string
  createTime?: string
  sendTime?: string
  readTime?: string
}

export interface MessageQueryParams {
  keyword?: string
  messageType?: string
  status?: string
  channels?: string
  receiver?: string
  excludeChannels?: string
  sender?: string
  isRead?: boolean
  startTime?: string
  endTime?: string
  page?: number
  size?: number
}

export interface MessagePageResult {
  total: number
  list: MessageInfo[]
}

// ==================== 子系统事件 ====================

export interface SubsystemEvent {
  messageId?: string
  title: string
  content: string
  sourceSystem?: string
  targetSubsystems?: string
  status?: string
  createTime?: string
}

// ==================== 子系统异常反馈 ====================

export interface SubsystemIncident {
  id?: number
  subsystem: string
  subsystemName?: string
  incidentType?: string
  level: string
  title: string
  content?: string
  stackTrace?: string
  traceId?: string
  status?: string
  reportedAt?: string
  resolvedAt?: string
  resolver?: string
  resolveNote?: string
  createTime?: string
}

export interface SubsystemIncidentReport {
  subsystem: string
  subsystemName?: string
  incidentType?: string
  level?: string
  title: string
  content?: string
  stackTrace?: string
  traceId?: string
}

export interface SubsystemIncidentStats {
  total: number
  pending: number
  criticalPending: number
}

export interface SubsystemIncidentQuery {
  keyword?: string
  subsystem?: string
  level?: string
  status?: string
  page?: number
  size?: number
}

// ==================== AI 智能体 (V2.0 对齐) ====================

export interface ChatAttachment {
  /** 附件类型: image=图片, file=普通文件 */
  type: 'image' | 'file'
  /** 展示名称/文件名 */
  name: string
  /** 本地预览 URL (blob/dataURL) 或后端返回的访问 URL */
  url?: string
  /** 原始 File 对象，上传时使用 */
  raw?: File
  /** 文件 MIME 类型 */
  mimeType?: string
  /** 文件大小(字节) */
  size?: number
}

export interface ChatRequest {
  sessionId?: string
  question: string
  useMemory?: boolean
  attachments?: ChatAttachment[]
  /** 用户ID，用于会话归属 */
  userId?: string
}

export interface ChatMessageRecord {
  id?: number
  sessionId?: string
  role: 'user' | 'assistant' | 'system'
  content?: string
  attachments?: ChatAttachment[]
  toolsUsed?: string[]
  createTime?: string
}

/** 文件相关性分析结果 */
export interface FileRelevanceItem {
  fileName: string
  relevanceScore: number
  isPrimary: boolean
  snippet: string
}

export interface ChatResponseData {
  answer: string
  toolsUsed?: string[]
  elapsedMs?: number
  /** 问题复杂度: simple / medium / complex */
  complexity?: string
  /** 路由到的模型名称 */
  modelUsed?: string
  /** 是否命中缓存 */
  cached?: boolean
  /** 上下文是否触发压缩 */
  compressed?: boolean
  /** 消息总数 */
  totalMessages?: number
  /** 多文件相关性分析结果 */
  fileRelevance?: FileRelevanceItem[]
  /** 最相关的主文件 */
  primaryFileName?: string
  /** 会话标题（首次对话时生成） */
  sessionTitle?: string
  /** 实际使用的供应商编码 */
  providerCode?: string
  /** 实际使用的供应商名称 */
  providerName?: string
  /** 切换前失败的供应商名称（主备切换时） */
  failoverProvider?: string
  /** 模型的思考/推理过程 */
  reasoning?: string
}

/** 会话列表项 */
export interface ChatSessionVO {
  sessionId: string
  title: string
  status: string
  userId: string
  messageCount: number
  createTime: string
  updateTime: string
}

/** 会话历史响应 */
export interface SessionHistoryData {
  sessionId: string
  title: string
  messages: ChatMessageRecord[]
  compressed: boolean
  compressedSummary?: string
  totalMessages: number
}

/** 缓存统计 */
export interface CacheStatsVO {
  totalEntries: number
  hitCount: number
  missCount: number
  hitRate: number
  estimatedSize: string
  maxSize: number
}

export interface KnowledgeBase {
  id: number
  name: string
  description?: string
  docCount: number
  status?: string
  createdAt?: string
  updatedAt?: string
}

export interface KnowledgeChunk {
  id: number
  docId: number
  chunkIndex: number
  chunkText: string
  tokenCount?: number
  createdAt?: string
}

export interface KnowledgeDocDetail extends KnowledgeDoc {
  content?: string
  splitterLabel?: string
  chunks?: KnowledgeChunk[]
}

export interface KnowledgeDocRequest {
  kbName?: string
  title?: string
  content?: string
  contentType?: string
  fileName?: string
  splitterType?: string
  chunkSize?: number
  overlap?: number
}

export interface KnowledgeDoc {
  id: number
  kbName: string
  title: string
  contentPreview?: string
  contentType?: string
  fileName?: string
  fileSize?: number
  chunkCount?: number
  splitterType?: string
  chunkSize?: number
  overlap?: number
  status?: string
  createdAt?: string
  updatedAt?: string
}

export interface UploadTask {
  id: string
  docId?: number
  kbName?: string
  fileName?: string
  fileSize?: number
  status: string
  progress: number
  errorMsg?: string
  createdAt?: string
  updatedAt?: string
}

/** 知识库文件上传结果（后端可能返回任务对象或数组） */
export interface UploadResult {
  taskId?: string
  id?: string
  docId?: number
  success?: boolean
  error?: string
  parsedContentType?: string
  parseTimeMs?: number
  status?: string
  progress?: number
}

export interface SearchRequest {
  query?: string
  searchType?: string
  kbName?: string
  maxResults?: number
}

export interface SearchResult {
  query: string
  searchType?: string
  /** RAG 模式下的 LLM 生成回答 */
  answer?: string
  /** RAG 模式下的检索上下文 */
  context?: Array<{
    text: string
    score: number
    metadata: Record<string, string>
  }>
  contextCount?: number
  /** 本地/联网搜索的结果列表 */
  results?: Array<{
    title: string
    content: string
    score?: number
    source?: string
    highlight?: string
  }>
  /** 联网搜索来源 */
  localResults?: any[]
  webResults?: any[]
  ragContextCount?: number
  latency?: number
  elapsedMs?: number
  totalResults?: number
}

export interface AlertVO {
  id: number
  alertName: string
  analysisType?: string
  dataSource?: string
  alertLevel: 'INFO' | 'WARN' | 'CRITICAL'
  alertContent?: string
  analysisDetail?: string
  suggestion?: string
  isRead: boolean
  resolved: boolean
  resolvedAt?: string
  resolvedBy?: string
  createdAt: string
}

export interface AlertPageResult {
  total: number
  list: AlertVO[]
}

export interface AlertQueryParams {
  keyword?: string
  level?: string
  resolved?: boolean
  analysisType?: string
  startTime?: string
  endTime?: string
  page: number
  size: number
}

// ==================== 日志服务 (V2.0 对齐) ====================

export interface LogQueryRequest {
  page?: number
  size?: number
  module?: string
  category?: string
  level?: string
  keyword?: string
  exceptionType?: string
  errorFingerprint?: string
  httpStatus?: number
  username?: string
  requestUri?: string
  startTime?: string
  endTime?: string
  minCostTime?: number
}

export interface LogRecord {
  id?: number
  traceId?: string
  module?: string
  category?: string
  level: string
  className?: string
  methodName?: string
  message: string
  fullMessage?: string
  exceptionStack?: string
  exceptionType?: string
  errorFingerprint?: string
  username?: string
  clientIp?: string
  requestUri?: string
  httpMethod?: string
  httpStatus?: number
  costTime?: number
  logTime?: string
  createdAt?: string
}

export interface LogStatsDTO {
  totalCount: number
  levelDistribution: Record<string, number>
  moduleDistribution: Record<string, number>
  categoryDistribution: Record<string, number>
  errorCount: number
  errorRate: number
  topErrorFingerprints: Array<{ fingerprint: string; count: number }>
  avgCostTime: number
  p99CostTime?: number
}

export interface SolutionSaveRequest {
  errorFingerprint?: string
  errorPattern?: string
  rootCause?: string
  solution?: string
  steps?: string
  referenceUrl?: string
  status?: string
}

// ==================== 请求日志 (auth-server) ====================

export interface RequestLogEntry {
  id?: number
  traceId?: string
  username?: string
  requestUri?: string
  httpMethod?: string
  httpStatus?: number
  success?: boolean
  costTime?: number
  clientIp?: string
  createTime?: string
}

// ==================== 网关熔断 ====================

export interface FallbackResponse {
  code: number
  message: string
  service: string
  timestamp: string
  data: null
}

// ==================== OAuth2 Token (向后兼容) ====================

export interface TokenInfo {
  access_token: string
  refresh_token?: string
  token_type: string
  expires_in: number
  scope?: string
}

// ==================== AI 供应商 (主备故障切换) ====================

export interface AiProvider {
  id?: number
  providerCode: string
  providerName: string
  /** 供应商类型: openai/azure/anthropic/custom */
  providerType: string
  baseUrl: string
  apiKeyMasked?: string
  apiKeyConfigured?: boolean
  secretKeyMasked?: string
  secretKeyConfigured?: boolean
  defaultModel?: string
  /** 向量模型(用于文本转向量, 如 text-embedding-3-small) */
  embeddingModel?: string
  models?: string[]
  /** 是否主供应商：true=主用，false=备用 */
  isPrimary?: boolean
  /** 备用供应商优先级，数字越小优先级越高 */
  priority?: number
  timeoutMs?: number
  maxRetries?: number
  temperature?: number
  enabled: boolean
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface AiProviderRequest {
  id?: number
  providerCode: string
  providerName: string
  /** 供应商类型: openai/azure/anthropic/custom */
  providerType: string
  baseUrl: string
  /** 新增必填；编辑时留空表示保持原值 */
  apiKey?: string
  secretKey?: string
  /** 编辑时传 true 则清空已保存密钥 */
  clearSecretKey?: boolean
  defaultModel?: string
  /** 向量模型(用于文本转向量, 如 text-embedding-3-small) */
  embeddingModel?: string
  models?: string[]
  /** 是否主供应商：true=主用，false=备用 */
  isPrimary?: boolean
  /** 备用供应商优先级，数字越小优先级越高 */
  priority?: number
  timeoutMs?: number
  maxRetries?: number
  temperature?: number
  enabled?: boolean
  remark?: string
}

export interface AiProviderQueryParams {
  providerName?: string
  providerType?: string
  enabled?: boolean
  page?: number
  pageSize?: number
}

export interface ProviderTestResponse {
  success: boolean
  httpStatus?: number
  latencyMs?: number
  message?: string
  availableModels?: string[]
}

// ==================== AI 复杂度路由 ====================

export interface AiRoutingInfo {
  id?: number
  routingName: string
  providerId: number
  providerCode?: string
  providerName?: string
  simpleModel: string
  mediumModel: string
  complexModel: string
  simpleMaxTokens: number
  mediumMaxTokens: number
  complexMaxTokens: number
  enabled: boolean
  routingConfigured?: boolean
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface AiRoutingRequest {
  routingName: string
  providerId: number
  providerCode?: string
  providerName?: string
  simpleModel: string
  mediumModel: string
  complexModel: string
  simpleMaxTokens: number
  mediumMaxTokens: number
  complexMaxTokens: number
  enabled?: boolean
  remark?: string
}

export interface AiRoutingQuery {
  keyword?: string
  providerId?: number
  enabled?: boolean
  page?: number
  pageSize?: number
}

export interface AiProviderOption {
  id: number
  providerCode: string
  providerName: string
  providerType: string
  defaultModel?: string
  embeddingModel?: string
  models?: string[]
}
