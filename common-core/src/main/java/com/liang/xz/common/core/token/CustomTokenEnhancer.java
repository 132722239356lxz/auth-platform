package com.liang.xz.common.core.token;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * <p>自定义Token增强器 —— 在JWT Token中添加租户、用户身份等自定义字段</p>
 *
 * <p>增强时机: 每次签发 AccessToken / ID Token 时触发</p>
 *
 * <p>添加的Claims:</p>
 * <ul>
 *   <li>tenant_id: 租户ID(从授权请求参数或用户属性中获取)</li>
 *   <li>user_type: 用户类型</li>
 *   <li>client_type: 客户端类型</li>
 *   <li>user_id: 用户ID(数据库主键)</li>
 *   <li>nickname: 用户昵称</li>
 *   <li>jti: JWT唯一标识(用于退出登录/强制下线时的Token吊销黑名单)</li>
 * </ul>
 *
 * <p>注意: permissions 和 scope 不再注入 JWT，权限校验由资源服务器的
 * {@code PermissionAspect} 通过 DB 查询兜底完成。</p>
 *
 * <p><b>jti 的重要性:</b>JWT 无状态，签发后无法天然失效。退出登录时只能凭 jti
 * 将 Token 登记进 Redis 黑名单（见 {@link TokenBlacklistService}）才能真正终止会话。
 * Spring Authorization Server 默认不生成该 claim，故在此显式注入。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomTokenEnhancer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    /** 默认租户ID */
    private static final String DEFAULT_TENANT = "default";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void customize(JwtEncodingContext context) {
        OAuth2Authorization authorization = context.getAuthorization();
        if (authorization == null) {
            return;
        }

        JwtClaimsSet.Builder claims = context.getClaims();
        String username = authorization.getPrincipalName();

        // 0. 添加 jti（JWT唯一标识）—— 退出登录吊销 Token 依赖此 claim
        claims.claim(JwtClaimNames.JTI, UUID.randomUUID().toString());

        // 1. 添加租户ID
        String tenantId = resolveTenantId(authorization);
        claims.claim(CustomTokenClaims.CLAIM_TENANT_ID, tenantId);

        // 2. 添加用户类型
        String userType = resolveUserType(authorization, username);
        if (userType != null) {
            claims.claim(CustomTokenClaims.CLAIM_USER_TYPE, userType);
        }

        // 3. 添加客户端类型
        String clientType = resolveClientType(authorization);
        if (clientType != null) {
            claims.claim(CustomTokenClaims.CLAIM_CLIENT_TYPE, clientType);
        }

        // 4. 添加用户ID: 优先从 authorization 属性读取，避免重复 DB 查询
        Long userId = resolveUserId(authorization, username);
        if (userId != null) {
            claims.claim(CustomTokenClaims.CLAIM_USER_ID, String.valueOf(userId));
        }

        // 5. 添加用户昵称: 优先从 authorization 属性读取
        String nickname = resolveNickname(authorization, username);
        if (nickname != null) {
            claims.claim(CustomTokenClaims.CLAIM_NICKNAME, nickname);
        }
    }

    // ======================== 参数解析（属性优先 → DB/默认值兜底） ========================

    /**
     * 解析租户ID
     * 优先级: authorization 属性 > 附加参数 > 默认值
     */
    private String resolveTenantId(OAuth2Authorization authorization) {
        // 优先从 authorization 属性读取
        String attrValue = authorization.getAttribute(CustomTokenClaims.CLAIM_TENANT_ID);
        if (attrValue != null && !attrValue.isEmpty()) {
            return attrValue;
        }
        // 回退：从附加参数读取（标准 OAuth2 端点流程）
        Map<String, Object> additionalParams = getAdditionalParams(authorization);
        if (additionalParams.containsKey(CustomTokenClaims.CLAIM_TENANT_ID)) {
            return String.valueOf(additionalParams.get(CustomTokenClaims.CLAIM_TENANT_ID));
        }
        return DEFAULT_TENANT;
    }

    /**
     * 解析用户类型
     * 优先级: authorization 属性 > 附加参数 > DB查询 > 默认值
     */
    private String resolveUserType(OAuth2Authorization authorization, String username) {
        // 优先从 authorization 属性读取
        String attrValue = authorization.getAttribute(CustomTokenClaims.CLAIM_USER_TYPE);
        if (attrValue != null && !attrValue.isEmpty()) {
            return attrValue;
        }
        // 回退：附加参数
        Map<String, Object> additionalParams = getAdditionalParams(authorization);
        if (additionalParams.containsKey(CustomTokenClaims.CLAIM_USER_TYPE)) {
            return String.valueOf(additionalParams.get(CustomTokenClaims.CLAIM_USER_TYPE));
        }
        // 兜底：DB 查询
        return resolveUserTypeFromDb(username);
    }

    /**
     * 解析客户端类型
     */
    private String resolveClientType(OAuth2Authorization authorization) {
        Map<String, Object> additionalParams = getAdditionalParams(authorization);
        if (additionalParams.containsKey(CustomTokenClaims.CLAIM_CLIENT_TYPE)) {
            return String.valueOf(additionalParams.get(CustomTokenClaims.CLAIM_CLIENT_TYPE));
        }
        return null;
    }

    /**
     * 从授权请求中获取附加参数（标准 OAuth2 端点流程）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getAdditionalParams(OAuth2Authorization authorization) {
        Object params = authorization.getAttribute(
                "org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames");
        if (params instanceof Map) {
            return (Map<String, Object>) params;
        }
        return Map.of();
    }

    // ======================== Claims 解析（属性优先 → DB 兜底） ========================

    /**
     * 获取用户ID：优先从 authorization 属性读取（buildAuthorization 已设置），
     * 未命中时回退到 DB 查询（兼容标准 OAuth2 流程）。
     */
    private Long resolveUserId(OAuth2Authorization authorization, String username) {
        String rawId = authorization.getAttribute(CustomTokenClaims.CLAIM_USER_ID);
        if (rawId != null && !rawId.isEmpty()) {
            try {
                return Long.valueOf(rawId);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return resolveUserIdFromDb(username);
    }

    /**
     * 获取用户昵称：优先从 authorization 属性读取，未命中时回退 DB。
     */
    private String resolveNickname(OAuth2Authorization authorization, String username) {
        String attrNickname = authorization.getAttribute(CustomTokenClaims.CLAIM_NICKNAME);
        if (attrNickname != null && !attrNickname.isEmpty()) {
            return attrNickname;
        }
        return resolveNicknameFromDb(username);
    }

    // ======================== 数据库查询（兜底） ========================

    /**
     * 从数据库查询用户ID（优先手机号，兼容username）
     */
    private Long resolveUserIdFromDb(String principalName) {
        try {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT id FROM sys_user WHERE username = ? OR phone = ? LIMIT 1",
                    Long.class, principalName, principalName);
            return ids.isEmpty() ? null : ids.get(0);
        } catch (Exception e) {
            log.debug("[TokenEnhancer] 查询用户ID失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从数据库查询用户昵称（优先username，兼容phone）
     */
    private String resolveNicknameFromDb(String principalName) {
        try {
            List<String> nicknames = jdbcTemplate.queryForList(
                    "SELECT nickname FROM sys_user WHERE username = ? OR phone = ? LIMIT 1",
                    String.class, principalName, principalName);
            return nicknames.isEmpty() ? null : nicknames.get(0);
        } catch (Exception e) {
            log.debug("[TokenEnhancer] 查询昵称失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从数据库查询用户类型（兜底）
     */
    private String resolveUserTypeFromDb(String principalName) {
        try {
            List<String> types = jdbcTemplate.queryForList(
                    "SELECT user_type FROM sys_user WHERE username = ? OR phone = ? LIMIT 1",
                    String.class, principalName, principalName);
            if (!types.isEmpty() && types.get(0) != null) {
                return types.get(0);
            }
        } catch (Exception e) {
            log.debug("[TokenEnhancer] 查询user_type失败: {}", e.getMessage());
        }
        return "user";
    }
}
