# Auth-Platform 架构文档

## 技术栈 (v2.0)

| 层级 | 技术选型 | 版本 |
|------|---------|------|
| 语言 | Java | 17 |
| 框架 | Spring Boot | 3.2.5 |
| 微服务 | Spring Cloud | 2023.0.1 |
| 注册/配置 | Nacos (Spring Cloud Alibaba) | 2023.0.1.2 |
| 认证 | Spring Security + OAuth2 Authorization Server | 1.2.4 |
| 持久层 | Spring JDBC + MyBatis Spring Boot | 3.0.3 |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis (Spring Data Redis) | - |
| API文档 | Knife4j + SpringDoc OpenAPI 3 | - |
| 构建 | Maven 多模块 | 3.9+ |
| 加密 | BouncyCastle SM4, BCrypt | 1.78.1 |
| AI | LangChain4j + Lucene + Milvus | 0.36.2 |

## 模块层级

```
Gateway (9000) ── 全局认证、路由转发
  ├── auth-server ── OAuth2 认证、JWT 签发
  ├── system-server ── RBAC 权限、用户/角色/菜单管理
  ├── auth-flow ── 审批工作流引擎
  ├── auth-message ── 消息通知(站内信/邮件/短信)
  ├── ai-agent-server ── AI 智能对话、RAG知识库、数据分析
  └── log-server ── 日志收集、分析、错误解决方案
```

## 数据流

```
Client → Gateway(AuthGlobalFilter) → JWT验证
  → 转发到对应微服务
  → Resource Server Filter(JWT解码)
  → @RequirePermission 权限校验
  → Controller → Service接口 → Mapper/Repository → DB
```

## 持久层双方案

| 方案 | 适用场景 | 模块 |
|------|---------|------|
| MyBatis + XML | 复杂查询、动态SQL、分页 | system-server(新) |
| Spring JDBC | 简单CRUD、批量操作 | 其他模块 |

两者可在同一模块中共存，通过不同包路径区分。

## 可复用模块

### common-core
提供给其他项目的公共核心库，包含:
- 统一返回模型 R.java
- 加密工具 (SM4/AES/DES/RS256/HS256)
- Redis 工具类
- 动态多数据源
- Trace/日志自动配置
- Nacos 配置解密

### resource-server-starter
资源服务器自动配置:
- JWT 解码验证
- @RequirePermission 权限注解
- @PublicApi 公开接口注解
- 全局异常处理

## 配置管理

所有环境配置通过 Nacos 统一管理:
- `datasource.yml` - 数据源配置
- `redis.yml` - Redis 配置
- `auth-security.yml` - JWT/安全配置
- `common-config.yml` - 公共配置

敏感配置使用 Nacos 加密 + ConfigCryptoUtil 解密。
