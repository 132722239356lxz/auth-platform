package com.liang.xz.system.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * <p>MyBatis 配置类</p>
 * <p>配置 Mapper 扫描路径，配合 spring-boot-starter 自动配置使用</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Configuration
@MapperScan("com.liang.xz.system.mapper")
public class MybatisPlusConfig {
}
