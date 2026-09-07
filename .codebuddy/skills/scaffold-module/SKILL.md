---
name: scaffold-module
description: 在 auth-platform（Spring Cloud 授权中台）中按既有约定脚手架一个新的 Maven 微服务子模块，集成 Nacos 配置/发现、common-core 公共依赖、统一返回与 Knife4j/springdoc API 文档。当用户要求"新建一个微服务/加一个模块/脚手架一个新服务/scaffold a new module"时使用。
context: fork
agent: general-purpose
allowed-tools: Read, Grep, Glob, Bash, Write, Edit
---

# 新建微服务模块脚手架

你负责在 **auth-platform** 仓库里新增一个符合现有约定的 Spring Cloud 微服务模块。本仓库是一套 Maven 多模块工程（parent `com.liang:auth-platform`，Java 17，Spring Boot 3.2.5 + Spring Cloud Alibaba）。参考标杆模块：`log-server`、`auth-message`、`system-server`。

## 第一步：确定模块名与端口

从 `$ARGUMENTS` 取模块名（如 `order-service`）。先排查现有模块，避免 artifactId / 端口冲突：

```
!`grep -rhoE '^\s*<artifactId>[^<]+</artifactId>' pom.xml */pom.xml | head -40`
!`grep -rhoE 'server:\s*$\s*port:\s*[0-9]+' */src/main/resources/application.yml 2>/dev/null`
!`grep -n '<module>' pom.xml`
```

- artifactId 用小写中划线，全局唯一。
- 端口在 `application.yml` 的 `server.port` 指定，必须从现有端口中挑一个**未被占用**的值。

## 第二步：遵守的约定（务必对齐现有模块）

1. **模块 `pom.xml`**
   - `<parent>` 指向 `com.liang:auth-platform:1.0-SNAPSHOT`。
   - 依赖至少包含：`spring-boot-starter-web`、`spring-cloud-starter-alibaba-nacos-discovery`、`spring-cloud-starter-alibaba-nacos-config`、`com.liang:common-core`（version `${project.version}`，可选）、`springdoc-openapi-starter-webmvc-ui`、`knife4j-openapi3-jakarta-spring-boot-starter`、可选 `lombok`（`optional=true`）。
   - `<build><plugins>` 必须显式声明 `spring-boot-maven-plugin`（否则打包产物不可 `java -jar` 启动）与 `maven-compiler-plugin`（source/target 17）。
2. **资源配置 `src/main/resources/application.yml`**
   - `spring.application.name` 等于模块名。
   - 通过 `spring.config.import` 引入 Nacos 配置：`optional:nacos:${spring.application.name}.yml`、`optional:nacos:redis.yml`、`optional:nacos:datasource.yml`。
   - Nacos 地址/命名空间/分组/账号**必须用占位符** `@nacos.server-addr@`、`@nacos.namespace@`、`@nacos.group@`、`@nacos.username@`、`@nacos.password@`（由 root pom 的 profile 在构建期替换，绝对不要硬编码真实凭据）。
   - 保留 `knife4j.enable: true` 与 `springdoc` 分组配置（按模块实际 API 路径设置 `paths-to-match`）。
3. **启动类**：`src/main/java/com/liang/xz/<module>/<Module>Application.java`，注解 `@SpringBootApplication`。
4. **注册到父工程**：在 root `pom.xml` 的 `<modules>` 中新增一行 `<module><artifactId></module>`。
5. 业务代码优先复用 `common-core` 提供的统一返回、常量与工具，不要重复造轮子。

## 第三步：落地文件

按上述约定创建：
- `<module>/pom.xml`
- `<module>/src/main/java/com/liang/xz/<module>/<Module>Application.java`
- `<module>/src/main/resources/application.yml`
- （可选）一个示例 Controller，演示如何用 `common-core` 的统一返回体封装结果。

然后把新模块名加入 root `pom.xml` 的 `<modules>`。

## 第四步：自检

```
!`mvn -q -DskipTests -T1C compile 2>&1 | tail -30`
```

- 编译通过才算完成；失败需修复后再交付。
- 提醒用户：本仓库的 pre-commit 钩子也会在提交时跑同样的编译校验，且 Nacos 真实凭据来自构建 profile，不应提交进仓库。

## 约束

- **不要硬编码任何 Nacos/数据库/Redis 密码**——一律用 `@nacos.*@` 占位符或本地回退配置。
- 端口、artifactId 必须唯一；不要改动其它无关模块。
- 保持与现有模块一致的技术选型（Nacos、Knife4j、common-core），不要引入未经评审的新框架。
