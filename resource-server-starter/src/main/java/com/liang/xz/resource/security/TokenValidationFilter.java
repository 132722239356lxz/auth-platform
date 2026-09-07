package com.liang.xz.resource.security;

import com.liang.xz.common.core.token.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * <p>Token验证过滤器 —— 对已通过JWT签名的Token进行额外安全检查</p>
 *
 * <p>检查项:</p>
 * <ul>
 *   <li><b>Token过期检查:</b> 验证JWT的exp字段，Spring Security NimbusJwtDecoder已自动验证，
 *       此过滤器做二次确认并记录详细日志</li>
 *   <li><b>用户存在性检查:</b> 通过 {@link UserPermissionProvider#isUserBlocked} 判断用户是否被禁用/黑名单</li>
 *   <li><b>Token黑名单检查:</b> 支持内存黑名单(可扩展为Redis)</li>
 *   <li><b>JWT Claims完整性校验:</b> 验证必要的claims是否存在</li>
 * </ul>
 *
 * <p>配置方式:</p>
 * <pre>
 * auth:
 *   resource:
 *     token:
 *       check-user-status: true      # 是否检查用户状态
 *       check-token-blacklist: false # 是否检查Token黑名单
 *       required-claims:             # 必要的JWT claims
 *         - sub
 *         - user_id
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
public class TokenValidationFilter extends OncePerRequestFilter {

    private final UserPermissionProvider permissionProvider;
    private final TokenValidationProperties properties;

    /**
     * Token 黑名单服务（Redis 实现，可为 null 表示当前服务未接入 Redis）。
     * <p>原先使用内存 ConcurrentHashMap，存在两个致命问题：
     * 多实例部署时各实例黑名单不共享（在 A 实例退出登录，B 实例仍放行），
     * 且应用重启后黑名单丢失。改为 Redis 后上述问题均得到解决。</p>
     */
    private final TokenBlacklistService tokenBlacklistService;

    public TokenValidationFilter(UserPermissionProvider permissionProvider,
                                  TokenValidationProperties properties,
                                  TokenBlacklistService tokenBlacklistService) {
        this.permissionProvider = permissionProvider;
        this.properties = properties;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        // 只处理JWT认证
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 1. Token过期检查(补充性校验，Spring Security已做主要验证)
            if (properties.isCheckTokenExpiry()) {
                checkTokenExpiry(jwt, request, response);
            }

            // 2. Token黑名单检查
            if (properties.isCheckTokenBlacklist()) {
                checkTokenBlacklist(jwt);
            }

            // 3. 用户状态检查(禁用/黑名单)
            if (properties.isCheckUserStatus()) {
                checkUserStatus(jwt, request);
            }

            // 4. JWT Claims完整性校验
            if (properties.getRequiredClaims() != null && !properties.getRequiredClaims().isEmpty()) {
                checkRequiredClaims(jwt, request);
            }

        } catch (SecurityValidationException e) {
            log.warn("[TokenValidation] Token验证失败: path={}, reason={}", request.getRequestURI(), e.getMessage());
            response.setStatus(e.getHttpStatus());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(String.format(
                    "{\"code\":%d,\"message\":\"%s\",\"data\":null}",
                    e.getHttpStatus(), e.getMessage()));
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Token过期检查
     */
    private void checkTokenExpiry(Jwt jwt, HttpServletRequest request, HttpServletResponse response) {
        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null) {
            log.warn("[TokenValidation] JWT缺少exp字段: subject={}, path={}",
                    jwt.getSubject(), request.getRequestURI());
            return; // 不强制拦截，由Spring Security处理
        }

        if (expiresAt.isBefore(Instant.now())) {
            throw new SecurityValidationException(HttpStatus.UNAUTHORIZED.value(),
                    "Token已过期，请重新登录");
        }

        // 即将过期警告(剩余不足5分钟)
        long remainingSeconds = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        if (remainingSeconds < 300 && remainingSeconds > 0) {
            log.debug("[TokenValidation] Token即将过期: subject={}, remaining={}s",
                    jwt.getSubject(), remainingSeconds);
            response.setHeader("X-Token-Expiring-Soon", String.valueOf(remainingSeconds));
        }
    }

    /**
     * Token黑名单检查 —— 校验 jti 是否已被吊销（退出登录/强制下线/改密）。
     */
    private void checkTokenBlacklist(Jwt jwt) {
        if (tokenBlacklistService == null) {
            log.debug("[TokenValidation] 未配置Token黑名单服务，跳过吊销校验");
            return;
        }
        String jti = resolveJti(jwt);
        if (jti == null) {
            // Token 无 jti（如历史签发），无法吊销，放行由过期时间兜底
            return;
        }
        if (tokenBlacklistService.isRevoked(jti)) {
            throw new SecurityValidationException(HttpStatus.UNAUTHORIZED.value(),
                    "Token已被吊销，请重新登录");
        }
    }

    /**
     * 解析 JWT 的唯一标识，兼容 {@code jti} claim 与 Spring Security 的 {@code Jwt#getId()}。
     */
    private String resolveJti(Jwt jwt) {
        String jti = jwt.getClaimAsString("jti");
        if (jti != null && !jti.isBlank()) {
            return jti;
        }
        return jwt.getId();
    }

    /**
     * 用户状态检查 —— 检查用户是否被禁用或处于黑名单
     */
    private void checkUserStatus(Jwt jwt, HttpServletRequest request) {
        if (permissionProvider == null) {
            return;
        }

        String username = jwt.getSubject();
        Long userId = resolveUserId(jwt);

        if (permissionProvider.isUserBlocked(username, userId)) {
            throw new SecurityValidationException(HttpStatus.FORBIDDEN.value(),
                    "用户已被禁用，无法访问系统");
        }
    }

    /**
     * JWT必要Claims完整性校验
     */
    private void checkRequiredClaims(Jwt jwt, HttpServletRequest request) {
        for (String claim : properties.getRequiredClaims()) {
            if (jwt.getClaim(claim) == null) {
                throw new SecurityValidationException(HttpStatus.UNAUTHORIZED.value(),
                        "Token缺少必要字段: " + claim);
            }
        }
    }

    /**
     * 从JWT中解析用户ID
     */
    private Long resolveUserId(Jwt jwt) {
        String userIdStr = jwt.getClaimAsString("user_id");
        if (userIdStr != null) {
            try {
                return Long.valueOf(userIdStr);
            } catch (NumberFormatException e) {
                log.debug("[TokenValidation] 无法解析user_id: {}", userIdStr);
            }
        }
        return null;
    }

}
