package com.liang.xz.common.core;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * <p>Common-Core 组件扫描兜底配置</p>
 *
 * <p>通过 AutoConfiguration 机制确保所有 @Component / @Repository
 * 注解的 Bean 被自动发现，引用方无需手动配置 scanBasePackages。</p>
 *
 * <p>该配置在 AutoConfiguration.imports 中注册，属于 Spring Boot 3.x
 * 标准自动配置机制，会在所有引用 common-core 的模块启动时自动生效。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@AutoConfiguration
@ComponentScan("com.liang.xz.common.core")
public class CommonCoreComponentScanConfiguration {
}
