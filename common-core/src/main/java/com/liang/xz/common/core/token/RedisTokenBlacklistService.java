package com.liang.xz.common.core.token;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * <p>基于 Redis 的 Token 黑名单实现（Servlet 技术栈）</p>
 *
 * <p>适用于 auth-server 及各业务资源服务器。使用 {@link StringRedisTemplate}
 * 以纯字符串存储，避免跨模块反序列化时的类型信息耦合。</p>
 *
 * <p><b>容错策略：</b>Redis 不可用时，黑名单校验降级为"未吊销"（放行），
 * 并记录告警日志。这样设计是为了避免 Redis 故障导致全站不可用；
 * 若业务要求强一致（如金融场景），可改为失败即拒绝，需调整 {@link #isRevoked(String)}。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
public class RedisTokenBlacklistService implements TokenBlacklistService {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisTokenBlacklistService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(buildKey(jti)));
        } catch (Exception e) {
            // Redis 故障不应导致整个认证链路不可用，降级放行并告警
            log.warn("[TokenBlacklist] 校验黑名单失败，降级放行: jti={}, reason={}", jti, e.getMessage());
            return false;
        }
    }

    @Override
    public void revoke(String jti, Duration remainingTtl) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        long seconds = remainingTtl == null || remainingTtl.isZero() || remainingTtl.isNegative()
                ? 0 : remainingTtl.getSeconds();
        if (seconds <= 0) {
            // Token 已过期，无需登记黑名单
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(buildKey(jti), "1", seconds, TimeUnit.SECONDS);
            log.info("[TokenBlacklist] Token 已吊销: jti={}, ttl={}s", jti, seconds);
        } catch (Exception e) {
            log.error("[TokenBlacklist] 吊销 Token 失败: jti={}", jti, e);
        }
    }
}
