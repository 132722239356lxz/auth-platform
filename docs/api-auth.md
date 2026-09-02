# 认证中心 REST API 接口文档

> **服务地址**：`http://127.0.0.1:9000`（开发环境）
> **Base URL**：`/api/auth`
> **更新日期**：2026-07-10

---

## 通用说明

### 响应格式

所有接口统一返回 `ApiResponse<T>` 结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { ... }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | 业务状态码：`200` 成功，`400` 参数错误，`401` 未认证，`403` 禁止，`423` 锁定，`500` 服务异常 |
| message | string | 提示信息 |
| data | object/null | 响应数据，失败时为 `null` |

### 认证方式

登录成功后，前端需在**所有请求的 Header** 中携带 Token：

```
Authorization: Bearer {access_token}
```

> ⚠️ 注意：`Bearer` 后面有一个空格。

---

## 1. 密码登录

用户通过用户名/手机号 + 密码登录，获取 JWT Token。

**基本信息**

| 项目 | 值 |
|------|-----|
| 路径 | `POST /api/auth/login` |
| Content-Type | `application/json` |
| 认证 | 无需认证（公开接口） |

### 请求参数（Body JSON）

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| username | string | 条件必填 | - | 登录用户名（与 phone 二选一） |
| phone | string | 条件必填 | - | 手机号（与 username 二选一） |
| password | string | ✅ | - | 登录密码（明文传输，后端 BCrypt 校验） |
| clientId | string | ❌ | `admin-web` | 前端应用标识 |
| tenantId | string | ❌ | `default` | 租户 ID（多租户场景） |

### 请求示例

```bash
# 用户名登录
curl -X POST 'http://127.0.0.1:9000/api/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{
    "username": "admin",
    "password": "admin123",
    "clientId": "admin-web"
  }'

# 手机号登录
curl -X POST 'http://127.0.0.1:9000/api/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{
    "phone": "13800138000",
    "password": "admin123",
    "clientId": "admin-web"
  }'
```

### 成功响应（200）

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "access_token": "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsInRlbmFudF9pZCI6ImRlZmF1bHQiLCJ1c2VyX2lkIjoiMSIsInVzZXJfdHlwZSI6ImFkbWluIiwibmlja25hbWUiOiLns7vnu5_nrqHnkIblkZgiLCJwZXJtaXNzaW9ucyI6WyJzeXNDdXNMMDAiLCJzeXNDdXNMMDEiXSwiaXNzIjoiaHR0cDovLzEyNy4wLjAuMTo5MDAwIiwiZXhwIjoxNzUyMTU4ODAwLCJpYXQiOjE3NTIxNTUyMDB9.xxx",
    "refresh_token": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6",
    "token_type": "Bearer",
    "expires_in": 3600,
    "expires_at": 1752158800000,
    "scope": "openid profile read write",
    "user_info": {
      "userId": "1",
      "username": "admin",
      "nickname": "系统管理员",
      "userType": "admin",
      "tenantId": "default",
      "email": "admin@example.com",
      "phone": "13800138000"
    },
    "permissions": [
      "sys:user:list",
      "sys:user:add",
      "sys:user:edit",
      "sys:user:delete",
      "sys:role:list",
      "sys:menu:list"
    ]
  }
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| data.access_token | string | JWT AccessToken，后续请求放 Authorization Header |
| data.refresh_token | string | RefreshToken，access_token 过期后用于刷新 |
| data.token_type | string | 固定值 `Bearer` |
| data.expires_in | number | AccessToken 有效时长（秒），默认 3600（1小时） |
| data.expires_at | number | AccessToken 过期时间戳（毫秒），可用于前端判断是否需要刷新 |
| data.scope | string | 授权范围，空格分隔 |
| data.user_info.userId | string | 用户 ID |
| data.user_info.username | string | 用户名 |
| data.user_info.nickname | string | 用户昵称 |
| data.user_info.userType | string | 用户类型：`admin` 管理员 / `user` 普通用户 |
| data.user_info.tenantId | string | 所属租户 |
| data.user_info.email | string | 邮箱（可能为 null） |
| data.user_info.phone | string | 手机号（可能为 null） |
| data.permissions | string[] | 用户拥有的权限标识列表 |

### 前端存储建议

```typescript
// 登录成功后存储
localStorage.setItem('access_token', res.data.access_token);
localStorage.setItem('refresh_token', res.data.refresh_token);
localStorage.setItem('user_info', JSON.stringify(res.data.user_info));
localStorage.setItem('permissions', JSON.stringify(res.data.permissions));
```

### 错误响应

| code | message | 说明 |
|------|---------|------|
| 400 | 请提供手机号或用户名 / 密码不能为空 | 参数校验失败 |
| 400 | 客户端不存在: xxx | clientId 未注册 |
| 401 | 账号或密码错误 | 用户名/手机号不存在或密码不匹配 |
| 403 | 账号已被禁用，请联系管理员 | 账号被管理员禁用 |
| 403 | 普通用户无法登录后台管理系统 | 仅拥有 ROLE_USER 角色的用户禁止登录管理后台 |
| 423 | 账号已被锁定，请联系管理员 | 账号被锁定（如多次密码错误） |
| 500 | 登录失败，服务内部异常 | 服务端未知错误 |

