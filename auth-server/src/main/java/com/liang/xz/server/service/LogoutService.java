package com.liang.xz.server.service;

import com.liang.xz.common.core.token.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * <p>注销服务 —— 让"退出登录"真正终止会话</p>
 *
 * <p><b>问题背景：</b>JWT 为无状态凭证，服务端签发的 access_token 在有效期内天然可被继续使用。
 * 仅清除前端存储的 Token（或仅销毁服务端 HttpSession）并不能阻止持有者继续用旧 Token 调用接口，
 * 这就是"退出登录后账号仍可访问"的成因。</p>
 *
 * <p><b>解决方案（双管齐下）：</b></p>
 * <ol>
 *   <li><b>access_token：</b>解析出 {@code jti} 与 {@code exp}，将 jti 写入 Redis 黑名单，
 *       TTL 设为该 Token 的剩余有效期，过期后 Redis 自动清理。</li>
 *   <li><b>refresh_token：</b>通过 {@link OAuth2AuthorizationService} 直接作废旧授权记录，
 *       使刷新令牌一并失效，防止用 refresh_token 重新换取可用 Token。</li>
 * </ol>
 *
 * <p>黑名单由网关与各资源服务器共同校验（见 resource-server-starter 的
 * {@code TokenValidationFilter}），从而在全站范围内立即生效。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutService {

    private final TokenBlacklistService tokenBlacklistService;
    private final JwtDecoder jwtDecoder;
    private final OAuth2AuthorizationService authorizationService;

    /**
     * 注销当前 access_token，使其立即失效。
     *
     * @param bearerToken 形如 {@code "Bearer xxx"} 或裸 Token 字符串
     * @return 成功返回 true；Token 无法解析或已过期返回 false
     */
    public boolean logout(String bearerToken) {
        String tokenValue = extractTokenValue(bearerToken);
        if (tokenValue == null) {
            log.warn("[Logout] 未提供有效的 Token，忽略注销请求");
            return false;
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(tokenValue);
        } catch (Exception e) {
            // Token 非法或已过期：过期 Token 本就不可用，无需登记黑名单
            log.warn("[Logout] 解析 Token 失败，忽略: reason={}", e.getMessage());
            return false;
        }

        // 1. access_token 加入黑名单（TTL = 剩余有效期，到期自动清除）
        String jti = resolveJti(jwt);
        if (jti == null) {
            log.warn("[Logout] Token 缺少 jti，无法加入黑名单（请确认 CustomTokenEnhancer 已生效）");
        } else {
            Duration remaining = remainingTtl(jwt);
            tokenBlacklistService.revoke(jti, remaining);
        }

        // 2. 一并作废旧授权记录，使 refresh_token 失效
        revokeAuthorization(tokenValue);

        log.info("[Logout] 注销成功: subject={}, jti={}", jwt.getSubject(), jti);
        return true;
    }

    /**
     * 从 "Bearer xxx" 中提取裸 Token。
     */
    private String extractTokenValue(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return null;
        }
        String trimmed = bearerToken.trim();
        if (trimmed.length() > 7 && trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }

    /**
     * 解析 jti，兼容标准 claim 与 Spring Security 的 Jwt#getId()。
     */
    private String resolveJti(Jwt jwt) {
        String jti = jwt.getClaimAsString("jti");
        if (jti != null && !jti.isBlank()) {
            return jti;
        }
        return jwt.getId();
    }

    /**
     * 计算 Token 剩余有效期，作为黑名单 TTL。
     */
    private Duration remainingTtl(Jwt jwt) {
        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null) {
            // 无过期时间的 Token 兜底按 24 小时登记，避免黑名单永久化
            return Duration.ofHours(24);
        }
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /**
     * 作废旧授权记录（refresh_token 随之失效）。
     * <p>先尝试按 access_token 反查，查不到时再按 refresh_token 反查，
     * 以覆盖"仅传 refresh_token 注销"的场景。</p>
     */
    private void revokeAuthorization(String tokenValue) {
        try {
            OAuth2Authorization authorization = authorizationService.findByToken(
                    tokenValue, OAuth2TokenType.ACCESS_TOKEN);
            if (authorization == null) {
                authorization = authorizationService.findByToken(
                        tokenValue, OAuth2TokenType.REFRESH_TOKEN);
            }
            if (authorization != null) {
                authorizationService.remove(authorization);
                log.info("[Logout] 已作废旧授权记录: id={}", authorization.getId());
            }
        } catch (Exception e) {
            log.warn("[Logout] 作废旧授权记录失败（不影响黑名单生效）: {}", e.getMessage());
        }
    }
}
