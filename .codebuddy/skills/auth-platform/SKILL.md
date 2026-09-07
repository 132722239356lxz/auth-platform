---
name: auth-platform
description: >
  This skill provides comprehensive knowledge of the auth-platform project including
  its microservice architecture, technology stack, code conventions, and development
  patterns. It should be used when working on any module of this project, when adding
  new features, fixing bugs, or when new team members need to understand the codebase.
  Also use when performing code reviews, refactoring, or integrating with the project's
  common-core and system-server modules.
---

# Auth-Platform 项目技能文档

## 项目概述

Auth-Platform 是基于 Spring Cloud 微服务架构的统一授权认证中台，使用 **Java 17 + Spring Boot 3.2.5 + Spring Cloud 2023.0.1 + Spring Cloud Alibaba 2023.0.1.2** 构建，通过 Nacos 实现服务注册发现和配置管理，基于 Spring Authorization Server 1.2.4 实现 OAuth2 认证授权。

## 技术栈

| 层级 | 技术选型 |
|------|----------|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.2.5, Spring Cloud 2023.0.1 |
| 微服务 | Spring Cloud Alibaba 2023.0.1.2 (Nacos) |
| 认证 | Spring Security, Spring Authorization Server 1.2.4, OAuth2 + JWT |
| 持久层 | Spring JDBC (JdbcTemplate), Spring Data JDBC, MyBatis Spring Boot |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis (Spring Data Redis) |
| API 文档 | Knife4j + SpringDoc OpenAPI 3 (Jakarta) |
| 构建 | Maven 多模块 |
| 加密 | BouncyCastle (SM4 国密), BCrypt |
| LLM | LangChain4j 0.36.2 + OkHttp |
| 搜索引擎 | Apache Lucene 9.11.1 |
| 向量数据库 | Milvus 2.4.5 |
| 前端 | Vue 3 + TypeScript |

## 模块架构

```
auth-platform/
├── gateway/                    # Spring Cloud Gateway 网关 (端口9000)
│   ├── 全局认证过滤器 AuthGlobalFilter
│   ├── SecurityConfig (OAuth2 JWT 验证)
│   └── 白名单/IP白名单/公开路径配置
├── auth-server/                # OAuth2 认证授权服务器
│   ├── Spring Authorization Server 核心
│   ├── JWT 签发与验证
│   └── 客户端管理
├── system-server/              # 系统管理服务 (核心业务模块)
│   ├── 用户/角色/菜单/部门 CRUD
│   ├── RBAC 权限模型
│   ├── 子系统 Token 管理
│   ├── OAuth2 客户端管理
│   ├── 授权审计
│   ├── 字典管理
│   ├── 公告/反馈管理
│   └── 缓存策略 (Redis)
├── auth-flow/                  # 审批工作流引擎
│   ├── 工作流定义/实例/节点/任务
│   ├── 权限申请审批
│   └── 动态审批链
├── auth-message/               # 消息通知服务
│   ├── 站内信/邮件/短信/WebSocket
│   ├── 消息模板管理
│   └── 广播架构
├── ai-agent-server/            # AI 智能体服务
│   ├── LangChain4j Agent 编排
│   ├── RAG 知识库 (向量检索 + 本地Lucene检索)
│   ├── 业务数据分析预警
│   ├── 多模态对话(图片/文件)
│   ├── 流式 SSE 对话
│   └── 工具调用 (数据库查询/预警/审批查询)
├── log-server/                 # 日志收集与分析服务
│   ├── 日志采集、查询、分析
│   └── 错误解决方案知识库
├── common-core/                # 公共核心库 (可独立复用)
│   ├── R.java 统一返回模型
│   ├── 加密工具 (SM4/AES/DES/RSA/HS256)
│   ├── Redis 工具类
│   ├── 动态多数据源
│   ├── 请求日志/Trace 追踪
│   ├── 日志上报客户端
│   ├── 异步任务工具
│   ├── Nacos 配置解密
│   └── TenantContext 多租户上下文
├── resource-server-starter/    # 资源服务器自动配置 Starter
│   ├── JWT 解码与验证
│   ├── 全局异常处理
│   ├── @RequirePermission 权限注解
│   ├── @PublicApi 公开接口注解
│   └── Spring Security 自动配置
├── knife4j-aggregation/        # API 文档聚合
└── subsystem-sdk/              # 子系统 SDK
```

