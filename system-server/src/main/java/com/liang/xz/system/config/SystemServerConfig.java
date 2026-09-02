package com.liang.xz.system.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * <p>系统管理服务配置 —— 提供PasswordEncoder等基础设施Bean</p>
 *
 * @author auth-platform
 * @since 1.1.0
 */
@Slf4j
@Configuration
public class SystemServerConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        log.info("[SystemConfig] 初始化BCryptPasswordEncoder");
        return new BCryptPasswordEncoder();
    }
}
