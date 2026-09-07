package com.liang.xz.server.config;

import com.liang.xz.common.core.token.RedisTokenBlacklistService;
import com.liang.xz.common.core.token.TokenBlacklistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * <p>Token 安全相关配置 —— 注销/吊销能力的基础设施</p>
 *
 * <p>注册基于 Redis 的 Token 黑名单服务，供 {@code LogoutService} 在用户退出登录时
 * 登记已吊销的 Token，实现"退出登录后 Token 立即失效"。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class TokenSecurityConfig {

    /**
     * Token 黑名单服务（Redis 实现）。
     * <p>与各资源服务器（resource-server-starter）共用同一套 Redis Key 规范，
     * 保证在 auth-server 注销后，全站所有服务都能立即拒绝该 Token。</p>
     */
    @Bean
    @ConditionalOnMissingBean(TokenBlacklistService.class)
    public TokenBlacklistService tokenBlacklistService(StringRedisTemplate stringRedisTemplate) {
        log.info("[AuthServer] 启用 Redis Token 黑名单服务");
        return new RedisTokenBlacklistService(stringRedisTemplate);
    }
}
