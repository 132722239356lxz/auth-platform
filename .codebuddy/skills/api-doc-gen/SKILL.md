---
name: api-doc-gen
description: 针对指定接口（Controller 端点）编写并运行单元测试以验证行为、提取真实入参/出参结构，然后生成带参数说明的接口对接文档（Markdown + 补充 OpenAPI/@Schema 注解），供前端对接。当用户要求"给某接口写单测并出接口文档/生成对接文档/出 API 文档/api doc for ..."时使用。
context: fork
agent: general-purpose
allowed-tools: Read, Grep, Glob, Bash, Write, Edit
---

# 接口单测 + 对接文档生成

你负责：**先给指定接口写/跑单元测试，再基于真实契约生成带参数说明的接口文档**，让前端能无歧义对接。本仓库是 Spring Boot + springdoc/Knife4j 工程。

## 第一步：定位并理解接口

从 `$ARGUMENTS` 取目标（Controller 类、方法名或路径，如 `/api/ai/analysis/query`）。用 Grep/Glob 找到对应 `@RestController` 与 `@RequestMapping`/`@PostMapping` 等方法，读清：

- HTTP 方法、完整路径、是否需要鉴权（Token / 经 gateway）。
- **入参**：`@PathVariable` / `@RequestParam` / `@RequestBody` 的 DTO 类（读其字段与 `@Schema`/`@NotNull` 等校验注解）。
- **出参类型**：本仓库统一响应体是**各模块自有的 `R<T>`**（`code`/`msg`/`data`，`200=成功`、`500=失败`，见如 `auth-flow/.../dto/R.java`）；也有部分 Controller 直接返回 `Map`。以实际返回类型为准。

```
!`grep -rln "@RestController" <module>/src/main/java`
!`grep -rn "@RequestMapping\|@PostMapping\|@GetMapping\|@PutMapping\|@DeleteMapping" <module>/src/main/java/com/liang/xz/<module>/controller/`
```

## 第二步：单元测试（先验证，再文档）

1. 用 **MockMvc**（`@WebMvcTest` 或 `@SpringBootTest` + `MockMvc`）针对该端点写/补测试，**Mock Service 层**以隔离真实 DB / Nacos / Redis（不连外部）。
2. 覆盖：正常入参、缺参/非法值（触发校验）、关键业务分支。用 `andReturn()` 抓取真实请求/响应 JSON 作为文档样例。
3. 只跑相关用例，省时：
   ```
   mvn -q -pl <module> -Dtest=<TestClass>#<method> test
   ```
4. **测试必须通过才继续**；失败先修复或向用户报告，不要基于未验证的假设写文档。

## 第三步：生成对接文档

**(A) Markdown 文档** —— 写到 `docs/api/<module>/<endpoint>.md`（无该目录则新建；仓库已有 `docs/` 放 API 文档）：

- 基本信息：路径、方法、描述、鉴权方式（是否需 Bearer Token / 经 gateway）。
- **请求参数表**：名称 / 位置(path|query|body) / 类型 / 是否必填 / 说明 / 示例值。
- **响应字段表**：基于 `R<业务data>` 或实际 DTO，逐字段说明（code/msg/data 结构 + data 内业务字段）。
- 错误码（如 200/500 及业务异常）、**curl 示例** 与 **前端 fetch 示例**。
- 参数说明必须反映真实校验（@NotNull、长度、枚举值）。

**(B) 同步补齐 OpenAPI 注解**，让 Knife4j 自动展示参数说明（前端可直接在 swagger-ui / knife4j UI 看）：

- Controller 方法加 `@Operation(summary = "...", description = "...")`；必要时 `@Parameter`。
- DTO 字段补 `@Schema(description = "...", example = "...")`，枚举/必填标注清楚。
- 确认该路径在模块 `application.yml` 的 `springdoc.group-configs[].paths-to-match` 中（否则 Knife4j 分组不显示此接口）。

## 第四步：自检

- 文档字段、示例与代码、DTO、测试结果三者一致。
- OpenAPI 注解已补齐；相关单元测试绿灯。
- 若 Controller 返回裸 `Map`，在文档中按真实字段说明，并建议改为模块内 `R<T>` 统一响应。

## 约束

- **不硬编码任何凭据**；测试用 Mock 隔离，不依赖真实 Nacos/DB/Redis（与 pre-commit 钩子、其它 Skill 的约定一致）。
- 文档与注解以**实际代码与测试结果**为准，不要凭空编造字段。
- 输出先给文档摘要，再把文件写入 `docs/api/`。
