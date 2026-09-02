# 项目级 Skills 说明

本目录（`.codebuddy/skills/`）存放 **auth-platform** 的**项目级 CodeBuddy Code Skills** —— 封装本仓库专属的领域知识与工作流，随仓库提交后团队所有成员共享（项目级 Skill 优先级高于用户级）。

## 如何使用

- **自动触发**：在对话中描述相关任务时，CodeBuddy 会根据 Skill 的 `description` 自动选择并调用。
- **手动触发**：输入 `/<skill-name>`，例如 `/scaffold-module`、`/project-architecture`。
- **查看已加载**：`/skills` 命令，项目级 Skill 显示在 **Project skills** 分组下。

> Skills 通过 `SKILL.md`（Markdown + YAML frontmatter）定义，每个 Skill 一个独立子目录。详见官方文档的 [Skills 章节](https://cnb.cool/codebuddy/codebuddy-code/-/blob/main/docs/cn/cli/skills.md)。

## 现有 Skills

| Skill | 类型 | 用途 | 触发示例 |
| --- | --- | --- | --- |
| [`scaffold-module`](./scaffold-module/SKILL.md) | 可写（`context: fork` + `agent: general-purpose`） | 按本仓库约定脚手架一个新的 Spring Cloud 微服务 Maven 子模块（集成 Nacos、`common-core`、Knife4j/springdoc）。 | "帮我新建一个 order-service 微服务模块" / "scaffold a new module" |
| [`project-architecture`](./project-architecture/SKILL.md) | 只读（`context: fork` + `agent: Explore`） | 讲解整体架构、各模块职责、模块间依赖与网关路由，帮助定位代码。 | "各模块是干什么的？" / "一个登录请求经过哪些服务？" / "了解架构" |
| [`api-doc-gen`](./api-doc-gen/SKILL.md) | 可写（`context: fork` + `agent: general-purpose`） | 给指定接口写/跑单元测试验证行为，再生成带参数说明的对接文档（Markdown + 补充 OpenAPI 注解），供前端对接。 | "给 /api/xxx 写单测并出接口文档" / "生成对接文档" / "api doc for ..." |
| [`java-validation`](./java-validation/SKILL.md) | 可写（`context: fork` + `agent: general-purpose`） | 按规范生成 Java DTO（jakarta.validation 校验 + Lombok + OpenAPI @Schema）与 Controller（统一返回 `ApiResponse<T>`、分组校验、全局异常处理）。 | "生成 DTO" / "加参数校验" / "生成接口层代码" / "统一返回" |
| [`fix-startup`](./fix-startup/SKILL.md) | 可写（`context: fork` + `agent: general-purpose`） | 应用启动/构建报错时，自主读报错、定位根因、改代码并重新验证可启动，给出"现象→根因→改动→验证"过程。 | "启动报错了" / "跑不起来" / "build failed" / "编译不过" / "修启动错误" |
| [`optimize-context`](./optimize-context/SKILL.md) | 只读（`context: fork` + `agent: Explore`） | 汇总压缩当前任务上下文（目标/改动/决策/待办/约束），生成紧凑摘要以降低后续 token 消耗。 | "优化上下文" / "压缩上下文" / "总结上下文" / "token 太多" / "给份进度摘要" |

### 约定要点（两 Skill 共同遵守）

- **Nacos / 数据库 / Redis 凭据一律用 `@nacos.*@` 占位符**，绝不硬编码进仓库（与 pre-commit 钩子的密钥防护一致）。
- **共享底座是 `common-core`**：新公共能力优先放 `common-core`，不要在各业务模块重复造轮子。
- `gateway` 按 `Path=/<service>/**` → `lb://<service>` 路由；跨服务走 HTTP（经网关）或 MQ（`auth-message`），不是 Maven 直接依赖。

## 相关：用户级 Skill

代码审查 Skill `code-review` 为**用户级**（位于 `~/.codebuddy/skills/code-review/`），跨所有项目可用，不在本目录内。可用 `/code-review` 审查工作区改动。

## 如何新增 / 修改 Skill

1. 在本目录新建子目录 `<skill-name>/`，放入 `SKILL.md`。
2. frontmatter 至少含 `name`、`description`；按需设 `allowed-tools`、`context: fork` + `agent`、`user-invocable`。
3. `description` 写清触发场景，便于 AI 自动识别。
4. 提交到仓库即可对团队生效。
