---
name: fix-startup
description: 当 Spring Boot / Maven 应用启动或构建报错时，自主读取报错（控制台/日志/构建输出）、定位根因、修改代码并重新验证可成功启动，最后给出"现象→根因→改动→验证"的完整过程。当用户说"启动报错了/跑不起来/build failed/编译不过/帮我修启动错误"时使用。
context: fork
agent: general-purpose
allowed-tools: Read, Grep, Glob, Bash, Write, Edit
---

# 启动报错自主诊断与修复

你的目标：把"启动/构建失败"闭环修复到可正常运行，并向用户清晰汇报过程。**自主完成 诊断 → 修复 → 验证**，保持最小改动，不破坏其它功能。

## 第一步：复现并获取报错

- 若用户已贴报错，直接分析；否则主动复现：
  - 编译错误优先：`mvn -q -DskipTests compile`（全量或 `-pl <module> -am` 限模块）。
  - 运行时错误再启动抓：`mvn -q -pl <module> -am spring-boot:run`（用 `timeout 90 ...` 限时，避免进程常驻）。
- 读取日志：本仓库有 `logs/` 目录与各模块 `target/*.log`；也可直接捕获启动进程输出。
- 把报错原文完整保留，作为现象依据。

## 第二步：定位根因

- 解析异常栈：找 `Caused by` 最底层、第一个**项目内栈帧**（`com.liang.xz...`），那通常就是根因所在文件:行号。
- 用 `Read`/`Grep`/`Glob` 打开相关源文件与 `application.yml`/`pom.xml` 确认，**不要臆测**。
- 本仓库高频启动故障（重点排查）：
  - Spring Authorization Server / OAuth2 API 误用（如方法不存在、上下文类型错配）。
  - Spring Bean 冲突、循环依赖、`@Transactional` 自调用失效。
  - `application.yml` 中 `@nacos.*@` 占位符**未被替换**（Nacos 未连接 / profile 未生效）→ 配置值为字面量 `@nacos.xxx@`。
  - 端口冲突（`server.port` 与其它模块重复）。
  - Knife4j / springdoc 配置或依赖缺失；JDK 17 语法/模块问题。
  - 缺 `spring-boot-maven-plugin` 导致无法启动（打包产物不可执行）。
- 区分 **代码错误** 与 **环境依赖缺失**（Nacos/Redis/MySQL 未起）：后者不要盲目改代码绕开必要依赖，应说明并建议用本地回退配置或先起依赖。

## 第三步：修复代码

- 用 `Edit`/`Write` 做**最小改动**，只修导致启动失败的根因。
- 保持本仓库约定：统一返回用 `ApiResponse<T>`（auth-server/system-server）或模块内 `R<T>`；Nacos 用 `@nacos.*@` 占位符；公共能力复用 `common-core`。
- 不引入新框架、不硬编码凭据；不扩大改动范围。

## 第四步：验证

1. `mvn -q -DskipTests compile` 编译通过。
2. 再次 `timeout 90 mvn -q -pl <module> -am spring-boot:run`，确认日志出现 `Started <Xxx>Application` 且无新异常。
3. **最多迭代 3 轮** 修复-验证；仍失败则停止，输出当前最佳诊断与卡点，请用户决策。
4. 注意：本仓库 pre-commit 钩子也会拦编译错误，改动提交前需可编译。

## 第五步：输出过程报告

按以下结构汇报（用户要的是"调整过程"）：

```
## 启动修复过程
### 现象
<报错摘要 + 关键异常/日志行>

### 根因
<文件:行号> — <为什么导致启动失败>

### 改动
<before → after，或 diff 片段；说明改动如何消除根因>

### 验证
<重新编译/启动结果，"Started ..." 确认；如仍依赖外部服务，说明环境前提>

### 遗留 / 建议
<可选：相关隐患或后续建议>
```

## 约束

- 闭环自治：诊断、修复、验证一气呵成；但不做破坏性大改，拿不准时停下说明方案而非擅自改动。
- **不主动提交代码**（除非用户明确要求）。
- 凭据不硬编码；改动须与本仓库既有风格一致。
