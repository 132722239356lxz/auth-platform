package com.liang.xz.common.core.token;

import lombok.Data;

/**
 * <p>自定义Token附加字段 —— 在JWT Token中扩展的Claims</p>
 *
 * <p>扩展字段:</p>
 * <ul>
 *   <li><b>tenant_id:</b> 租户ID，实现多租户隔离</li>
 *   <li><b>user_type:</b> 用户类型(admin/user/service等)</li>
 *   <li><b>client_type:</b> 客户端类型(web/mobile/iot等)</li>
 *   <li><b>extra_claims:</b> 其他自定义扩展属性(JSON格式)</li>
 * </ul>
 *
 * <p>JWT Token 最终结构:</p>
 * <pre>
 * {
 *   "sub": "user-123",
 *   "iss": "http://127.0.0.1:9000",
 *   "aud": ["my-mobile-app"],
 *   "scope": ["openid", "profile", "read"],
 *   "tenant_id": "tenant-001",         ← 自定义
 *   "user_type": "admin",              ← 自定义
 *   "client_type": "web",              ← 自定义
 *   "iat": 1717747200,
 *   "exp": 1717750800
 * }
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
public class CustomTokenClaims {

    /** 租户ID —— 实现多租户数据隔离 */
    public static final String CLAIM_TENANT_ID = "tenant_id";

    /** 用户类型 —— 用于角色/权限区分 */
    public static final String CLAIM_USER_TYPE = "user_type";

    /** 客户端类型 —— 区分接入端类型 */
    public static final String CLAIM_CLIENT_TYPE = "client_type";

    /** 扩展属性 —— JSON格式存储业务自定义数据 */
    public static final String CLAIM_EXTRA = "extra_claims";

    /** 用户ID —— 用于system-server查询角色权限 */
    public static final String CLAIM_USER_ID = "user_id";

    /** 用户昵称 —— 前端展示用 */
    public static final String CLAIM_NICKNAME = "nickname";

    /** 用户权限标识列表 —— 从 用户→角色→菜单 链路查询的权限标识 */
    public static final String CLAIM_PERMISSIONS = "permissions";

    /** 手机号 —— OIDC phone_number claim */
    public static final String CLAIM_PHONE_NUMBER = "phone_number";

    // ======================== 字段 ========================

    /** 租户ID */
    private String tenantId;

    /** 用户类型 */
    private String userType;

    /** 客户端类型 */
    private String clientType;

    /** 扩展属性(JSON) */
    private String extraClaims;

    /**
     * 创建默认的自定义Token声明(仅租户ID)
     */
    public static CustomTokenClaims ofTenant(String tenantId) {
        CustomTokenClaims claims = new CustomTokenClaims();
        claims.setTenantId(tenantId);
        return claims;
    }

    /**
     * 创建完整的自定义Token声明
     */
    public static CustomTokenClaims of(String tenantId, String userType, String clientType) {
        CustomTokenClaims claims = new CustomTokenClaims();
        claims.setTenantId(tenantId);
        claims.setUserType(userType);
        claims.setClientType(clientType);
        return claims;
    }
}
