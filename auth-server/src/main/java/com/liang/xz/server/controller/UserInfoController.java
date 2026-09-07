package com.liang.xz.server.controller;

import com.liang.xz.common.core.token.CustomTokenClaims;
import com.liang.xz.server.dto.ApiResponse;
import com.liang.xz.server.dto.UserInfoResponse;
import com.liang.xz.server.repository.UserRepository;
import com.liang.xz.server.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * <p>用户信息控制器 —— 子系统通过Token获取用户详细信息</p>
 *
 * <p>OAuth2标准端点:</p>
 * <ul>
 *   <li><b>GET /userinfo:</b> OIDC标准用户信息端点，子系统用AccessToken获取用户信息</li>
 *   <li><b>GET /api/userinfo/extended:</b> 扩展用户信息端点，包含权限列表</li>
 *   <li><b>GET /api/userinfo/permissions:</b> 获取当前用户权限标识列表</li>
 * </ul>
 *
 * <p>子系统接入流程:</p>
 * <pre>
 *   子系统 → POST /oauth2/token (authorization_code → access_token)
 *   子系统 → GET /userinfo (Authorization: Bearer access_token → 用户信息)
 *   子系统 → 基于用户信息签发自己的JWT Token
 *   子系统 → POST /api/subsystem/tokens (记录自签Token)
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "用户信息", description = "OIDC用户信息端点及扩展用户信息查询")
public class UserInfoController {

    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    // ======================== OIDC标准用户信息端点 ========================

    /**
     * OIDC标准 /userinfo 端点
     * 子系统使用AccessToken调用此端点获取当前登录用户的基本信息
     */
    @GetMapping("/userinfo")
    @Operation(summary = "OIDC用户信息端点", description = "使用OAuth2 AccessToken获取当前登录用户的基本信息")
    public Map<String, Object> userInfo(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String principalName = jwt.getSubject();

            Map<String, Object> claims = new java.util.LinkedHashMap<>();
            claims.put(JwtClaimNames.SUB, principalName);
            claims.put("nickname", jwt.getClaimAsString(CustomTokenClaims.CLAIM_NICKNAME));
            claims.put("user_type", jwt.getClaimAsString(CustomTokenClaims.CLAIM_USER_TYPE));
            claims.put("tenant_id", jwt.getClaimAsString(CustomTokenClaims.CLAIM_TENANT_ID));
            claims.put("user_id", jwt.getClaimAsString(CustomTokenClaims.CLAIM_USER_ID));
            claims.put("phone_number", jwt.getClaimAsString("phone_number"));

            log.debug("[UserInfo] OIDC用户信息查询: principal={}", principalName);
            return claims;
        }

