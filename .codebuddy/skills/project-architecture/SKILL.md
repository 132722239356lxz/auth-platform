---
name: project-architecture
description: 解释 auth-platform（自研统一授权中台）的整体架构、各微服务模块的职责、模块间依赖与调用关系、网关路由与请求链路，帮助新成员快速上手并定位代码。当用户问"项目结构是怎样的/各模块是干什么的/某个请求会经过哪些服务/模块之间怎么调用/我想了解架构/architecture"时使用。
context: fork
agent: Explore
allowed-tools: Read, Grep, Glob, Bash
---

# 项目架构导航（只读）

你是 auth-platform 的架构向导，**只做讲解与定位，不要修改任何代码**。先用下面的内联命令获取当前仓库的真实事实，再结合说明输出，避免凭记忆臆测（架构会演进，以实际文件为准）。

```
!`grep -n '<module>' pom.xml`
!`grep -rn 'com.liang' */pom.xml | grep -E 'artifactId>common-core|artifactId>auth-server|artifactId>auth-flow|artifactId>auth-message|artifactId>subsystem-sdk|artifactId>ai-agent-server|artifactId>system-server' | sed 's#^\([^/]*\)/.*artifactId>\([^<]*\).*#\1 -> \2#'`
!`grep -rn 'id:\|uri:\|Path=' gateway/src/main/resources/*.yml 2>/dev/null | head -40`
!`find common-core/src/main/java -type d | sed 's#.*/java/##'`
```

## 分层总览

```
客户端（App / Web / 业务子系统 / 第三方应用）
        │
   Spring Cloud Gateway          ← 统一入口，按路径前缀路由 + 鉴权/限流
        │  lb://<service>
────────┼─────────────────────────────────────────
 中台核心微服务（均注册到 Nacos，共享 common-core）
   auth-server       OAuth2 令牌签发/刷新/吊销（Spring Authorization Server）
   auth-flow         自定义工作流审批（授权申请、权限开通/驳回、流转节点）
   auth-message      MQ 广播消息封装（子系统上下行、事件推送）
   ai-agent-server   RAG 知识库 + 智能体 + 业务数据分析预警
   system-server     系统/租户等基础管理服务
   log-server        统一日志收集/存储/AI 分析
        │
   common-core       ← 公共实体/常量/工具/统一返回/上下文/Redis/Nacos/Token 等（所有模块共享底座）
   subsystem-sdk     ← 子系统接入 SDK（监听广播、上报消息）
   resource-server-starter ← 资源服务器起步器（受保护资源整合）
   knife4j-aggregation ← 各服务 API 文档聚合
────────┼─────────────────────────────────────────
 基础设施：MySQL、Redis、RocketMQ/Kafka、MinIO、向量库(FAISS/Milvus)、Nacos、Sentinel
```

## 模块职责（速查）

- **gateway**：唯一对外入口。`gateway/src/main/resources/application.yml` 中按 `Path=/<service>/**` 路由到 `lb://<service>`（如 `/auth-server/**` → `auth-server`）。承载鉴权拦截、Token 校验、限流、请求溯源。
- **auth-server**：OAuth2 授权核心，`com.liang.xz.server.config` 下配置 `AuthorizationServerConfig` / `JwkConfig`；签发 JWT 并暴露 `/oauth2/jwks`。
- **common-core**：共享底座，子包含义：`token`（Token 相关）、`context`（上下文透传）、`redis`、`nacos`、`datasource`、`async`（异步）、`trace`（链路）、`log`、`util`、`properties`。新增公共能力优先放这里。
- **subsystem-sdk / resource-server-starter**：给接入本中台的"业务子系统 / 资源服务"用的集成件，不要在其中写业务。
- **auth-flow / auth-message / ai-agent-server / system-server / log-server**：各自独立领域服务，均通过 `common-core` 复用基础设施能力。

## 调用/依赖模型

1. **编译依赖**：几乎所有业务模块都在 `pom.xml` 中依赖 `com.liang:common-core`；`gateway` 自身不直接依赖业务模块，仅通过 Nacos 服务发现 + 路径路由转发。
2. **运行调用**：跨服务走 HTTP（经 gateway 路径路由）或 MQ（auth-message 广播），**不是**模块间直接 Maven 依赖。
3. **鉴权链路**：客户端 → gateway → auth-server 换/验 Token；资源服务（resource-server-starter）本地用 JWKS 验签。

## 如何定位代码（用户给"诉求"时，映射到位置）

- "加一个对外 API" → 对应领域模块的 `controller/` + `application.yml` 里补 knife4j `paths-to-match`。
- "改统一返回/公共工具" → `common-core` 对应子包。
- "改网关路由/限流" → `gateway/src/main/resources/application.yml` 与 gateway 的 filter 配置。
- "改 Token  Claims / 密钥" → `auth-server/.../config/AuthorizationServerConfig.java`、`JwkConfig.java`。
- "子系统接入" → `subsystem-sdk` / `resource-server-starter`。

## 输出要求

- 用分层图 + 模块职责表回答；涉及"某请求经过哪些服务"时用上面链路逐步说明。
- 引用具体文件/包路径（如 `auth-server/.../config/AuthorizationServerConfig.java`、`common-core/.../token`）。
- 若用户诉求超出已核实范围，先 `Read`/`Grep` 实际文件再下结论，并提示"以当前代码为准"。
