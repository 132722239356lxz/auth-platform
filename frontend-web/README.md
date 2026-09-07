# 统一授权中台 - 前端项目

基于后端 Spring Authorization Server 统一授权中台的完整前端实现，包含 Web 管理端和 App 移动端。

## 项目结构

```
auth-platform/
├── frontend-web/          # Web 管理端 (Vue3 + Vite + Element Plus)
├── frontend-app/          # 移动端 (UniApp + Vue3)
├── auth-server/           # 后端 - OAuth2授权服务
├── auth-flow/             # 后端 - 工作流审批
├── auth-message/          # 后端 - 消息广播
├── ai-agent-server/       # 后端 - AI智能体
├── gateway/               # 后端 - API网关
├── common-core/           # 后端 - 公共模块
└── ...
```

## 一、Web 管理端 (frontend-web)

### 技术栈
- Vue 3.4 (Composition API)
- Vite 5.2
- TypeScript 5.4
- Element Plus 2.6
- Pinia 2.1
- Vue Router 4.3
- Axios 1.6
- ECharts 5.5

### 快速启动

```bash
cd frontend-web
npm install
npm run dev
```

访问地址: http://localhost:5173

### 功能模块

| 模块 | 页面 | 对接后端API |
|------|------|-------------|
| 登录 | SSO登录 / 开发模式登录 | OAuth2 授权码流程 |
| 仪表盘 | 统计概览、快捷入口、系统信息 | /api/audit/dashboard, /api/subsystem/stats |
| 用户管理 | 用户列表、启禁用、分配角色 | /api/users |
| 角色管理 | 角色CRUD、分配菜单权限 | /api/roles |
| 菜单管理 | 菜单树CRUD（目录/菜单/按钮） | /api/menus |
| 客户端管理 | OAuth2客户端注册/编辑/启禁用/重置密钥 | /api/clients |
| 授权审计 | 概览统计、吊销日志、强制吊销Token | /api/audit |
| 子系统Token | Token查询、吊销 | /api/subsystem/tokens |
| 工作流审批 | 我的申请、待我审批、流程详情、发起申请 | /api/workflow |
| 消息中心 | 收件箱、发送消息（单发/广播）、消息模板 | /api/message |
| AI智能搜索 | 本地/联网/RAG/混合搜索 | /api/ai/search |
| 知识库管理 | 知识库列表、文档CRUD | /api/ai/knowledge |
| 数据预警 | 预警列表、处理、关键指标 | /api/ai/analysis |
| 日志查询 | 分页查询、多条件筛选 | /api/logs |
| AI日志分析 | 错误指纹分析、TraceId分析、相似错误搜索 | /api/logs/analysis |

### 登录方式

1. **SSO 登录**: 点击"前往SSO登录"按钮，跳转到 auth-server OAuth2 授权页面，完成授权码流程后自动回调
2. **开发模式**: 输入任意用户名/密码即可进入系统，用于前端独立调试（API请求会代理到Gateway但无Token验证）

### API代理配置

Vite 开发服务器已配置代理，所有以 `/auth-server`、`/system-server` 等开头的请求会自动代理到 `http://127.0.0.1:8080`（Gateway）。

修改 `.env.development` 中的 `VITE_GATEWAY_URL` 可更改目标地址。

## 二、App 移动端 (frontend-app)

### 技术栈
- UniApp 3.0 (Vue 3 语法)
- Pinia 2.1
- 支持 H5 / 微信小程序 / App 三端

### 快速启动

```bash
cd frontend-app
npm install
npm run dev:h5      # H5 模式
npm run dev:mp-weixin  # 微信小程序模式
npm run dev:app     # App 模式
```

H5 访问地址: http://localhost:5174

### 功能模块

| 页面 | 功能 | 对接后端API |
|------|------|-------------|
| 登录 | 用户名密码登录（开发模式） | 模拟JWT Token |
| 首页 | 统计卡片、快捷功能、系统信息 | /api/audit/dashboard, /api/workflow/my-pending |
| 审批列表 | 我的申请/待我审批 | /api/workflow/my-applications, my-pending |
| 流程详情 | 审批记录、通过/驳回/转交 | /api/workflow/{id}, /api/workflow/approve |
| 发起申请 | 选择流程、填写申请 | /api/workflow/submit |
| 消息列表 | 收件箱、未读标记 | /api/message/inbox |
| 消息详情 | 消息内容、标记已读 | /api/message/{id} |
| AI搜索 | 本地/联网/RAG/混合搜索 | /api/ai/search |
| 知识库 | 知识库列表、文档查看 | /api/ai/knowledge |
| 设置 | 用户信息、权限列表、退出登录 | /api/userinfo/extended |

### TabBar 底部导航

- 首页: 统计概览 + 快捷入口
- 审批: 工作流审批列表
- 消息: 站内消息收件箱
- 我的: 个人设置

## 三、后端服务对照

| 后端服务 | 端口 | 前端对接 |
|---------|------|---------|
| Gateway | 8080 | 所有 API 请求通过网关路由 |
| Auth-Server | 9000 | 登录认证、客户端管理、授权审计 |
| System-Server | 9001 | 用户/角色/菜单管理 |
| Auth-Flow | 9001 | 工作流审批 |
| Auth-Message | 9002 | 消息广播 |
| AI-Agent-Server | 9003 | AI搜索/知识库/预警 |
| Log-Server | 9009 | 日志查询/分析 |

## 四、认证流程

```
前端 → [SSO登录按钮] → 跳转 auth-server/oauth2/authorize
                         ↓
                    用户输入账号密码
                         ↓
                    授权确认 → 重定向回前端?code=xxx
                         ↓
前端 ← [回调页面] → POST /oauth2/token (code换token)
                         ↓
                    存储 access_token
                         ↓
              后续请求携带 Authorization: Bearer {token}
```

## 五、统一响应格式

```json
// auth-server / system-server
{ "code": 200, "message": "操作成功", "data": {} }

// auth-flow / auth-message
{ "code": 200, "msg": "success", "data": {} }
```

## 六、注意事项

1. **后端未启动时**: 前端可正常启动和浏览页面，但 API 请求会失败（显示空数据或错误提示）
2. **开发模式登录**: 不需要后端服务，使用模拟 JWT Token，适用于前端 UI 开发调试
3. **SSO 登录**: 需要后端 Gateway + Auth-Server 运行，且已注册 OAuth2 客户端
4. **CORS**: 开发环境通过 Vite proxy 代理解决跨域问题
5. **权限控制**: 前端使用 `v-permission` 指令控制按钮/元素显示，后端使用 `@RequirePermission` 注解做最终拦截
