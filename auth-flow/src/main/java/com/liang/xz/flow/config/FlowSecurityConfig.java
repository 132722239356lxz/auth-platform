package com.liang.xz.flow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * <p>auth-flow 安全配置 —— 启用 OAuth2 资源服务器 JWT 校验</p>
 *
 * <p>JWT 由 Gateway AuthGlobalFilter 透传，auth-flow 再次校验并填充 SecurityContext，
 * 使 {@link com.liang.xz.resource.security.PermissionAspect} 能提取用户权限进行细粒度鉴权。</p>
 *
 * <p>JWK 公钥地址通过 Nacos auth-security.yml 下发:
 * spring.security.oauth2.resourceserver.jwt.jwk-set-uri</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Configuration
public class FlowSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain flowSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/**")
                .authorizeHttpRequests(auth -> auth
                        // API 请求需要 Bearer Token，细粒度权限由 @RequirePermission 控制
                        .requestMatchers("/api/**").authenticated()
                        // Knife4j/Swagger 文档公开
                        .requestMatchers(
                                "/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**", "/doc.html", "/webjars/**"
                        ).permitAll()
                        // Actuator 健康检查公开
                        .requestMatchers("/actuator/**").permitAll()
                        // 其余请求拒绝
                        .anyRequest().denyAll()
                )
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }
}
