package com.liang.xz.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * <p>Gateway限流配置 —— 基于令牌桶算法的请求限流</p>
 *
 * <p>限流维度:</p>
 * <ul>
 *   <li><b>IP限流:</b> ipKeyResolver —— 按客户端IP限流</li>
 *   <li><b>用户限流:</b> userKeyResolver —— 按JWT中的用户限流</li>
 *   <li><b>接口限流:</b> apiKeyResolver —— 按请求路径限流</li>
 * </ul>
 *
 * <p>使用方式:</p>
 * <pre>
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *         - id: auth-server
 *           filters:
 *             - name: RequestRateLimiter
 *               args:
 *                 key-resolver: "#{@ipKeyResolver}"
 *                 redis-rate-limiter.replenishRate: 100
 *                 redis-rate-limiter.burstCapacity: 200
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Configuration
public class RateLimiterConfig {

    /**
     * 按客户端IP限流（默认限流策略）
     */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String ip = Objects.requireNonNullElse(
                    exchange.getRequest().getHeaders().getFirst("X-Forwarded-For"),
                    exchange.getRequest().getRemoteAddress() != null
                            ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                            : "unknown"
            );
            return Mono.just(ip);
        };
    }

    /**
     * 按用户限流（从JWT或请求头提取用户标识）
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String user = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (user == null) {
                user = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");
            }
            if (user == null) {
                user = "anonymous";
            }
            return Mono.just(user);
        };
    }

    /**
     * 按API路径限流
     */
    @Bean
    public KeyResolver apiKeyResolver() {
        return exchange -> Mono.just(exchange.getRequest().getPath().value());
    }
}