## 请求链路

```
Client → Gateway (9000, AuthGlobalFilter token 验证)
         → auth-server (OAuth2 认证)
         → system-server (RBAC 权限 + 业务数据)
         → auth-flow (审批流)
         → auth-message (消息通知)
         → ai-agent-server (AI 智能问答)
         → log-server (日志分析)
```

## 代码规范

### 命名规范
- **包名**: 全部小写 `com.liang.xz.system`
- **类名**: UpperCamelCase `UserEntity`, `UserManageService`
- **方法名**: lowerCamelCase `findByUsername`, `getUserRoles`
- **常量**: CONSTANT_CASE `DEFAULT_ROLE_CODE`
- **实体**: `XxxEntity` 映射数据库表
- **DTO**: `XxxRequest`/`XxxResponse` 入参/出参
- **Service**: `XxxService` 业务逻辑
- **Repository**: `XxxRepository` 数据访问

### 注解规范
- 实体类必须添加 `@Schema(description = "...")` 注解用于 API 文档生成
- DTO 类同样需要 @Schema 注解
- Controller 使用 `@Tag` 和 `@Operation` 描述接口

### 面向接口编程
- Service 层必须定义接口，实现类使用 `XxxServiceImpl` 命名
- Controller 注入接口而非实现类
- Repository 也应定义接口（MyBatis Mapper 接口）

### 统一返回
- 全部 Controller 使用 `R<T>` 统一返回格式
- `R.ok(data)` 成功返回
- `R.fail(code, msg)` 失败返回

### 日志规范
- 使用 Lombok `@Slf4j`
- 关键操作使用 `log.info`
- 异常使用 `log.error`
- 调试使用 `log.debug`
- 生产环境禁止输出 debug 日志

### 缩进与格式
- 4 空格缩进，禁止 Tab
- 单行不超过 120 字符
- 使用 K&R 大括号风格
- 每个 import 独立成行，不使用通配符

### 异常处理
- 不在 finally 块中使用 return
- 异常不用来做流程控制
- 异常信息包含现场信息和堆栈
- 优先使用 try-with-resources

## 持久层规范

项目使用双层持久层方案:
- **MyBatis**: 复杂查询、动态 SQL、分页查询（system-server 核心模块）
- **Spring JDBC**: 简单 CRUD、批量操作、其他模块

Entity 需配合 MyBatis 使用:
- 使用 Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`
- 添加 `@Schema` 注解用于 API 文档
- 字段命名与数据库列名对应（下划线转驼峰由 MyBatis 自动处理）

## 配置管理

- 所有配置通过 Nacos 统一管理
- 敏感配置使用 Nacos 配置加密
- 各模块 bootstrap.yml 指向 Nacos 配置中心
- 本地开发可使用本地 application.yml 覆盖

## 安全规范

- 所有 API 默认需要认证（通过 Gateway AuthGlobalFilter）
- 公开接口使用 `@PublicApi` 注解或在 Gateway 配置 publicApiPaths
- 权限控制使用 `@RequirePermission("system:user:list")` 注解
- 密码必须 BCrypt 加密存储
- Token 有效期通过 Nacos 配置

## common-core 和 system-server 可复用策略

### common-core 复用
common-core 是无业务逻辑的公共核心库，其他项目直接引入依赖:
```xml
<dependency>
    <groupId>com.liang</groupId>
    <artifactId>common-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### system-server 复用
system-server 包含完整的 RBAC 权限体系，其他项目可复用:
- **Entity 层**: 实体类定义（与数据库表映射）
- **DTO 层**: 请求/响应对象
- **Service 接口层**: 业务接口定义（面向接口编程后）
- **Repository/Mapper 层**: 数据访问接口

## 参考文件

详细参考文档位于:
- `references/architecture.md` - 详细架构文档
- `references/code-conventions.md` - 编码规范详解
- `references/api-design.md` - API 设计规范
