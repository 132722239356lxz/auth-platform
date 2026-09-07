package com.liang.xz.gateway.config;

import com.liang.xz.gateway.security.TokenBlacklistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * <p>网关 Token 安全配置 —— 注册吊销黑名单服务</p>
 *
 * <p>使 {@code AuthGlobalFilter} 能在入口处拦截用户退出登录后已失效的 Token。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class TokenSecurityConfig {

    @Bean
    @ConditionalOnMissingBean(TokenBlacklistService.class)
    public TokenBlacklistService tokenBlacklistService(
            ReactiveStringRedisTemplate reactiveStringRedisTemplate) {
        log.info("[Gateway] 启用 Token 吊销黑名单校验");
        return new TokenBlacklistService(reactiveStringRedisTemplate);
    }
}
