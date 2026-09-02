package com.liang.xz.log.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * <p>log-server 安全配置 —— 放行内部日志收集接口，不启用 OAuth2 资源认证</p>
 *
 * <p>背景: common-core 中引入了 spring-boot-starter-oauth2-resource-server，
 * 导致所有依赖 common-core 的模块默认要求 Bearer Token 认证。
 * log-server 是内部服务，日志上报 API 不需要认证。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Configuration
public class LogServerSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain logServerSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/**")
                .authorizeHttpRequests(auth -> auth
                        // 日志上报接口 — 各模块内网直连调用，无需认证
                        .requestMatchers("/api/logs/collect/**").permitAll()
                        // 日志查询/分析接口 — 前端通过网关访问，网关已统一鉴权
                        .requestMatchers("/api/logs/**").permitAll()
                        // Knife4j/Swagger 文档
                        .requestMatchers(
                                "/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**", "/doc.html", "/webjars/**"
                        ).permitAll()
                        // Actuator 健康检查
                        .requestMatchers("/actuator/**").permitAll()
                        // 其余请求拒绝
                        .anyRequest().denyAll()
                )
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .oauth2ResourceServer(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
