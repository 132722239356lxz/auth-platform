# CODEBUDDY.md

This file provides guidance to CodeBuddy Code when working with code in this repository.

## Project Overview

`auth-platform` 是一个自研的**统一授权中台**：基于 Spring Cloud Alibaba 微服务 + Spring Authorization Server 的 OAuth2 认证授权平台，并叠加工作流审批、消息广播、AI 智能体（RAG）和统一日志分析。前端包含 Web 管理端（Vue3）和移动端（UniApp）。

- **语言/构建**：后端 Java 17 + Maven 多模块；前端 Node.js + Vite。
- **核心依赖版本**：Spring Boot 3.2.5、Spring Cloud 2023.0.1、Spring Cloud Alibaba 2023.0.1.2、Spring Authorization Server 1.2.4、Nacos 2.3.2、Knife4j 4.5.0。
- **运行前提**：Nacos（注册中心 + 配置中心，默认 `127.0.0.1:8848`）以及 MySQL / Redis / MQ 等基础设施。**本地配置通过 `bootstrap.yml` / `application.yml` 从 Nacos 拉取**；离线调试可用模块本地 `application.yml` 覆盖。

各模块的详细编码规范见 `.codebuddy/rules/java-coding-style.mdc`（已自动生效），本文件只补充项目特有约定。

## Common Commands

### Backend (Maven)

在仓库根目录执行 Maven 构建（默认 dev profile 已激活）。常用目标：

- 全量编译：`mvn -q compile`
- 仅编译单个模块及其依赖（reactor 模式），例如网关：`mvn -pl gateway -am compile`
- 运行测试（全量）：`mvn test`
- 运行单个模块的测试：`mvn -pl system-server test`
- 运行单个测试类：`mvn -pl system-server test -Dtest=UserRepositoryTest`
- 运行单个测试方法：`mvn -pl system-server test -Dtest=UserRepositoryTest#shouldCreateUser`

启动某个后端服务：直接运行对应模块的 Spring Boot 启动类（如各模块的 `*Application.java`）；构建产物会生成在各模块 `target/` 目录下，名为 `*-1.0-SNAPSHOT.jar`。

注意：后端各模块目前**基本没有单元测试用例**（仅 `ai-agent-server` 带有 `src/test/resources/application-test.yml` 测试配置）。新增能力时按上方 Maven Surefire 命令即可，但需先确认该模块是否引入了测试框架依赖。

> 资源过滤使用 `@...@` 占位符（在根 `pom.xml` 的 `maven-resources-plugin` 中显式限定），**不要**改用 `${...}`，以免与 Spring 自身的 `${}` 配置占位符冲突。`application.yml` 中的 `@nacos.server-addr@` 等会在构建时被 Maven 替换。

### Frontend (Node)

```bash
# Web 管理端（frontend-web）
cd frontend-web
npm install
npm run dev          # http://localhost:5173
npm run preview      # 预览构建产物

# 移动端（frontend-app）
cd frontend-app
npm install
npm run dev:h5           # H5 模式，http://localhost:5174
npm run dev:mp-weixin    # 微信小程序模式
npm run dev:app          # App 模式
```

前端生产构建：进入对应前端目录执行其 Vite `build` 脚本（先类型检查再打包）。

Web 端的开发服务器通过 Vite proxy 把 `/auth-server`、`/system-server`、`/auth-flow`、`/auth-message`、`/ai-agent-server`、`/log-server` 等前缀代理到网关（默认 `http://127.0.0.1:8080`，可用 `.env.development` 的 `VITE_GATEWAY_URL` 覆盖）。Web 端更细的目录与请求链路说明见 `frontend-web/CODEBUDDY.md`。

## Architecture

### 分层与请求链路

```
客户端（Web / App / 业务子系统 / 第三方应用）
        │
   Spring Cloud Gateway          ← 唯一对外入口 :8080
    AuthGlobalFilter 校验 Token、限流、路由转发（lb://<service>）
        │
 中台微服务（均注册到 Nacos，共享 common-core）
   auth-server       OAuth2 令牌签发/刷新/吊销，JWT + JWKS
   system-server     RBAC：用户/角色/菜单/部门、客户端/审计/字典
   auth-flow         自定义工作流审批（申请、流转节点、权限开通/驳回）
   auth-message      消息通知（站内信/邮件/短信/WebSocket + MQ 广播）
   ai-agent-server   LangChain4j 智能体、RAG 知识库、业务数据预警
   log-server        日志采集/查询/AI 日志分析
        │
   common-core       公共底座（实体/常量/工具/统一返回/上下文/Redis/Nacos/动态数据源/Trace）
   resource-server-starter  资源服务器自动配置（JWT 验签、@RequirePermission、@PublicApi）
   subsystem-sdk     外部子系统接入 SDK（监听广播、上报消息）
   knife4j-aggregation  各服务 API 文档聚合
        │
 基础设施：MySQL、Redis、MQ（auth-message 用 RabbitMQ）、MinIO、向量库(Milvus)、Nacos、Sentinel
```

