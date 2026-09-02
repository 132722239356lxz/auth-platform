package com.liang.xz.resource.security;

import java.util.Set;

/**
 * <p>用户权限提供者接口 —— 业务模块可自行实现，用于从数据库查询用户权限</p>
 *
 * <p>当JWT中没有permissions claim，且GrantedAuthorities中也没有权限信息时，
 * 作为兜底方案从数据库查询。业务模块实现此接口并注册为Spring Bean即可。</p>
 *
 * <p>实现示例:</p>
 * <pre>
 * {@code @Component}
 * public class MyPermissionProvider implements UserPermissionProvider {
 *     private final JdbcTemplate jdbcTemplate;
 *
 *     {@code @Override}
 *     public Set<String> getPermissions(String username, Long userId) {
 *         // 从数据库查询用户权限
 *         List<String> perms = jdbcTemplate.queryForList(
 *             "SELECT DISTINCT m.permission FROM sys_menu m ... WHERE ur.user_id = ?",
 *             String.class, userId);
 *         return new HashSet<>(perms);
 *     }
 * }
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public interface UserPermissionProvider {

    /**
     * 获取用户的权限标识集合
     *
     * @param username 用户名(JWT subject)
     * @param userId   用户ID(从JWT user_id claim解析)
     * @return 权限标识集合，如 ["system:user:list", "system:role:add"]
     */
    Set<String> getPermissions(String username, Long userId);

    /**
     * 判断用户是否被禁用/黑名单
     *
     * @param username 用户名
     * @param userId   用户ID
     * @return true 表示用户被禁用或处于黑名单中
     */
    default boolean isUserBlocked(String username, Long userId) {
        return false;
    }
}
