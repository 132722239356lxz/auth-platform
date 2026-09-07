# 项目记忆

## 项目概要
- **项目名**：auth-platform（统一认证平台）
- **技术栈**：Spring Boot 3.x + Spring Cloud Alibaba + Nacos + MySQL + Redis + RabbitMQ + OAuth2
- **JDK**：17
- **构建工具**：Maven
- **包名规范**：`com.liang.xz.{module}`

## 项目模块结构
| 模块 | 端口 | 职责 |
|------|------|------|
| auth-server | 9000 | OAuth2 认证授权服务 |
| auth-flow | 9001 | 审批工作流引擎 |
| auth-message | 9002 | 消息推送服务（短信/邮件/站内信/WebSocket） |
| system-server | 9001 | 系统管理（用户/角色/菜单/权限） |
| ai-agent-server | 9003 | AI 智能体（RAG/搜索/分析预警） |
| log-server | 9009 | 日志收集与分析 |
| gateway | 8080 | API 网关 |
| common-core | - | 公共组件（数据源/Redis/OAuth2） |

## 核心架构约定
- **多数据源**：自定义 `DynamicDataSourceAutoConfiguration` + `@ConditionalOnProperty(prefix = "spring.datasource.primary", name = "url")`，通过 Nacos `datasource.yml` 下发
- **数据源本地回退**：各模块 `application.yml` 均配置标准 `spring.datasource.url` 作为无 Nacos 时的回退
- **配置中心**：Nacos，配置通过 `spring.config.import` 以 `optional:nacos:` 前缀引入
- **邮件配置**：`MailConfig` 始终创建 `JavaMailSender` Bean，默认 `localhost:1025`
- **数据库**：`auth_platform`（主库）、`auth_log`（日志库）

## 代码规范
- 详见 `.codebuddy/memory/coding-standards.md`

## 接口文档规范
- **所有新增 REST API 接口必须同步编写接口文档**，存放于项目 `docs/` 目录
- **全量接口文档**: `docs/api-reference.md` — 所有模块完整 API 参考（19 Controller、100+ 接口）
- 文档格式：Markdown，包含接口路径、请求方法、Content-Type、请求参数表格、响应字段表格、curl 示例、前端 TS 类型定义
- 目的：作为前后端联调唯一标准，所有 DTO/VO 字段、TS 类型、前端页面渲染字段必须与文档严格一致
- **接口变更流程**: 先更新文档 → 同步修改后端 DTO/VO/Controller → 同步修改前端 API/类型/页面

## 已安装开发技能 (Skills)
全局技能安装于 `~/.agents/skills/`，已自动链接到 CodeBuddy。

| 类别 | 技能 | 能力 |
|------|------|------|
| 排错调试 | `devops` | 容器运维、CI/CD、部署排障 |
| 业务增强 | `code-refactoring` | 代码重构、遗留代码优化 |
| 业务增强 | `legacy-modernizer` | 代码现代化改造、迁移方案 |
| 交付辅助 | `deployment-documentation` | 部署文档生成、上线手册 |
| 交付辅助 | `risk-assessment` | 安全风险自查、合规评估 |

## MCP 工具调用规范

### 客户端配置
- **auto_connect**: true，MCP 工具自动连接
- **timeout**: 15000ms
- **max_parallel_mcp**: 最大 3 个并行 MCP 调用
- **workspace_root_auto_detect**: 自动检测工作目录
- **allow_user_override_workspace**: 禁止用户覆盖工作目录

### 安全白名单
- Shell 屏蔽：`rm -rf /`、`chmod 777 /`、`sudo -i`、`curl | bash`
- 文件访问：仅允许工作目录内读写，禁止跨盘读取系统敏感文件
- 数据库：禁止 drop 全库、无条件 delete 全表执行

### 工具调用优先级（从高到低）
1. `fs-mcp` — 文件系统操作（读/写/搜索）
2. `lint-mcp` — 代码规范校验
3. `git-mcp` — 版本控制 diff/log/status
4. `build-mcp` — 编译打包
5. `shell-mcp` — 命令行执行
6. `database-mcp` — 数据库操作校验
7. `docker-mcp` — 容器管理
8. `code-search-mcp` — 代码语义搜索
9. `test-mcp` — 测试执行与生成
10. `doc-mcp` — 文档生成

### 7 步调用链
| 步骤 | 规则 | 说明 |
|------|------|------|
| Step 1 | 读取文件优先使用 fs-mcp，不凭空猜测代码 | 任何代码修改前必须先读取原始文件 |
| Step 2 | 生成代码前调用 lint-mcp 校验规范 | 确保生成代码符合项目编码规范 |
| Step 3 | 修改代码前调用 git-mcp diff 查看原有逻辑 | 理解变更上下文，避免误改 |
| Step 4 | 涉及编译/打包必须调用 build-mcp | 验证代码可编译通过 |
| Step 5 | SQL 相关逻辑必须调用 database-mcp 校验执行计划 | 确保 SQL 性能可接受、索引正确 |
| Step 6 | 代码修改完成自动执行 lint 自动修复 | 自动修复格式/规范问题 |
| Step 7 | 输出前调用 doc-mcp 生成配套文档与测试代码 | 保证交付物完整性 |

### MCP Server 安装清单（10个，2026-07-01）
| 规范名称 | npm 包 | 版本 | CLI 命令 | 能力 |
|----------|--------|------|----------|------|
| fs-mcp | `@modelcontextprotocol/server-filesystem` | 2026.1.14 | `mcp-server-filesystem` | 文件系统读写 |
| lint-mcp | `@paretools/lint` | 0.20.0 | `pare-lint` | ESLint/Prettier/Biome 校验 |
| git-mcp | `@cyanheads/git-mcp-server` | 2.15.1 | `git-mcp-server` | Git 版本控制 |
| build-mcp | `@paretools/jvm` | 0.20.0 | `pare-jvm` | Maven/Gradle 构建 |
| shell-mcp | `@shell-mcp/mcp-lite` | 0.3.1 | `shell-mcp-lite` | 持久化 Shell 会话 |
| database-mcp | `@benborla29/mcp-server-mysql` | 2.0.9 | `mcp-server-mysql` | MySQL 数据库操作 |
| docker-mcp | `@paretools/docker` | 0.20.0 | `pare-docker` | 容器/镜像/Compose |
| code-search-mcp | `@paretools/search` | 0.20.0 | `pare-search` | ripgrep/fd 代码搜索 |
| test-mcp | `junit-mcp-server` | 3.0.4 | `junit-mcp-server` | JUnit 测试生成 |
| doc-mcp | `mcp-api-doc-generator` | 1.1.3 | `api-doc-generator` | API 文档生成 |

> 配置文件：`.codebuddy/mcp-config.json`，在 CodeBuddy Settings > MCP 中导入即可。
