package com.liang.xz.gateway.config;

import com.liang.xz.gateway.properties.GatewayPublicApiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

/**
 * <p>Gateway 安全配置</p>
 *
 * <p>放行 Knife4j/Swagger 文档聚合路径、健康检查、OAuth2端点、
 * 以及 {@code auth.gateway.public-api-paths} 配置的 @PublicApi 公开接口路径，
 * 其余请求由 OAuth2 Resource Server 校验 JWT</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * ReactiveJwtDecoder —— 从 auth-server 的 JWKS 端点获取公钥验签。
     *
     * <p>优先使用 Nacos 配置的 {@code auth.security.jwk-set-uri}，
     * 若不可用则回退到本地默认值（开发环境直连 auth-server:9000）。</p>
     */
    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(
            @Value("${auth.security.jwk-set-uri:http://127.0.0.1:9000/oauth2/jwks}") String jwkSetUri) {
        log.info("[Gateway Security] 初始化 ReactiveJwtDecoder: jwkSetUri={}", jwkSetUri);
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    /**
     * 文档端点专用 Security Filter Chain —— 完全不启用 JWT 认证，避免无效 Token 导致 401。
     *
     * <p>优先级最高，专门处理各服务的 /v3/api-docs 及 Swagger 静态资源。</p>
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityWebFilterChain swaggerSecurityWebFilterChain(ServerHttpSecurity http) {
        http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers(
                        "/doc.html",
                        "/swagger-ui/**",
                        "/swagger-resources/**",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        "/favicon.ico",
                        "/auth-server/v3/api-docs/**",
                        "/auth-flow/v3/api-docs/**",
                        "/auth-message/v3/api-docs/**",
                        "/ai-agent-server/v3/api-docs/**",
                        "/system-server/v3/api-docs/**",
                        "/log-server/v3/api-docs/**"
                ))
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .csrf(ServerHttpSecurity.CsrfSpec::disable);

        return http.build();
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            GatewayPublicApiProperties publicApiProperties,
            ReactiveJwtDecoder reactiveJwtDecoder) {

        http
                .authorizeExchange(exchanges -> {
                    exchanges
                            // 健康检查和 fallback 端点放行
                            .pathMatchers(
                                    "/actuator/health",
                                    "/fallback/**"
                            ).permitAll()
                            // OAuth2 端点放行（auth-server 的 /oauth2/** 路由）
                            .pathMatchers("/auth-server/oauth/**").permitAll()
                            .pathMatchers("/auth-server/.well-known/**").permitAll()
                            // SSO 登录相关页面放行
                            .pathMatchers("/auth-server/login").permitAll()
                            .pathMatchers("/auth-server/login-success").permitAll()
                            .pathMatchers("/auth-server/logout").permitAll()
                            .pathMatchers("/auth-server/register").permitAll()
                            // 获取密钥
                            .pathMatchers("/auth-server/api/crypto/public-key").permitAll()
                            // REST 公开接口放行
                            .pathMatchers("/auth-server/api/auth/login").permitAll()
                            .pathMatchers("/auth-server/api/auth/refresh").permitAll()
                            .pathMatchers("/auth-server/api/register").permitAll();

                    // 动态注册 @PublicApi 配置的公开路径
                    if (!publicApiProperties.getPublicApiPaths().isEmpty()) {
                        String[] publicPaths = publicApiProperties.getPublicApiPaths()
                                .toArray(new String[0]);
                        exchanges.pathMatchers(publicPaths).permitAll();
                    }

                    exchanges.anyExchange().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtDecoder(reactiveJwtDecoder))
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(ServerHttpSecurity.CsrfSpec::disable);

        return http.build();
    }
}