        log.warn("[UserInfo] 未认证请求");
        return Map.of("error", "unauthorized");
    }

    // ======================== 扩展用户信息端点 ========================

    /**
     * 扩展用户信息端点 —— 子系统用AccessToken获取完整用户信息(含权限)
     */
    @GetMapping("/api/userinfo/extended")
    @Operation(summary = "扩展用户信息", description = "获取当前登录用户的完整信息(基本信息+权限列表)")
    public ApiResponse<UserInfoResponse> extendedUserInfo(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            return ApiResponse.fail(401, "未认证或认证类型不支持");
        }

        Jwt jwt = jwtAuth.getToken();
        String principalName = jwt.getSubject();

        // 从数据库查询用户详细信息（优先按手机号，兼容 username）
        var userOpt = userRepository.findByPhoneIncludeDisabled(principalName);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByUsername(principalName);
        }
        if (userOpt.isEmpty()) {
            return ApiResponse.fail(404, "用户不存在: " + principalName);
        }
        var user = userOpt.get();

        // 查询权限
        Long userId = user.getId();
        List<String> permissions = resolveUserPermissions(userId);

        // 构造响应
        UserInfoResponse response = UserInfoResponse.builder()
                .userId(userId)
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .phone(user.getPhone())
                .userType(user.getUserType())
                .tenantId(user.getTenantId())
                .permissions(permissions)
                .accessToken(jwt.getTokenValue())
                .tokenType("Bearer")
                .expiresIn(calculateExpiresIn(jwt))
                .scope(jwt.getClaimAsString("scope"))
                .issuedAt(toLocalDateTime(jwt.getIssuedAt()))
                .expiresAt(toLocalDateTime(jwt.getExpiresAt()))
                .build();

        log.info("[UserInfo] 扩展用户信息查询: phone={}, permissions={}", user.getPhone(), permissions.size());
        return ApiResponse.success(response);
    }

    /**
     * 用户权限查询端点
     */
    @GetMapping("/api/userinfo/permissions")
    @Operation(summary = "用户权限查询", description = "获取当前登录用户的权限标识列表")
    public ApiResponse<Map<String, Object>> userPermissions(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            return ApiResponse.fail(401, "未认证或认证类型不支持");
        }

        Jwt jwt = jwtAuth.getToken();
        String principalName = jwt.getSubject();
        String userIdStr = jwt.getClaimAsString(CustomTokenClaims.CLAIM_USER_ID);

        List<String> permissions;
        if (userIdStr != null) {
            permissions = resolveUserPermissions(Long.parseLong(userIdStr));
        } else {
            permissions = Collections.emptyList();
        }

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("phone", principalName);
        result.put("permissions", permissions);

        return ApiResponse.success(result);
    }

    // ======================== 通过授权码查询用户信息(供子系统调试) ========================

    /**
     * 通过授权码查询用户信息(仅限内部调试，生产环境需增加安全控制)
     * <p>子系统在获取到authorization_code后，可用此端点确认用户信息</p>
     */
    @GetMapping("/api/userinfo/by-code")
    @Operation(summary = "通过授权码查询用户信息(调试用)",
            description = "子系统使用授权码查询即将获取Token的用户信息，方便调试排查问题")
    public ApiResponse<Map<String, Object>> userInfoByCode(
            @Parameter(description = "授权码") @RequestParam String code,
            @Parameter(description = "客户端ID") @RequestParam String clientId) {

        // 查询授权码对应的授权记录
        try {
            List<Map<String, Object>> records = jdbcTemplate.queryForList(
                    "SELECT principal_name, authorized_scopes, authorization_code_expires_at " +
                    "FROM oauth2_authorization " +
                    "WHERE authorization_code_value LIKE ? AND registered_client_id = ?",
                    code + "%", clientId);

            if (records.isEmpty()) {
                return ApiResponse.fail(404, "授权码无效或已过期");
            }

            Map<String, Object> record = records.get(0);
            String principalName = (String) record.get("principal_name");

            // 优先按手机号查询，兼容 username
            var userOpt = userRepository.findByPhoneIncludeDisabled(principalName);
            if (userOpt.isEmpty()) {
                userOpt = userRepository.findByUsername(principalName);
            }
            if (userOpt.isEmpty()) {
                return ApiResponse.fail(404, "用户不存在: " + principalName);
            }
            var user = userOpt.get();

            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("phone", user.getPhone());
            result.put("username", user.getUsername());
            result.put("nickname", user.getNickname());
            result.put("userType", user.getUserType());
            result.put("tenantId", user.getTenantId());
            result.put("scopes", record.get("authorized_scopes"));
            result.put("codeExpiresAt", record.get("authorization_code_expires_at"));

            log.info("[UserInfo] 授权码查询: clientId={}, phone={}", clientId, user.getPhone());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[UserInfo] 授权码查询失败: {}", e.getMessage());
            return ApiResponse.fail(500, "查询失败: " + e.getMessage());
        }
    }

    // ======================== 辅助方法 ========================

    /**
     * 查询用户的所有权限标识(通过 用户→角色→菜单 链路)
     */
    private List<String> resolveUserPermissions(Long userId) {
        try {
            String sql = """
                    SELECT DISTINCT m.permission FROM sys_menu m
                    INNER JOIN sys_role_menu rm ON m.id = rm.menu_id
                    INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id
                    WHERE ur.user_id = ? AND m.enabled = 1
                    AND m.permission IS NOT NULL AND m.permission != ''
                    """;
            return jdbcTemplate.queryForList(sql, String.class, userId);
        } catch (Exception e) {
            log.warn("[UserInfo] 查询用户权限失败: userId={}, error={}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private Long calculateExpiresIn(Jwt jwt) {
        if (jwt.getExpiresAt() != null) {
            long diff = jwt.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond();
            return Math.max(0, diff);
        }
        return null;
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneId.systemDefault()) : null;
    }
}