**单条请求路径**：Client → Gateway `:8080`（AuthGlobalFilter 用 JWKS 本地验签）→ 按路径前缀路由到 `lb://<service>` → 业务服务经 `resource-server-starter` 做方法级权限校验（或 `@PublicApi` 放行）。跨服务通信走 HTTP（经网关）或 MQ（auth-message 广播），**不是** Maven 模块间直接依赖。

### 模块与服务端口

| 模块 | 端口 | 职责 | 包名前缀 |
|---|---|---|---|
| gateway | 8080 | 路由/鉴权/限流入口 | `com.liang.xz.gateway` |
| auth-server | 9000 | OAuth2 认证授权核心 | `com.liang.xz.server` |
| system-server | 9001 | RBAC 与系统管理 | `com.liang.xz.system` |
| auth-flow | 9011 | 工作流审批 | `com.liang.xz.flow` |
| auth-message | 9002 | 消息广播 | `com.liang.xz.message` |
| ai-agent-server | 9003 | AI 智能体 / RAG | `com.liang.xz.aiagent` |
| log-server | 9009 | 日志收集与分析 | `com.liang.xz.log` |
| knife4j-aggregation | 10909 | API 文档聚合 | — |

> 网关路由前缀示例：`/auth-server/**` → auth-server，`/system-server/**` → system-server，`/auth-flow/**` → auth-flow，`/auth-message/**` → auth-message，`/ai-agent-server/**` → ai-agent-server，`/log-server/**` → log-server。具体路由在 `gateway/src/main/resources/application.yml`（被 Nacos `gateway.yml` 覆盖）。

### 关键设计约定（改动代码前必读）

- **统一返回**：`common-core` 定义 `R<T>`（`code` / `msg` / `data`）为统一响应；`system-server` 另定义 `ApiResponse<T>`（`code` / `message` / `data`）。前端响应拦截器两者都兼容，新增接口请沿用所在模块既有格式。
- **权限控制**：方法级用 `@RequirePermission("xxx:yyy:zzz")` 注解（由 `resource-server-starter` 提供），公开接口用 `@PublicApi`，或在网关白名单配置。前端仅做 UX 级隐藏（`v-permission`），后端注解才是最终拦截。
- **持久层双层方案**：`system-server` 用 **MyBatis**（复杂 SQL / 分页），其余模块多用 **Spring JDBC (JdbcTemplate)** 做简单 CRUD / 批量。新增数据访问时先确认所在模块的既有技术栈。
- **面向接口编程**：Service 必须定义接口、实现类命名 `XxxServiceImpl`；Controller 注入接口；MyBatis 用 Mapper 接口。实体类用 Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor` 并加 `@Schema(description=...)` 供 Knife4j 生成文档。
- **公共能力归属**：加密（SM4/AES/RSA/HS256）、Redis 工具、动态多数据源、TenantContext 多租户、Trace 链路、异步任务、Nacos 配置解密等都在 `common-core`；新增公共能力优先放这里，不要在业务模块重复造轮子。
- **SDK / Starter 不写业务**：`subsystem-sdk` 与 `resource-server-starter` 是给接入方用的集成件，只放通用集成逻辑，不放具体业务。
- **配置管理**：所有环境相关配置走 Nacos（`bootstrap.yml` 的 `spring.config.import` 拉取 `nacos:<serviceName>.yml` 等）；敏感配置在 Nacos 加密。本地调试可加本地 `application.yml` 覆盖。

### 如何定位代码

- 新增对外 API → 对应领域模块的 `controller/` + 在该模块 `application.yml` 的 Knife4j `paths-to-match` 中补充路径。
- 改统一返回 / 公共工具 → `common-core` 对应子包（`token` / `context` / `redis` / `nacos` / `datasource` / `async` / `trace` / `log` / `util` / `properties`）。
- 改网关路由 / 限流 / 白名单 → `gateway/src/main/resources/application.yml` 及 gateway 的 filter / `RateLimiterConfig`。
- 改 Token Claims / 密钥 → `auth-server/.../config/AuthorizationServerConfig.java`、`JwkConfig.java`。
- 子系统接入 → `subsystem-sdk` / `resource-server-starter`。
