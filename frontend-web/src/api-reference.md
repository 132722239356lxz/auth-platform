# 统一认证平台 API 接口文档 V2.0

> **版本**: 2.0  
> **更新日期**: 2026-07-10  
> **文档作用**: 前后端联调唯一标准，所有接口入参/出参结构以此文档为准  
> **文档驱动原则**: 后端 DTO 字段 + 前端 TS 类型 + 页面渲染字段 必须与本文档严格一致

---

## 目录

- [1. 通用规范](#1-通用规范)
- [2. 认证服务 auth-server (9000)](#2-认证服务-auth-server-9000)
  - [2.1 REST 登录/注册](#21-rest-登录注册)
  - [2.2 OIDC 用户信息端点](#22-oidc-用户信息端点)
  - [2.3 客户端管理](#23-客户端管理)
  - [2.4 子系统 Token 管理](#24-子系统-token-管理)
  - [2.5 授权审计与吊销](#25-授权审计与吊销)
  - [2.6 请求日志查询](#26-请求日志查询)
- [3. 系统管理 system-server (9001)](#3-系统管理-system-server-9001)
  - [3.1 用户管理](#31-用户管理)
  - [3.2 角色管理](#32-角色管理)
  - [3.3 菜单管理](#33-菜单管理)
- [4. 审批流 auth-flow (9001)](#4-审批流-auth-flow-9001)
- [5. 消息服务 auth-message (9002)](#5-消息服务-auth-message-9002)
- [6. AI 智能体 ai-agent-server (9003)](#6-ai-智能体-ai-agent-server-9003)
  - [6.1 数据分析预警](#61-数据分析预警)
  - [6.2 健康检查](#62-健康检查)
  - [6.3 知识库管理](#63-知识库管理)
  - [6.4 智能搜索](#64-智能搜索)
- [7. 日志服务 log-server (9009)](#7-日志服务-log-server-9009)
  - [7.1 日志收集](#71-日志收集)
  - [7.2 日志查询](#72-日志查询)
  - [7.3 AI 日志分析](#73-ai-日志分析)
- [8. 网关熔断 gateway (8080)](#8-网关熔断-gateway-8080)
- [9. 前端对接指南](#9-前端对接指南)
- [10. curl 调试命令速查](#10-curl-调试命令速查)

---

## 1. 通用规范

### 1.1 响应格式

项目中存在两套响应体，分别对应不同模块：

**格式 A：`ApiResponse<T>`（auth-server / system-server）**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | 200=成功，400=参数错误，401=未认证，404=不存在，500=服务异常 |
| message | String | 提示信息 |
| data | T | 响应数据（可能是对象、数组或null） |

**格式 B：`R<T>` / `Map`（auth-flow / auth-message / ai-agent / log-server）**

```json
{
  "code": 200,
  "msg": "success",
  "data": {},
  "success": true
}
```

### 1.2 统一网关入口

```
网关地址: http://localhost:8080
所有服务通过网关代理访问，前缀与服务名一致:
  /auth-server/**    → auth-server:9000
  /system-server/**  → system-server:9001
  /auth-flow/**      → auth-flow:9001
  /auth-message/**   → auth-message:9002
  /ai-agent/**       → ai-agent-server:9003
  /log-server/**     → log-server:9009
```

### 1.3 认证方式

- **REST 接口**: `Authorization: Bearer <access_token>`
- **OAuth2 端点**: 标准 OAuth2 协议（/oauth2/token, /oauth2/authorize）
- **SSO 页面**: Session Cookie（Spring Security 管理）

### 1.4 通用数据类型约定

| 类型 | Java | JSON | 示例 |
|------|------|------|------|
| 主键ID | Long | number | `1` |
| UUID | String | string | `"a1b2c3d4e5f6"` |
| 时间 | LocalDateTime | string (ISO 8601) | `"2025-01-01T12:00:00"` |
| 布尔 | Boolean | boolean | `true` / `false` |

---

## 2. 认证服务 auth-server (9000)

### 2.1 REST 登录/注册

#### 2.1.1 密码登录

```
POST /api/auth/login
Content-Type: application/json
```

**请求参数**

| 字段 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| username | String | ✅ | 登录用户名 | `"admin"` |
| password | String | ✅ | 登录密码（明文） | `"123456"` |
| tenantId | String | ❌ | 租户ID | `"default"` |

**curl 示例**

```bash
curl -X POST http://localhost:9000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'
```

**成功响应**

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
    "refreshToken": "def50200...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "scope": "openid profile",
    "userId": 1,
    "username": "admin",
    "nickname": "系统管理员",
    "userType": "admin",
    "tenantId": "default",
    "issuedAt": "2025-01-01T12:00:00",
    "expiresAt": "2025-01-01T13:00:00"
  }
}
```

**响应字段 `LoginResponse`**

| 字段 | 类型 | 说明 |
|------|------|------|
| accessToken | String | JWT AccessToken |
| refreshToken | String | JWT RefreshToken |
| tokenType | String | 固定 `"Bearer"` |
| expiresIn | Long | Token 剩余有效秒数 |
| scope | String | 授权范围（空格分隔） |
| userId | Long | 用户主键ID |
| username | String | 用户名 |
| nickname | String | 昵称 |
| userType | String | 用户类型（admin/user/service） |
| tenantId | String | 租户ID |
| issuedAt | String (ISO) | Token 签发时间 |
| expiresAt | String (ISO) | Token 过期时间 |

**错误码**

| code | 说明 |
|------|------|
| 401 | 用户名或密码错误 |
| 400 | 参数校验失败 |

---

#### 2.1.2 刷新 Token

```
POST /api/auth/refresh
Content-Type: application/json
```

> ⚠️ **当前状态: 开发中（返回501），不可用于联调**

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| refreshToken | String | ✅ | 当前的 RefreshToken |

---

#### 2.1.3 用户注册

```
POST /api/register
Content-Type: application/json
```

**请求参数 `UserRegisterRequest`**

| 字段 | 类型 | 必填 | 校验规则 | 示例 |
|------|------|------|------|------|
| username | String | ✅ | 3-50字符 | `"zhangsan"` |
| password | String | ✅ | 6-100字符 | `"123456"` |
| nickname | String | ❌ | - | `"张三"` |
| email | String | ❌ | - | `"zhangsan@example.com"` |
| phone | String | ❌ | - | `"13800138000"` |
| userType | String | ❌ | admin/user/service | `"user"`（默认） |
| tenantId | String | ❌ | - | `"default"`（默认） |

**成功响应**

```json
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "id": 2,
    "username": "zhangsan",
    "nickname": "张三",
    "email": "zhangsan@example.com",
    "phone": "13800138000",
    "userType": "user",
    "tenantId": "default",
    "enabled": true,
    "createTime": "2025-01-01T12:00:00",
    "lastLoginTime": null
  }
}
```

---

#### 2.1.4 SSO 登录页面（服务端渲染，非 API）

```
GET /login          → Thymeleaf 登录页面 (OAuth2 授权码模式)
GET /login-success  → 登录成功回调（前端不直接调用）
GET /register       → Thymeleaf 注册页面
```

---

### 2.2 OIDC 用户信息端点

#### 2.2.1 OIDC 标准用户信息

```
GET /userinfo
Authorization: Bearer <access_token>
```

> 子系统使用 OAuth2 AccessToken 获取当前登录用户基本信息

**curl 示例**

```bash
curl -X GET http://localhost:9000/userinfo \
  -H "Authorization: Bearer eyJhbGciOi..."
```

**成功响应**

```json
{
  "sub": "admin",
  "nickname": "系统管理员",
  "user_type": "admin",
  "tenant_id": "default",
  "user_id": "1"
}
```

---

#### 2.2.2 扩展用户信息（含权限）

```
GET /api/userinfo/extended
Authorization: Bearer <access_token>
```

**响应字段 `UserInfoResponse`**

| 字段 | 类型 | 说明 |
|------|------|------|
| userId | Long | 用户ID |
| username | String | 用户名 |
| nickname | String | 昵称 |
| email | String | 邮箱 |
| phone | String | 手机号 |
| userType | String | 用户类型 |
| tenantId | String | 租户ID |
| permissions | List\<String\> | 权限标识列表 |
| accessToken | String | AccessToken（JWT原文） |
| tokenType | String | `"Bearer"` |
| expiresIn | Long | 剩余有效秒数 |
| scope | String | 授权范围 |
| issuedAt | String (ISO) | 签发时间 |
| expiresAt | String (ISO) | 过期时间 |

---

#### 2.2.3 用户权限查询

```
GET /api/userinfo/permissions
Authorization: Bearer <access_token>
```

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "username": "admin",
    "permissions": ["system:user:list", "system:user:add", "system:role:list"]
  }
}
```

---

#### 2.2.4 通过授权码查用户信息（调试用）

```
GET /api/userinfo/by-code?code=<authorization_code>&clientId=<client_id>
```

> ⚠️ 仅限内部调试，生产环境需安全控制

---

### 2.3 客户端管理

> **路径前缀**: `/api/clients`  
> **权限要求**: 管理端登录后操作（后续需加 `@RequirePermission`）

#### 2.3.1 查询所有客户端

```
GET /api/clients
```

**响应** `ApiResponse<List<ClientResponse>>`

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": "a1b2c3d4e5f6",
      "clientId": "my-mobile-app",
      "clientName": "移动端App",
      "clientIdIssuedAt": "2025-01-01T00:00:00",
      "clientSecretExpiresAt": null,
      "scopes": ["openid", "profile"],
      "grantTypes": ["authorization_code", "refresh_token"],
      "redirectUris": ["http://localhost:8080/login/oauth2/code/app"],
      "postLogoutRedirectUris": [],
      "authMethods": ["client_secret_basic"],
      "tokenTtl": 3600,
      "refreshTtl": 43200,
      "enabled": true,
      "requireAuthorizationConsent": true
    }
  ]
}
```

> 🔒 安全策略：响应中不返回 `clientSecret`

**响应字段 `ClientResponse`**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | String | 内部主键ID |
| clientId | String | 客户端唯一标识 |
| clientName | String | 客户端名称 |
| clientIdIssuedAt | String (ISO) | 签发时间 |
| clientSecretExpiresAt | String (ISO) | 密钥过期时间（null=永不过期） |
| scopes | List\<String\> | 授权范围 |
| grantTypes | List\<String\> | 授权模式 |
| redirectUris | List\<String\> | 回调地址 |
| postLogoutRedirectUris | List\<String\> | 登出回调地址 |
| authMethods | List\<String\> | 认证方式 |
| tokenTtl | Long | AccessToken 有效期（秒） |
| refreshTtl | Long | RefreshToken 有效期（秒） |
| enabled | Boolean | 是否启用 |
| requireAuthorizationConsent | Boolean | 是否需要用户授权确认 |

---

#### 2.3.2 按ID查询客户端

```
GET /api/clients/{id}
```

---

#### 2.3.3 注册新客户端

```
POST /api/clients
Content-Type: application/json
```

**请求参数 `ClientRequest`**

| 字段 | 类型 | 必填 | 校验规则 | 示例 |
|------|------|------|------|------|
| clientId | String | ✅ | 3-100字符，仅字母数字 `_-` | `"my-mobile-app"` |
| clientSecret | String | ✅ | 8-128字符 | `"my-secret-key-2024"` |
| clientName | String | ✅ | 不超过200字符 | `"移动端App"` |
| scopes | List\<String\> | ✅ | 至少含 `"openid"` | `["openid","profile","read"]` |
| grantTypes | List\<String\> | ✅ | - | `["authorization_code","refresh_token"]` |
| redirectUris | List\<String\> | ❌ | 授权码模式必填 | `["http://localhost:8080/login/oauth2/code/app"]` |
| postLogoutRedirectUris | List\<String\> | ❌ | - | `["http://localhost:8080/logout"]` |
| authMethods | List\<String\> | ✅ | - | `["client_secret_basic"]` |
| tokenTtl | Long | ❌ | 默认3600 | `7200` |
| refreshTtl | Long | ❌ | 默认43200 | `86400` |
| enabled | Boolean | ❌ | 默认true | `true` |
| clientSecretExpiresAt | String (ISO) | ❌ | null=永不过期 | `"2025-12-31T23:59:59"` |

---

#### 2.3.4 更新客户端

```
PUT /api/clients/{id}
Content-Type: application/json
```
参数同 `ClientRequest`

---

#### 2.3.5 删除客户端

```
DELETE /api/clients/{id}
```

---

#### 2.3.6 重置客户端密钥

```
PUT /api/clients/{id}/secret
Content-Type: application/json

{
  "secret": "new-secret-key-2025"
}
```

---

#### 2.3.7 客户端上下线

```
PUT /api/clients/{id}/enable     → 启用
PUT /api/clients/{id}/disable    → 禁用
```

---

#### 2.3.8 加密配置查询

```
GET /api/clients/crypto-info
```

**响应**

```json
{
  "code": 200,
  "data": {
    "currentAlgorithm": "AES",
    "supportedAlgorithms": "AES, SM4, DES"
  }
}
```

---

### 2.4 子系统 Token 管理

> **路径前缀**: `/api/subsystem`  
> **场景**: 子系统自签 JWT Token 的记录、刷新、吊销、查询

---

#### 2.4.1 记录子系统 Token

```
POST /api/subsystem/tokens
Content-Type: application/json
```

**请求参数 `SubsystemTokenRequest`**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| clientId | String | ✅ | 子系统客户端ID |
| userId | Long | ❌ | 用户ID |
| username | String | ✅ | 用户名 |
| accessToken | String | ✅ | 子系统自签的 AccessToken |
| refreshToken | String | ❌ | 子系统自签的 RefreshToken |
| tokenType | String | ❌ | 默认 `"JWT_BEARER"` |
| accessTokenExpiresAt | String (ISO) | ❌ | AccessToken 过期时间 |
| refreshTokenExpiresAt | String (ISO) | ❌ | RefreshToken 过期时间 |
| issuedIp | String | ❌ | 签发IP |
| userAgent | String | ❌ | User-Agent |

**响应字段 `SubsystemTokenResponse`**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | Token 记录ID |
| clientId | String | 客户端ID |
| userId | Long | 用户ID |
| username | String | 用户名 |
| accessTokenSnip | String | AccessToken 脱敏片段 |
| refreshTokenSnip | String | RefreshToken 脱敏片段 |
| tokenType | String | Token 类型 |
| accessTokenExpiresAt | String (ISO) | 过期时间 |
| refreshTokenExpiresAt | String (ISO) | 刷新Token过期时间 |
| status | String | `ACTIVE/EXPIRED/REVOKED/REFRESHED` |
| expired | Boolean | 是否已过期 |
| parentTokenId | Long | 父Token记录ID（刷新来源） |
| issuedIp | String | 签发IP |
| createTime | String (ISO) | 创建时间 |
| lastRefreshTime | String (ISO) | 最后刷新时间 |
| refreshCount | Integer | 刷新次数 |
| revokeTime | String (ISO) | 吊销时间 |
| revokeReasonDesc | String | 吊销原因 |

---

#### 2.4.2 刷新子系统 Token

```
POST /api/subsystem/tokens/refresh
```

**请求参数 `TokenRefreshRequest`**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| clientId | String | ✅ | 客户端ID |
| username | String | ✅ | 用户名 |
| oldRefreshToken | String | ✅ | 当前 RefreshToken |
| newAccessToken | String | ✅ | 新签发的 AccessToken |
| newRefreshToken | String | ✅ | 新签发的 RefreshToken |
| accessTokenExpiresAt | String (ISO) | ❌ | 新Token过期时间 |
| refreshTokenExpiresAt | String (ISO) | ❌ | 新RefreshToken过期时间 |
| issuedIp | String | ❌ | 签发IP |

---

#### 2.4.3 吊销 Token

```
POST /api/subsystem/tokens/{id}/revoke?reason=1&remark=用户登出
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| reason | int | ❌ | 1=用户登出，2=后台强制下线，3=Token过期，4=密钥轮转 |
| remark | String | ❌ | 备注 |

---

#### 2.4.4 批量吊销

```
POST /api/subsystem/tokens/revoke-batch?clientId=my-app&username=zhangsan&reason=2
```

---

#### 2.4.5 Token 查询

```
GET /api/subsystem/tokens/{id}                                  → 按ID查询
GET /api/subsystem/tokens?clientId=my-app&username=zhangsan    → 按客户端+用户查询(分页)
GET /api/subsystem/tokens/active?clientId=my-app&username=zhangsan  → 活跃Token
GET /api/subsystem/tokens/by-client/{clientId}                 → 按客户端查询(分页)
GET /api/subsystem/tokens/by-user/{username}                   → 按用户查询(分页)
```

分页参数：`limit`（默认20），`offset`（默认0）

---

#### 2.4.6 统计

```
GET /api/subsystem/stats
```

---

### 2.5 授权审计与吊销

> **路径前缀**: `/api/audit`

#### 2.5.1 授权记录查询

```
GET /api/audit/authorizations/client/{clientId}                              → 按客户端
GET /api/audit/authorizations/user/{principalName}                           → 按用户
GET /api/audit/authorizations/user/{principalName}/client/{clientId}         → 按用户+客户端
```

**响应字段 `AuthorizationRecordResponse`**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | String | 授权记录ID |
| clientId | String | 客户端ID |
| clientName | String | 客户端名称 |
| principalName | String | 授权用户 |
| authorizationGrantType | String | 授权模式 |
| authorizedScopes | List\<String\> | 授权范围 |
| accessTokenSnip | String | AccessToken脱敏 |
| accessTokenIssuedAt | String (ISO) | 签发时间 |
| accessTokenExpiresAt | String (ISO) | 过期时间 |
| accessTokenExpired | Boolean | 是否过期 |
| refreshTokenSnip | String | RefreshToken脱敏 |
| refreshTokenIssuedAt | String (ISO) | 签发时间 |
| refreshTokenExpiresAt | String (ISO) | 过期时间 |
| refreshTokenExpired | Boolean | 是否过期 |

---

#### 2.5.2 强制吊销 Token

```
POST /api/audit/revoke
Content-Type: application/json
```

**请求参数 `RevokeLogRequest`**

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|------|------|------|
| userId | String | ✅ | - | 被吊销的用户ID |
| clientId | String | ✅ | - | 被吊销的客户端ID |
| tokenType | String | ❌ | 默认 `"ALL"` | ACCESS_TOKEN / REFRESH_TOKEN / ALL |
| revokeType | Integer | ✅ | 1-3 | 1=用户登出 2=强制下线 3=凭证失效 |
| remark | String | ❌ | ≤255字符 | 吊销原因备注 |

---

#### 2.5.3 吊销日志查询

```
GET /api/audit/revoke-logs?limit=20&offset=0                      → 分页查询
GET /api/audit/revoke-logs/user/{userId}                          → 按用户
GET /api/audit/revoke-logs/client/{clientId}                      → 按客户端
GET /api/audit/revoke-logs/type/{revokeType}                      → 按类型（1/2/3）
```

**响应字段 `RevokeLogResponse`**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 日志ID |
| userId | String | 被吊销用户ID |
| clientId | String | 客户端ID |
| clientName | String | 客户端名称 |
| tokenType | String | 令牌类型 |
| tokenSnip | String | Token 脱敏片段 |
| revokeType | Integer | 1=登出 2=强制下线 3=凭证失效 |
| revokeTypeDesc | String | 类型描述（中文） |
| createTime | String (ISO) | 吊销时间 |
| remark | String | 备注 |

---

#### 2.5.4 门户概览

```
GET /api/audit/dashboard                       → 总概览（客户端总数/授权记录总数/吊销日志总数）
GET /api/audit/dashboard/client/{clientId}     → 客户端活跃Token统计
```

---

### 2.6 请求日志查询

> **路径前缀**: `/api/logs`

#### 2.6.1 分页查询

```
GET /api/logs?username=admin&requestUri=/api/&httpMethod=POST&httpStatus=200&success=true&startTime=2025-01-01T00:00:00&endTime=2025-01-01T23:59:59&page=0&size=20
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | String | ❌ | 用户名 |
| requestUri | String | ❌ | 请求URI（模糊匹配） |
| httpMethod | String | ❌ | HTTP 方法 |
| httpStatus | Integer | ❌ | HTTP 状态码 |
| success | Boolean | ❌ | 是否成功 |
| startTime | String (ISO) | ❌ | 开始时间 |
| endTime | String (ISO) | ❌ | 结束时间 |
| page | int | ❌ | 页码（从0开始，默认0） |
| size | int | ❌ | 每页条数（默认20） |

**响应**

```json
{
  "code": 200,
  "data": {
    "total": 150,
    "page": 0,
    "size": 20,
    "list": [...]
  }
}
```

---

#### 2.6.2 按 TraceId 查链路

```
GET /api/logs/trace/{traceId}
```

#### 2.6.3 24小时统计

```
GET /api/logs/stats
```

#### 2.6.4 清理过期日志

```
DELETE /api/logs/clean?before=2025-01-01T00:00:00
```

---

## 3. 系统管理 system-server (9001)

> **统一响应格式**: `ApiResponse<T>`（与 auth-server 一致）  
> **权限控制**: 各接口标注了 `@RequirePermission`  
> **认证方式**: `Authorization: Bearer <access_token>`

### 3.1 用户管理

> **路径前缀**: `/api/users`

#### 3.1.1 当前用户信息

```
GET /api/users/current         → 当前登录用户信息（无需额外权限）
GET /api/users/current/menus   → 当前用户可访问的菜单树
```

**响应字段 `CurrentUserResponse`**

| 字段 | 类型 | 说明 |
|------|------|------|
| username | String | 用户名（登录账号） |
| nickname | String | 昵称 |
| userId | Long | 用户ID |
| tenantId | String | 租户ID |
| userType | String | 用户类型 |
| roles | List\<String\> | 角色编码列表 |
| permissions | List\<String\> | 权限标识列表 |

---

#### 3.1.2 用户管理（需要权限）

```
GET /api/users                            → 查询所有用户         [权限: system:user:list]
GET /api/users/{id}                       → 按ID查询用户          [权限: system:user:list]
PUT /api/users/{id}/enable                → 启用用户              [权限: system:user:edit]
PUT /api/users/{id}/disable               → 禁用用户              [权限: system:user:edit]
DELETE /api/users/{id}                    → 删除用户              [权限: system:user:delete]
```

**响应字段 `UserResponse`**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 用户ID |
| username | String | 用户名 |
| nickname | String | 昵称 |
| email | String | 邮箱 |
| phone | String | 手机号 |
| userType | String | 用户类型 |
| tenantId | String | 租户ID |
| enabled | Boolean | 是否启用 |
| roleCodes | List\<String\> | 角色编码列表 |
| createTime | String (ISO) | 创建时间 |
| lastLoginTime | String (ISO) | 最后登录时间 |

---

#### 3.1.3 角色分配

```
GET /api/users/{id}/roles                → 查询用户角色          [权限: system:user:list]
PUT /api/users/{id}/roles                → 分配用户角色          [权限: system:user:edit]
```

**分配角色请求 `UserAssignRoleRequest`**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| roleIds | List\<Long\> | ✅ | 角色ID列表 |

```json
{
  "roleIds": [1, 2, 3]
}
```

---

### 3.2 角色管理

> **路径前缀**: `/api/roles`

```
GET /api/roles                            → 查询所有角色          [权限: system:role:list]
GET /api/roles/{id}                       → 按ID查询角色          [权限: system:role:list]
POST /api/roles                           → 创建角色              [权限: system:role:add]
PUT /api/roles/{id}                       → 更新角色              [权限: system:role:edit]
PUT /api/roles/{id}/enable                → 启用角色              [权限: system:role:edit]
PUT /api/roles/{id}/disable               → 禁用角色              [权限: system:role:edit]
DELETE /api/roles/{id}                    → 删除角色              [权限: system:role:delete]
PUT /api/roles/{id}/menus                 → 分配菜单权限          [权限: system:role:edit]
```

**创建/更新角色请求 `RoleRequest`**

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|------|------|------|
| roleCode | String | ✅ | 2-50字符 | 角色编码（唯一） |
| roleName | String | ✅ | ≤50字符 | 角色名称 |
| description | String | ❌ | - | 角色描述 |
| sortOrder | Integer | ❌ | - | 排序号 |
| menuIds | List\<Long\> | ❌ | - | 关联菜单ID列表 |

**响应字段 `RoleResponse`**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 角色ID |
| roleCode | String | 角色编码 |
| roleName | String | 角色名称 |
| description | String | 描述 |
| sortOrder | Integer | 排序号 |
| enabled | Boolean | 是否启用 |
| menuIds | List\<Long\> | 关联菜单ID列表 |
| createTime | String (ISO) | 创建时间 |
| updateTime | String (ISO) | 更新时间 |

---

**分配菜单权限请求**

```
PUT /api/roles/{id}/menus
Content-Type: application/json

{
  "menuIds": [1, 2, 3, 4, 5]
}
```

---

### 3.3 菜单管理

> **路径前缀**: `/api/menus`

```
GET /api/menus/tree                       → 获取完整菜单树        [权限: system:menu:list]
GET /api/menus/{id}                       → 按ID查询菜单          [权限: system:menu:list]
POST /api/menus                           → 创建菜单              [权限: system:menu:add]
PUT /api/menus/{id}                       → 更新菜单              [权限: system:menu:edit]
PUT /api/menus/{id}/enable                → 启用菜单              [权限: system:menu:edit]
PUT /api/menus/{id}/disable               → 禁用菜单              [权限: system:menu:edit]
DELETE /api/menus/{id}                    → 删除菜单              [权限: system:menu:delete]
```

**创建/更新菜单请求 `MenuRequest`**

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|------|------|------|
| parentId | Long | ❌ | 默认0 | 父菜单ID（0=顶级） |
| menuName | String | ✅ | ≤50字符 | 菜单名称 |
| menuType | Integer | ✅ | - | 0=目录 1=菜单 2=按钮 |
| path | String | ❌ | - | 路由路径 |
| component | String | ❌ | - | 组件路径 |
| permission | String | ❌ | 按钮类型必填 | 权限标识 |
| icon | String | ❌ | - | 图标 |
| sortOrder | Integer | ❌ | - | 排序号 |
| isFrame | Boolean | ❌ | 默认false | 是否外链 |
| autoAssignRoleIds | List\<Long\> | ❌ | - | 自动分配的角色ID |

---

**响应字段 `MenuResponse`**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 菜单ID |
| parentId | Long | 父菜单ID |
| menuName | String | 菜单名称 |
| menuType | Integer | 0=目录 1=菜单 2=按钮 |
| path | String | 路由路径 |
| component | String | 组件路径 |
| permission | String | 权限标识 |
| icon | String | 图标 |
| sortOrder | Integer | 排序号 |
| enabled | Boolean | 是否启用 |
| isFrame | Boolean | 是否外链 |
| children | List\<MenuResponse\> | 子菜单（树形结构） |
| createTime | String (ISO) | 创建时间 |

---

## 4. 审批流 auth-flow (9001)

> **路径前缀**: `/api/workflow`  
> **响应格式**: `R<T>`（code=200=成功）

#### 4.1 工作流定义管理

```
GET  /api/workflow/definition/list          → 查询所有工作流定义
GET  /api/workflow/definition/{id}          → 查询定义详情(含审批节点)
POST /api/workflow/definition               → 创建工作流定义
PUT  /api/workflow/definition/{id}          → 更新工作流定义(含节点)
DELETE /api/workflow/definition/{id}        → 删除工作流定义
PUT  /api/workflow/definition/{id}/status?enabled=true|false  → 启用/停用定义
```

**定义详情响应 `DefinitionDetailVO`**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 定义ID |
| definitionKey | String | 流程Key |
| definitionName | String | 流程名称 |
| description | String | 描述 |
| category | String | 分类 |
| version | Integer | 版本号 |
| status | Integer | 状态(0-草稿/1-启用/2-停用) |
| createTime | String | 创建时间 |
| nodes | List\<NodeVO\> | 审批节点列表 |

**创建/更新请求 `WorkflowCreateRequest`**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| definitionKey | String | ✅ | 流程Key（唯一，更新时不可修改） |
| definitionName | String | ✅ | 流程名称 |
| description | String | ❌ | 描述 |
| category | String | ❌ | 分类：PERMISSION/RESOURCE/ROLE |
| nodes | List\<NodeRequest\> | ✅ | 审批节点列表 |

**节点定义 `NodeRequest`**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| nodeName | String | ❌ | 节点名称，如"部门经理审批" |
| nodeType | String | ❌ | APPROVAL/CONDITION/START/END（默认APPROVAL） |
| execMode | String | ❌ | SERIAL(串行)/PARALLEL(并行) |
| conditionExpression | String | ❌ | SpEL条件表达式（CONDITION节点必填） |
| onConditionFail | String | ❌ | REJECT(驳回)/SKIP(跳过) |
| approverStrategy | String | ❌ | SPECIFIC/ROLE_BASED/DEPARTMENT_LEADER（默认SPECIFIC） |
| approvers | String | ❌ | 指定审批人，多个用逗号分隔 |
| approverRole | String | ❌ | 审批角色 |
| sortOrder | Integer | ❌ | 排序号，决定审批顺序 |
| timeoutHours | Integer | ❌ | 超时时间(小时, 0=不限时) |
| countersign | Boolean | ❌ | 会签模式(true=所有人通过才流转) |
| rejectStrategy | String | ❌ | 驳回策略: TO_PREV(回退上一节点)/TO_START(退回发起人) |

---

#### 4.2 申请与审批

```
POST /api/workflow/submit?definitionKey=xxx&title=权限申请&applyContent={...}&applicant=zhangsan
POST /api/workflow/approve                                             → 审批操作
GET  /api/workflow/{instanceId}                                        → 流程详情
GET  /api/workflow/my-applications?applicant=zhangsan                  → 我的申请
GET  /api/workflow/my-pending?approver=lisi                            → 待我审批
GET  /api/workflow/pending-count                                       → 待审批总数
```

**审批操作 `ApprovalRequest`**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| taskId | Long | ✅ | 审批任务ID |
| action | String | ✅ | APPROVE/REJECT/TRANSFER |
| comment | String | ❌ | 审批意见 |
| transferTo | String | ❌ | 转交目标人（action=TRANSFER时必填） |

---

## 5. 消息服务 auth-message (9002)

> **路径前缀**: `/api/message`  
> **响应格式**: `R<T>`

#### 5.1 消息发送

```
POST /api/message/send                  → 发送消息（单发/群发）
POST /api/message/broadcast             → 广播消息到多个子系统
POST /api/message/subsystem/upward      → 子系统上行通信
POST /api/message/event/push            → 事件推送
```

**发送请求 `MessageSendRequest`**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| messageType | String | ✅ | SYSTEM_NOTICE/EVENT_PUSH/APPROVAL_NOTIFY/SUBSYSTEM_COMM/USER_MESSAGE |
| title | String | ✅ | 消息标题 |
| content | String | ✅ | 消息内容 |
| channels | String | ❌ | 发送渠道：SMS/EMAIL/IN_APP/WEBSOCKET/MQ（逗号分隔） |
| sourceSystem | String | ❌ | 来源系统 |
| sender | String | ❌ | 发送者 |
| receivers | List\<String\> | ❌ | 接收者列表 |
| businessId | String | ❌ | 业务关联ID |
| templateCode | String | ❌ | 消息模板编码 |
| templateVars | Map\<String,String\> | ❌ | 模板变量 |

**广播请求 `MessageBroadcastRequest`**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| messageType | String | ✅ | 消息类型 |
| title | String | ✅ | 广播标题 |
| content | String | ✅ | 广播内容 |
| targetSubsystems | List\<String\> | ✅ | 目标子系统列表 |
| channels | String | ❌ | 发送渠道 |
| sourceSystem | String | ❌ | 来源系统 |
| eventType | String | ❌ | 事件类型 |
| businessId | String | ❌ | 业务关联ID |
| extraData | Map\<String,Object\> | ❌ | 扩展数据 |

---

#### 5.2 消息查询

```
GET /api/message/{messageId}                       → 查询消息详情
GET /api/message/inbox/{receiver}?limit=50          → 收件箱
GET /api/message/business/{businessId}              → 按业务ID查询
GET /api/message/subsystem/{subsystem}/events       → 查询子系统事件
GET /api/message/unread-count/{receiver}            → 未读消息数
PUT /api/message/{messageId}/read                   → 标记已读
```

---

#### 5.3 消息模板

```
POST /api/message/template                  → 创建消息模板
GET  /api/message/template/list             → 查询模板列表
```

---

## 6. AI 智能体 ai-agent-server (9003)

> **路径前缀**: `/api/ai`  
> **响应格式**: `Map<String, Object>`（success + data）

### 6.1 数据分析预警

#### 6.1.1 触发分析

```
POST /api/ai/analysis/trigger              → 手动触发分析预警
POST /api/ai/analysis/query                → 即时分析问答（自然语言）
GET  /api/ai/analysis/metrics               → 获取当前关键业务指标
```

**即时分析请求**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| query | String | ❌ | 自然语言的分析问题 |

---

#### 6.1.2 预警管理

```
GET  /api/ai/analysis/alerts                        → 未处理预警列表
GET  /api/ai/analysis/alerts/level/{level}          → 按级别查询（INFO/WARN/CRITICAL）
GET  /api/ai/analysis/alerts/recent?n=20             → 最近N条预警
GET  /api/ai/analysis/alerts/summary                 → 预警汇总
PUT  /api/ai/analysis/alerts/{id}/resolve?by=admin   → 处理预警
PUT  /api/ai/analysis/alerts/{id}/read               → 标记已读
```

**预警响应 `AlertVO`**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 预警ID |
| alertName | String | 预警名称 |
| analysisType | String | 分析类型 |
| dataSource | String | 数据来源 |
| alertLevel | String | 预警级别（INFO/WARN/CRITICAL） |
| alertContent | String | 预警内容 |
| analysisDetail | String | 分析详情 |
| suggestion | String | 建议措施 |
| isRead | Boolean | 是否已读 |
| resolved | Boolean | 是否已处理 |
| resolvedAt | String (ISO) | 处理时间 |
| resolvedBy | String | 处理人 |
| createdAt | String (ISO) | 创建时间 |

---

### 6.2 健康检查

```
GET /api/ai/health/llm        → LLM 连通性检查
GET /api/ai/health/status     → 服务状态概览
```

---

### 6.3 知识库管理

```
POST   /api/ai/knowledge/doc                  → 添加文档到知识库
GET    /api/ai/knowledge/kb                    → 获取知识库列表
GET    /api/ai/knowledge/kb/{kbName}/docs      → 指定知识库文档列表
GET    /api/ai/knowledge/docs                  → 所有文档
GET    /api/ai/knowledge/doc/{id}              → 文档详情
DELETE /api/ai/knowledge/doc/{id}              → 删除文档
DELETE /api/ai/knowledge/kb/{kbName}           → 删除知识库
GET    /api/ai/knowledge/stats                 → 知识库统计
```

**添加文档请求 `KnowledgeDocRequest`**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| kbName | String | ❌ | 知识库名称 |
| title | String | ❌ | 文档标题 |
| content | String | ❌ | 文档内容 |
| contentType | String | ❌ | 内容类型 |
| fileName | String | ❌ | 文件名 |

**文档响应 `KnowledgeDocVO`**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 文档ID |
| kbName | String | 知识库名称 |
| title | String | 文档标题 |
| contentPreview | String | 内容预览（前200字符） |
| contentType | String | 内容类型 |
| fileName | String | 文件名 |
| chunkCount | Integer | 分块数量 |
| status | String | 状态 |
| createdAt | String (ISO) | 创建时间 |
| updatedAt | String (ISO) | 更新时间 |

---

### 6.4 智能搜索

```
POST /api/ai/search                    → 统一搜索入口（LOCAL/INTERNET/RAG/HYBRID）
GET  /api/ai/search/local?q=xxx        → 本地离线搜索
GET  /api/ai/search/suggest?q=xxx      → 前缀补全
GET  /api/ai/search/web?q=xxx          → 联网搜索
GET  /api/ai/search/rag?q=xxx&k=5      → RAG知识库检索
GET  /api/ai/search/hybrid?q=xxx       → 混合搜索
GET  /api/ai/search/index-stats        → 本地索引统计
```

**统一搜索请求 `SearchRequest`**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| query | String | ❌ | 搜索查询 |
| searchType | String | ❌ | LOCAL/INTERNET/RAG/HYBRID（默认HYBRID） |
| kbName | String | ❌ | 知识库名称（RAG模式时使用） |
| maxResults | Integer | ❌ | 最大返回数（默认10） |

---

## 7. 日志服务 log-server (9009)

### 7.1 日志收集

```
POST /api/logs/collect/batch              → 批量上报日志
POST /api/logs/collect/single             → 单条上报（异步）
POST /api/logs/collect/single/sync        → 单条上报（同步，确保落盘）
```

**日志条目 `LogEntry`**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| traceId | String | ❌ | 链路追踪ID |
| module | String | ❌ | 来源模块 |
| category | String | ❌ | 日志分类 |
| level | String | ❌ | 日志级别 |
| className | String | ❌ | 类名 |
| methodName | String | ❌ | 方法名 |
| message | String | ❌ | 日志消息 |
| fullMessage | String | ❌ | 完整消息 |
| exceptionStack | String | ❌ | 异常堆栈 |
| exceptionType | String | ❌ | 异常类型 |
| username | String | ❌ | 操作人 |
| clientIp | String | ❌ | 客户端IP |
| requestUri | String | ❌ | 请求URI |
| httpMethod | String | ❌ | HTTP方法 |
| httpStatus | Integer | ❌ | HTTP状态码 |
| costTime | Long | ❌ | 耗时（毫秒） |
| metadata | String | ❌ | 扩展元数据（JSON） |
| logTime | String (ISO) | ❌ | 日志产生时间 |

---

### 7.2 日志查询

```
POST /api/logs/query              → 分页查询日志
GET  /api/logs/stats              → 日志统计概览
DELETE /api/logs/clean?retentionDays=30 → 清理过期日志
```

**查询请求 `LogQueryRequest`**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|------|------|
| page | Integer | ❌ | 1 | 当前页 |
| size | Integer | ❌ | 20 | 每页大小 |
| module | String | ❌ | - | 来源模块 |
| category | String | ❌ | - | 日志分类 |
| level | String | ❌ | - | 日志级别 |
| keyword | String | ❌ | - | 关键字（消息/traceId模糊搜索） |
| exceptionType | String | ❌ | - | 异常类型 |
| errorFingerprint | String | ❌ | - | 错误指纹 |
| httpStatus | Integer | ❌ | - | HTTP状态码 |
| username | String | ❌ | - | 操作人 |
| requestUri | String | ❌ | - | 请求URI |
| startTime | String (ISO) | ❌ | - | 开始时间 |
| endTime | String (ISO) | ❌ | - | 结束时间 |
| minCostTime | Long | ❌ | - | 最小耗时（ms） |

**统计响应 `LogStatsDTO`**

| 字段 | 类型 | 说明 |
|------|------|------|
| totalCount | Long | 总日志数 |
| levelDistribution | Map\<String,Long\> | 各级别分布 |
| moduleDistribution | Map\<String,Long\> | 各模块分布 |
| categoryDistribution | Map\<String,Long\> | 各分类分布 |
| errorCount | Long | 错误数 |
| errorRate | Double | 错误率 |
| topErrorFingerprints | List\<ErrorFingerprintTop\> | TOP5 错误指纹 |
| avgCostTime | Double | 平均耗时（ms） |
| p99CostTime | Long | P99耗时（ms） |

---

### 7.3 AI 日志分析

```
GET  /api/logs/analysis/fingerprint/{fingerprint}    → 按错误指纹分析
GET  /api/logs/analysis/trace/{traceId}              → 按TraceId分析
GET  /api/logs/analysis/similar?exceptionType=xxx    → 搜索相似错误
POST /api/logs/analysis/solution                     → 保存解决方案
GET  /api/logs/analysis/solution/{fingerprint}       → 按指纹查询方案
PUT  /api/logs/analysis/solution/{fingerprint}/resolved → 标记已使用（次数+1）
GET  /api/logs/analysis/solution/top?limit=10        → 热门方案TOP N
```

**保存方案 `SolutionSaveRequest`**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| errorFingerprint | String | ❌ | 错误指纹 |
| errorPattern | String | ❌ | 错误特征描述 |
| rootCause | String | ❌ | 根因分析 |
| solution | String | ❌ | 解决方案 |
| steps | String | ❌ | 详细步骤（逗号分隔） |
| referenceUrl | String | ❌ | 参考链接 |
| status | String | ❌ | DRAFT/PUBLISHED |

---

## 8. 网关熔断 gateway (8080)

> **说明**: 以下端点仅当后端服务熔断降级时由网关自动调用，前端无需主动请求

```
/fallback/auth-server       → 授权服务降级
/fallback/auth-flow         → 审批流服务降级
/fallback/auth-message      → 消息服务降级
/fallback/ai-agent-server   → AI智能体服务降级
```

**降级响应**

```json
{
  "code": 503,
  "message": "授权服务 暂不可用，请稍后重试",
  "service": "auth-server",
  "timestamp": "2025-01-01T12:00:00",
  "data": null
}
```

---

## 9. 前端对接指南

### 9.1 Axios 封装示例（Vue3 + TypeScript）

```typescript
// api/request.ts
import axios from 'axios'
import { useUserStore } from '@/stores/user'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',       // vite代理转发
  timeout: 30000
})

// 请求拦截: 自动注入 Token
request.interceptors.request.use(config => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截: 统一错误处理 + Token 过期刷新
request.interceptors.response.use(
  response => {
    const { code, message } = response.data
    if (code === 401) {
      localStorage.removeItem('accessToken')
      router.push('/login')
      return Promise.reject(new Error('登录已过期'))
    }
    return response.data
  },
  error => Promise.reject(error)
)

export default request
```

### 9.2 Vite 代理配置

```typescript
// vite.config.ts
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',  // 网关统一入口
        changeOrigin: true
      }
    }
  }
})
```

### 9.3 TS 类型定义（关键类型）

```typescript
// types/api.d.ts

// 统一响应格式 A (auth-server / system-server)
interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

// 统一响应格式 B (其他服务)
interface R<T> {
  code: number
  msg: string
  data: T
}

// 登录请求/响应
interface LoginRequest {
  username: string
  password: string
  tenantId?: string
}

interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  scope: string
  userId: number
  username: string
  nickname: string
  userType: string
  tenantId: string
  issuedAt: string
  expiresAt: string
}

// 当前用户信息
interface CurrentUserResponse {
  username: string
  nickname: string
  userId: number
  tenantId: string
  userType: string
  roles: string[]
  permissions: string[]
}

// 菜单
interface MenuResponse {
  id: number
  parentId: number
  menuName: string
  menuType: number       // 0=目录 1=菜单 2=按钮
  path: string
  component: string
  permission: string
  icon: string
  sortOrder: number
  enabled: boolean
  isFrame: boolean
  children: MenuResponse[]
  createTime: string
}

// 角色
interface RoleResponse {
  id: number
  roleCode: string
  roleName: string
  description: string
  sortOrder: number
  enabled: boolean
  menuIds: number[]
  createTime: string
  updateTime: string
}

// 客户端
interface RedirectUriItem {
  uri: string
  platform: string     // web / mobile / miniapp / desktop
  label?: string
}

interface ClientResponse {
  id: string
  clientId: string
  clientName: string
  clientIdIssuedAt: string
  clientSecretExpiresAt: string | null
  scopes: string[]
  grantTypes: string[]
  redirectUris: RedirectUriItem[]
  postLogoutRedirectUris: RedirectUriItem[]
  authMethods: string[]
  tokenTtl: number
  refreshTtl: number
  enabled: boolean
  requireAuthorizationConsent: boolean
}

// 分页通用
interface PageResult<T> {
  total: number
  page: number
  size: number
  list: T[]
}
```

### 9.4 前端调用示例

```typescript
// api/auth.ts
import request from './request'

export const login = (data: LoginRequest) =>
  request.post<ApiResponse<LoginResponse>>('/api/auth/login', data)

export const register = (data: UserRegisterRequest) =>
  request.post<ApiResponse<UserResponse>>('/api/register', data)

export const getCurrentUser = () =>
  request.get<ApiResponse<CurrentUserResponse>>('/api/users/current')

export const getUserMenus = () =>
  request.get<ApiResponse<MenuResponse[]>>('/api/users/current/menus')

export const getClientList = () =>
  request.get<ApiResponse<ClientResponse[]>>('/api/clients')
```

---

## 10. curl 调试命令速查

```bash
# ============ auth-server ============

# 登录
curl -X POST http://localhost:9000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'

# 注册
curl -X POST http://localhost:9000/api/register \
  -H "Content-Type: application/json" \
  -d '{"username":"newuser","password":"123456","nickname":"新用户"}'

# 查询客户端列表
curl http://localhost:9000/api/clients

# 注册客户端
curl -X POST http://localhost:9000/api/clients \
  -H "Content-Type: application/json" \
  -d '{"clientId":"test-app","clientSecret":"test-secret-123","clientName":"测试应用","scopes":["openid"],"grantTypes":["authorization_code"],"authMethods":["client_secret_basic"]}'

# 获取用户扩展信息
curl http://localhost:9000/api/userinfo/extended \
  -H "Authorization: Bearer <TOKEN>"

# ============ system-server ============

# 当前用户
curl http://localhost:9001/api/users/current \
  -H "Authorization: Bearer <TOKEN>"

# 用户菜单
curl http://localhost:9001/api/users/current/menus \
  -H "Authorization: Bearer <TOKEN>"

# 用户列表
curl http://localhost:9001/api/users \
  -H "Authorization: Bearer <TOKEN>"

# 角色列表
curl http://localhost:9001/api/roles \
  -H "Authorization: Bearer <TOKEN>"

# 菜单树
curl http://localhost:9001/api/menus/tree \
  -H "Authorization: Bearer <TOKEN>"

# ============ auth-flow ============

# 查询工作流定义
curl http://localhost:9001/api/workflow/definition/list

# 发起申请
curl -X POST "http://localhost:9001/api/workflow/submit?definitionKey=PERMISSION_APPROVAL&title=权限申请&applyContent={}&applicant=admin"

# 我的待审批
curl "http://localhost:9001/api/workflow/my-pending?approver=admin"

# ============ auth-message ============

# 发送消息
curl -X POST http://localhost:9002/api/message/send \
  -H "Content-Type: application/json" \
  -d '{"messageType":"USER_MESSAGE","title":"测试","content":"测试消息内容","receivers":["admin"]}'

# ============ ai-agent-server ============

# 关键业务指标
curl http://localhost:9003/api/ai/analysis/metrics

# 即时分析问答
curl -X POST http://localhost:9003/api/ai/analysis/query \
  -H "Content-Type: application/json" \
  -d '{"query":"最近一周的活跃用户增长趋势"}'

# 知识库列表
curl http://localhost:9003/api/ai/knowledge/kb

# ============ log-server ============

# 日志统计
curl http://localhost:9009/api/logs/stats

# 查询日志
curl -X POST http://localhost:9009/api/logs/query \
  -H "Content-Type: application/json" \
  -d '{"page":1,"size":20,"level":"ERROR"}'
```

---

> **文档维护**: 接口变更时请同步更新本文档，确保前后端始终对齐。  
> **Swagger 文档**: 各服务启动后可访问 `http://服务地址:端口/swagger-ui.html` 查看在线 Swagger 文档。  
> **聚合文档**: 访问 `http://localhost:10909/doc.html` 可查看所有服务的统一接口文档。
