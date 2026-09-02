package com.liang.xz.system.config;

import com.liang.xz.resource.security.UserPermissionProvider;
import com.liang.xz.system.entity.UserEntity;
import com.liang.xz.system.repository.RoleRepository;
import com.liang.xz.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>系统管理模块的权限提供者实现 —— 从数据库查询用户权限和状态</p>
 *
 * <p>实现 {@link UserPermissionProvider} 接口，供 resource-server-starter 中的
 * {@link com.liang.xz.resource.security.PermissionAspect} 和
 * {@link com.liang.xz.resource.security.TokenValidationFilter} 使用</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemUserPermissionProvider implements UserPermissionProvider {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    @Override
    public Set<String> getPermissions(String username, Long userId) {
        try {
            List<String> permissions;
            if (userId != null) {
                permissions = roleRepository.findPermissionsByUserId(userId);
            } else if (username != null && !"anonymous".equals(username)) {
                // JWT 未携带 user_id claim 时，回退用用户名查询
                log.debug("[PermissionProvider] userId为空，使用用户名回查权限: username={}", username);
                permissions = roleRepository.findPermissionsByUsername(username);
            } else {
                log.debug("[PermissionProvider] userId与username均为空，无法查询权限");
                return Collections.emptySet();
            }
            return new HashSet<>(permissions);
        } catch (Exception e) {
            log.warn("[PermissionProvider] 查询用户权限失败: username={}, userId={}, error={}",
                    username, userId, e.getMessage());
            return Collections.emptySet();
        }
    }

    @Override
    public boolean isUserBlocked(String username, Long userId) {
        if (username == null && userId == null) {
            return false;
        }
        try {
            UserEntity user;
            if (userId != null) {
                user = userRepository.findById(userId).orElse(null);
            } else {
                user = userRepository.findByUsername(username).orElse(null);
            }

            if (user == null) {
                log.warn("[PermissionProvider] 用户不存在: username={}, userId={}", username, userId);
                return true; // 用户不存在，阻止访问
            }

            if (!user.getEnabled()) {
                log.warn("[PermissionProvider] 用户已被禁用: username={}, userId={}", username, userId);
                return true;
            }

            if (!user.getAccountNonLocked()) {
                log.warn("[PermissionProvider] 用户账号已锁定: username={}, userId={}", username, userId);
                return true;
            }

            return false;
        } catch (Exception e) {
            log.warn("[PermissionProvider] 检查用户状态失败: username={}, userId={}, error={}",
                    username, userId, e.getMessage());
            return false; // 查询失败时不阻止(降级)
        }
    }
}
