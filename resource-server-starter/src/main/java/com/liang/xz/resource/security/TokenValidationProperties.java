package com.liang.xz.resource.security;

import lombok.Data;

import java.util.List;

/**
 * <p>Token验证配置属性</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
public class TokenValidationProperties {

    /** 是否检查Token过期(二次确认) */
    private boolean checkTokenExpiry = true;

    /** 是否检查用户状态(禁用/黑名单) */
    private boolean checkUserStatus = true;

    /**
     * 是否检查Token黑名单。
     * <p>默认为 true：退出登录/强制下线后，旧 Token 必须在所有服务立即失效，
     * 否则会出现"退出登录后 Token 仍可访问接口"的越权漏洞。
     * 关闭该开关等同于放弃会话终止能力，仅应在明确无需吊销能力的场景使用。</p>
     */
    private boolean checkTokenBlacklist = true;

    /** JWT必要claims字段列表 */
    private List<String> requiredClaims = List.of("sub");
}
