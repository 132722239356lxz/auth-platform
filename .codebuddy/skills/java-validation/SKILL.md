---
name: java-validation
description: 按本仓库规范生成 Java DTO（jakarta.validation / JSR303 校验 + Lombok @Data + OpenAPI @Schema 含示例值）与 Controller（统一返回 ApiResponse<T>、分组校验 CreateGroup/UpdateGroup、全局异常处理），用于新增或改造接口层代码。当用户要求"生成 DTO/参数校验/统一返回/接口层代码/校验注解"时使用。
context: fork
agent: general-purpose
allowed-tools: Read, Grep, Glob, Bash, Write, Edit
---

# Java 参数校验 & 统一返回体代码生成

> **适用范围**：统一返回体 `ApiResponse<T>` 位于 `auth-server`（`com.liang.xz.server.dto`）与 `system-server`（`com.liang.xz.system.dto`）。同仓 `auth-flow` / `auth-message` 各自使用 `R<T>`（code/msg/data 结构），`ai-agent-server` 部分接口返回裸 `Map`。本 Skill 针对采用 `ApiResponse<T>` 的模块；若目标模块用 `R<T>`，将返回体替换为该模块的 `R<T>` 并同样套用 `code/msg/data` 结构即可，其余校验与注解规则不变。

## 基础约束

1. 全局统一返回对象固定使用 `com.liang.xz.server.dto.ApiResponse<T>`，**禁止自定义返回类**。
2. 遵循 **SpringBoot3 + `jakarta.validation` JSR303** 校验规范，**不使用 `javax` 包**。
3. 所有 DTO 必须携带 **Lombok `@Data`**、**OpenAPI `@Schema`** 文档注解，并包含示例值。
4. 参数校验**全部携带中文 `message` 提示**，禁止默认英文报错。
5. 自动生成分组校验接口 `CreateGroup` / `UpdateGroup`，区分新增、更新字段校验规则。
6. Controller 接口返回值统一为 `ApiResponse<泛型>`；校验异常由全局 `GlobalExceptionHandler` 处理，**无需手动 try-catch**。

## DTO 生成规则

1. 字符串必填：`@NotBlank`；数字 / 对象必填：`@NotNull`。
2. 字符串长度限制搭配 `@Size`，数字范围搭配 `@Min` `@Max`。
3. 邮箱使用 `@Email`，手机号使用正则 `@Pattern(regexp = "^1[3-9]\\d{9}$")`。
4. 所有校验注解**绑定对应分组**：新增仅 `CreateGroup`，更新同时支持 `Create` + `Update`。
5. 每个字段 `@Schema` 必须填写 `description`、`example`，注释清晰易懂。

## Controller 生成规则

1. `@RequestBody` POST 接口：入参添加 `@Valid @Validated(分组.class)`。
2. GET 查询参数：使用 `@ModelAttribute` + `@Validated`。
3. 每个接口增加 `@Operation`、`@ApiResponses` 文档注解，标注 200 成功、400 参数错误返回结构。
4. 成功返回使用 `ApiResponse.success()` / `ApiResponse.success(data)`；失败统一交由全局异常处理器返回 `ApiResponse.paramFail`。

## 配套代码自动生成项

1. 分组校验接口 `CreateGroup`、`UpdateGroup`。
2. 全局异常处理器 `GlobalExceptionHandler`（若模块尚未提供则生成，已有则直接复用）。
3. DTO 请求类、Controller 接口层代码。
4. 代码注释遵循 **JavaDoc** 规范，类、方法、字段都有清晰说明。