```json
// 登录失败示例
{
  "code": 401,
  "message": "账号或密码错误",
  "data": null
}
```

---

## 2. 发送短信验证码

向指定手机号发送 6 位数字验证码，存入 Redis（5 分钟有效），登录时校验。

> 💡 验证码通过日志输出（`log.info`），接入真实短信通道后替换为调用 `SmsProvider.send()`。

**基本信息**

| 项目 | 值 |
|------|-----|
| 路径 | `POST /api/auth/sms/send` |
| Content-Type | `application/json` |
| 认证 | 无需认证（公开接口） |

### 请求参数（Body JSON）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| phone | string | ✅ | 手机号，须满足 `1[3-9]\\d{9}` 格式 |

### 请求示例

```bash
curl -X POST 'http://127.0.0.1:9000/api/auth/sms/send' \
  -H 'Content-Type: application/json' \
  -d '{"phone": "13800138000"}'
```

### 成功响应（200）

```json
{ "code": 200, "message": "验证码已发送", "data": null }
```

### 错误响应

| code | message | 说明 |
|------|---------|------|
| 400 | 手机号不能为空 | phone 缺失 |
| 400 | 手机号格式不正确 | 格式不匹配 |
| 400 | 短信发送过于频繁，请N秒后重试 | 60 秒内重复请求 |
| 400 | 今日短信发送次数已达上限(10次) | 单日超限 |

---

## 3. 短信验证码登录

用户通过手机号+短信验证码登录，验证码校验通过后签发 JWT Token。

> 🔐 验证码从 Redis 读取并校验，通过后立即删除，一次性使用，防重放。

**基本信息**

| 项目 | 值 |
|------|-----|
| 路径 | `POST /api/auth/login` |
| Content-Type | `application/json` |
| 认证 | 无需认证（公开接口） |

### 请求参数（Body JSON）

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| phone | string | ✅ | - | 手机号 |
| smsCode | string | ✅ | - | 6 位短信验证码 |
| loginType | string | ✅ | `SMS` | 必须为 `SMS` |
| clientId | string | ❌ | `admin-web` | 前端应用标识 |

### 请求示例

```bash
# 步骤1: 先获取验证码
curl -X POST 'http://127.0.0.1:9000/api/auth/sms/send' \
  -H 'Content-Type: application/json' \
  -d '{"phone": "13800138000"}'

# 步骤2: 使用验证码登录
curl -X POST 'http://127.0.0.1:9000/api/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{
    "phone": "13800138000",
    "smsCode": "123456",
    "loginType": "SMS",
    "clientId": "admin-web"
  }'
```

### 成功响应（200）

响应结构与密码登录一致，返回 `access_token` + `refresh_token` + 用户信息。

### 错误响应

| code | message | 说明 |
|------|---------|------|
| 400 | 短信验证码登录需要提供手机号 | phone 为空 |
| 400 | 短信验证码不能为空 | smsCode 为空 |
| 401 | 验证码错误或已过期，请重新获取 | Redis 中无此验证码或不匹配 |
| 401 | 该手机号未注册 | 手机号不在系统中 |
| 403 | 账号已被禁用/普通用户无法登录后台 | 同密码登录 |

---

## 4. 刷新 Token

当 `access_token` 即将过期或已过期时，使用 `refresh_token` 换取新的 Token。

**基本信息**

| 项目 | 值 |
|------|-----|
| 路径 | `POST /api/auth/refresh` |
| Content-Type | `application/json` |
| 认证 | 无需认证（公开接口） |

### 请求参数（Body JSON）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| refresh_token | string | ✅ | 登录时获取的 refresh_token |

### 请求示例

```bash
curl -X POST 'http://127.0.0.1:9000/api/auth/refresh' \
  -H 'Content-Type: application/json' \
  -d '{
    "refresh_token": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6"
  }'
```

### 成功响应（200）

```json
{
  "code": 200,
  "message": "刷新成功",
  "data": {
    "access_token": "eyJhbGci...新token...",
    "refresh_token": "新的refresh_token...",
    "token_type": "Bearer",
    "expires_in": 3600,
    "expires_at": 1752158800000,
    "scope": "openid profile read write",
    "user_info": {
      "userId": "1",
      "username": "admin",
      "nickname": "系统管理员",
      "userType": "admin",
      "tenantId": "default",
      "email": null,
      "phone": null
    },
    "permissions": ["sys:user:list", "sys:role:list"]
  }
}
```

> 💡 **刷新成功后**，旧 `refresh_token` 立即作废，需用新的 `refresh_token` 进行下次刷新。前端应更新 localStorage 中存储的两个 token。

