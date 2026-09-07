package com.liang.xz.resource.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>默认数据库权限提供者 —— 基于模块自身的 {@link JdbcTemplate} 从标准权限表查询用户权限</p>
 *
 * <p>查询链路: sys_user_role → sys_role_menu → sys_menu，
 * 仅返回已启用的菜单且 permission 非空的权限标识。</p>
 *
 * <p>适用场景: 各业务模块库中都存在统一的 sys_menu / sys_role_menu / sys_user_role 权限表时，
 * 引入 resource-server-starter 即可自动获得数据库权限查询能力，
 * 无需在每个模块重复实现 {@link UserPermissionProvider}。</p>
 *
 * <p>若业务模块需要定制(例如按用户名回查、多租户过滤)，可自行实现
 * {@link UserPermissionProvider} 并注册为 Spring Bean，本默认实现会被 {@code @ConditionalOnMissingBean} 覆盖。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
public class DefaultDbUserPermissionProvider implements UserPermissionProvider {

    private final JdbcTemplate jdbcTemplate;

    public DefaultDbUserPermissionProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Set<String> getPermissions(String username, Long userId) {
        if (username == null || "anonymous".equals(username)) {
            return Collections.emptySet();
        }
        try {
            List<String> permissions = queryByUsername(username);
            if (permissions.isEmpty() && userId != null) {
                permissions = queryByUserId(userId);
            }
            return new HashSet<>(permissions);
        } catch (Exception e) {
            log.warn("[DefaultPermissionProvider] 查询用户权限失败: username={}, userId={}, error={}",
                    username, userId, e.getMessage());
            return Collections.emptySet();
        }
    }

    private List<String> queryByUserId(Long userId) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT m.permission FROM sys_menu m "
                        + "INNER JOIN sys_role_menu rm ON m.id = rm.menu_id "
                        + "INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id "
                        + "WHERE ur.user_id = ? AND m.enabled = true "
                        + "AND m.permission IS NOT NULL AND m.permission != ''",
                String.class, userId);
    }

    private List<String> queryByUsername(String username) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT m.permission FROM sys_menu m "
                        + "INNER JOIN sys_role_menu rm ON m.id = rm.menu_id "
                        + "INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id "
                        + "INNER JOIN sys_user u ON ur.user_id = u.id "
                        + "WHERE u.username = ? AND m.enabled = true "
                        + "AND m.permission IS NOT NULL AND m.permission != ''",
                String.class, username);
    }
}
