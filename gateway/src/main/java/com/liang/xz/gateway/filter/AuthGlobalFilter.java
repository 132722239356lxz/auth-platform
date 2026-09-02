package com.liang.xz.gateway.filter;

import com.liang.xz.gateway.properties.GatewayPublicApiProperties;
import com.liang.xz.gateway.security.JwtJtiResolver;
import com.liang.xz.gateway.security.TokenBlacklistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>全局认证过滤器 —— JWT Token校验 + 请求日志</p>
 *
 * <p>功能:</p>
 * <ul>
 *   <li>校验请求头中的 Authorization: Bearer {token}</li>
 *   <li>记录所有请求日志(方法、路径、耗时)</li>
 *   <li>透传认证信息到下游服务</li>
 *   <li>白名单路径直接放行（内置白名单 + 配置的 public-api-paths）</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** 内置白名单（OAuth2端点、文档、健康检查等基建路径） */
    private static final List<String> BUILT_IN_WHITE_LIST = List.of(
            "/auth-server/oauth2/**",
            "/auth-server/login",
            "/auth-server/login/**",
            "/auth-server/login-success",
            "/auth-server/logout",
            "/auth-server/api/crypto/public-key",
            "/auth-server/api/auth/login",
            "/auth-server/api/auth/refresh",
            "/auth-server/api/register",
            "/auth-server/.well-known/**",
            "/auth-server/actuator/health",
            "/actuator/health",
            // Knife4j/Swagger 文档聚合（所有服务）
            "/doc.html",
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/v3/api-docs/**",
            "/webjars/**",
            "/favicon.ico",
            // 各服务通过Gateway的API文档路径
            "/auth-server/v3/api-docs/**",
            "/auth-flow/v3/api-docs/**",
            "/auth-message/v3/api-docs/**",
            "/ai-agent-server/v3/api-docs/**",
            "/system-server/v3/api-docs/**",
            "/log-server/v3/api-docs/**",
            // fallback端点
            "/fallback/**"
    );

    private final GatewayPublicApiProperties gatewayPublicApiProperties;
    private final TokenBlacklistService tokenBlacklistService;

    public AuthGlobalFilter(GatewayPublicApiProperties gatewayPublicApiProperties,
                            TokenBlacklistService tokenBlacklistService) {
        this.gatewayPublicApiProperties = gatewayPublicApiProperties;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.currentTimeMillis();
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();

        // 白名单路径（内置 + 配置的 @PublicApi 路径）直接放行
        if (isWhiteListed(path)) {
            log.debug("[Gateway] 白名单放行: {} {}", method, path);
            return chain.filter(exchange).doFinally(s -> logAccess(start, method, path, HttpStatus.OK.value()));
        }

        // 检查Authorization头
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("[Gateway] 缺少Token: {} {}", method, path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // 吊销校验：用户退出登录后，其 Token 的 jti 会被登记进 Redis 黑名单。
        // 网关作为统一入口在此拦截，避免已注销的 Token 继续访问下游服务。
        String tokenValue = authHeader.substring(7).trim();
        String jti = JwtJtiResolver.resolveJti(tokenValue);

        return tokenBlacklistService.isRevoked(jti)
                .flatMap(revoked -> {
                    if (Boolean.TRUE.equals(revoked)) {
                        log.warn("[Gateway] Token已被吊销: {} {}", method, path);
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        exchange.getResponse().getHeaders().setContentType(
                                org.springframework.http.MediaType.APPLICATION_JSON);
                        String body = "{\"code\":401,\"message\":\"Token已被吊销，请重新登录\",\"data\":null}";
                        return exchange.getResponse().writeWith(
                                reactor.core.publisher.Mono.just(exchange.getResponse()
                                        .bufferFactory().wrap(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
                    }

                    // Token由OAuth2 Resource Server自动校验（Gateway配置中jwt解码器）
                    // 这里只做透传和日志
                    ServerHttpRequest mutatedRequest = request.mutate()
                            .header("X-Gateway-Timestamp", String.valueOf(System.currentTimeMillis()))
                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build())
                            .doFinally(s -> logAccess(start, method, path,
                                    exchange.getResponse().getStatusCode() != null
                                            ? exchange.getResponse().getStatusCode().value() : 500));
                });
    }

    @Override
    public int getOrder() {
        // 必须早于 Spring Security WebFilterChainProxy（默认 -100），
        // 否则未认证请求会先被 Spring Security 302 重定向到 /login
        return -200;
    }

    /**
     * 判断路径是否在白名单中（内置白名单 + 配置的 @PublicApi 路径）
     */
    private boolean isWhiteListed(String path) {
        // 先检查内置白名单
        boolean builtIn = BUILT_IN_WHITE_LIST.stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
        if (builtIn) {
            return true;
        }
        // 再检查配置的 @PublicApi 路径
        List<String> publicPaths = gatewayPublicApiProperties.getPublicApiPaths();
        return publicPaths.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    /**
     * 记录访问日志
     */
    private void logAccess(long start, String method, String path, int status) {
        long cost = System.currentTimeMillis() - start;
        if (status >= 400) {
            log.warn("[Gateway] {} {} -> {} ({}ms)", method, path, status, cost);
        } else {
            log.info("[Gateway] {} {} -> {} ({}ms)", method, path, status, cost);
        }
    }
}
