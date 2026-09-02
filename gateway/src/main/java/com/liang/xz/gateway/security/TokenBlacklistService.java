package com.liang.xz.gateway.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * <p>Token 黑名单服务 —— 支撑"退出登录后 Token 立即失效"（Reactive 版）</p>
 *
 * <p>网关是所有请求的入口，在这里拦截已吊销 Token 能以最小代价终止会话，
 * 避免无效请求继续打到下游业务服务。</p>
 *
 * <p><b>Key 规范必须与 auth-server / 各资源服务器保持一致：</b>
 * {@code auth:token:blacklist:{jti}}，否则注销后网关无法识别吊销状态。
 * 该前缀与 common-core 的 {@code TokenBlacklistService} 常量定义一致，
 * 由于 gateway 未依赖 common-core，此处独立声明，改动时务必同步。</p>
 *
 * <p><b>容错：</b>Redis 不可用时降级放行，避免基础设施故障导致全站 401。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
public class TokenBlacklistService {

    /** 黑名单 Key 前缀（与 common-core 的 TokenBlacklistService 保持一致） */
    private static final String BLACKLIST_KEY_PREFIX = "auth:token:blacklist:";

    private final ReactiveStringRedisTemplate reactiveStringRedisTemplate;

    public TokenBlacklistService(ReactiveStringRedisTemplate reactiveStringRedisTemplate) {
        this.reactiveStringRedisTemplate = reactiveStringRedisTemplate;
    }

    /**
     * 判断指定 jti 是否已被吊销。
     */
    public Mono<Boolean> isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return Mono.just(false);
        }
        return reactiveStringRedisTemplate.hasKey(BLACKLIST_KEY_PREFIX + jti)
                .defaultIfEmpty(false)
                .onErrorResume(e -> {
                    log.warn("[Gateway] 校验Token黑名单失败，降级放行: jti={}, reason={}", jti, e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * 吊销指定 Token（供内部管理接口使用，如强制下线）。
     */
    public Mono<Boolean> revoke(String jti, Duration remainingTtl) {
        if (jti == null || jti.isBlank()) {
            return Mono.just(false);
        }
        return reactiveStringRedisTemplate.opsForValue()
                .set(BLACKLIST_KEY_PREFIX + jti, "1", remainingTtl)
                .onErrorResume(e -> {
                    log.error("[Gateway] 吊销Token失败: jti={}, reason={}", jti, e.getMessage());
                    return Mono.just(false);
                });
    }
}