### 错误响应

| code | message | 说明 |
|------|---------|------|
| 400 | refresh_token不能为空 | 参数缺失 |
| 401 | refresh_token无效或已过期，请重新登录 | Token 无效或已使用过 |
| 401 | 用户不存在 | 关联用户已被删除 |
| 403 | 账号已被禁用/锁定 | 用户状态异常 |

---

## 5. 前端完整对接示例

### Axios 封装

```typescript
// api/request.ts
import axios from 'axios';
import router from '@/router';

const request = axios.create({
  baseURL: 'http://127.0.0.1:9000',
  timeout: 15000,
});

// 请求拦截器：自动附带 Token
request.interceptors.request.use(config => {
  const token = localStorage.getItem('access_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 响应拦截器：Token 过期自动刷新
let isRefreshing = false;
let pendingRequests: Array<() => void> = [];

request.interceptors.response.use(
  response => {
    const { code, message, data } = response.data;
    if (code !== 200) {
      console.error(`[API Error] ${code}: ${message}`);
    }
    return response.data;
  },
  async error => {
    if (error.response?.status === 401) {
      // Token 过期，尝试刷新
      const refreshToken = localStorage.getItem('refresh_token');
      if (!refreshToken) {
        router.push('/login');
        return Promise.reject(error);
      }

      if (!isRefreshing) {
        isRefreshing = true;
        try {
          const res = await axios.post('/api/auth/refresh', { refresh_token: refreshToken });
          const { access_token, refresh_token } = res.data.data;
          localStorage.setItem('access_token', access_token);
          localStorage.setItem('refresh_token', refresh_token);
          // 重试挂起的请求
          pendingRequests.forEach(cb => cb());
          pendingRequests = [];
          // 重试当前请求
          error.config.headers.Authorization = `Bearer ${access_token}`;
          return axios(error.config);
        } catch {
          localStorage.clear();
          router.push('/login');
        } finally {
          isRefreshing = false;
        }
      } else {
        // 已有刷新进行中，排队等待
        return new Promise(resolve => {
          pendingRequests.push(() => {
            error.config.headers.Authorization = `Bearer ${localStorage.getItem('access_token')}`;
            resolve(axios(error.config));
          });
        });
      }
    }
    return Promise.reject(error);
  }
);

export default request;
```

### 登录页面调用

```typescript
// views/Login.vue
import request from '@/api/request';

async function handleLogin() {
  try {
    const res = await request.post('/api/auth/login', {
      username: formData.username,
      password: formData.password,
      clientId: 'admin-web',
    });

    if (res.code === 200) {
      const { access_token, refresh_token, user_info, permissions } = res.data;

      // 持久化存储
      localStorage.setItem('access_token', access_token);
      localStorage.setItem('refresh_token', refresh_token);
      localStorage.setItem('user_info', JSON.stringify(user_info));
      localStorage.setItem('permissions', JSON.stringify(permissions));

      // 跳转首页
      router.push('/dashboard');
    } else {
      showError(res.message);
    }
  } catch (err) {
    showError('网络异常，请稍后重试');
  }
}
```

### 业务 API 调用

```typescript
// 获取用户列表（需登录后调用）
import request from '@/api/request';

const userList = await request.get('/system-server/api/users?page=1&size=20');
// 自动附带 Authorization Header，无需手动处理
```

---

## 6. JWT Token 结构（参考）

JWT Payload 中包含以下自定义字段（Claims），前端可按需使用：

| Claim | 说明 |
|-------|------|
| sub | 用户名 |
| user_id | 用户 ID |
| user_type | 用户类型（admin/user） |
| tenant_id | 租户 ID |
| nickname | 用户昵称 |
| permissions | 权限标识数组 |
| iss | 签发者 |
| iat | 签发时间 |
| exp | 过期时间 |

前端可直接从 JWT 中解码 payload 获取用户信息（无需额外请求），但**不要**信任前端解码的内容作为权限判断依据，后端会校验每个请求的 Token 和权限。

---

## 7. 常见问题

**Q: access_token 过期了怎么办？**
A: 调用 `/api/auth/refresh` 用 `refresh_token` 换取新的 `access_token`。请在前端封装中实现自动刷新逻辑（参考第 5 节示例）。

**Q: Token 应该存在哪里？**
A: 推荐 `localStorage`（SPA 应用），注意防范 XSS 攻击。对于高安全场景可考虑 `httpOnly cookie` + CSRF Token 方案。

**Q: 如何退出登录？**
A: 前端清除 `localStorage` 中的 Token 和用户信息即可，无需调用后端接口。如需要服务端注销 Token，可调用 `/oauth2/revoke` 端点。

**Q: 权限标识 `permissions` 怎么用？**
A: 前端可用于判断是否显示某个按钮/菜单，例如 `permissions.includes('sys:user:delete')` 决定是否显示"删除用户"按钮。但**最终权限控制由后端负责**。
