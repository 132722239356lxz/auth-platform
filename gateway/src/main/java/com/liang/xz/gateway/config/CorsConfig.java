package com.liang.xz.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * <p>Gateway 跨域配置 (响应式)</p>
 *
 * <p>配合 Spring Security 使用，确保 OPTIONS 预检请求不会被拦截。
 * application.yml 中的 globalcors 配置仅对 Gateway 路由生效，
 * 此处提供 CorsConfigurationSource Bean 供 Security 链条使用。</p>
 *
 * @author auth-platform
 * @since 1.1.0
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 允许的来源（本地开发 + 前端域名）
        config.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "http://192.168.*.*:*",
                "https://*.example.com"
        ));

        // 允许所有请求头
        config.addAllowedHeader("*");

        // 允许的 HTTP 方法
        config.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));

        // 允许携带认证信息（Cookie / Authorization Header）
        config.setAllowCredentials(true);

        // 预检请求缓存时间（秒）
        config.setMaxAge(3600L);

        // 暴露的响应头
        config.addExposedHeader("Authorization");
        config.addExposedHeader("Content-Disposition");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
