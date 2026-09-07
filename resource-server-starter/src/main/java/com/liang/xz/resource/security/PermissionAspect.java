package com.liang.xz.resource.security;

import com.liang.xz.common.core.annotation.PublicApi;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Method;
import java.util.*;

/**
 * <p>权限校验AOP切面 —— 拦截 {@link RequirePermission} 注解进行权限验证</p>
 *
 * <p>权限获取策略:</p>
 * <ul>
 *   <li><b>权限一律从数据库查询</b>(通过 {@link UserPermissionProvider})，
 *       不再从 JWT claims / GrantedAuthorities 中获取(PERM_ 前缀已废弃)。</li>
 *   <li>只有在未配置 {@code UserPermissionProvider} 时，才退化为使用
 *       GrantedAuthorities 中 ROLE_ADMIN 角色判定管理员。</li>
 * </ul>
 *
 * <p>注意: 权限不再通过 JWT 传递，{@code UserPermissionProvider} 是唯一权限来源。</p>
 *
 * <p>管理员判断:</p>
 * <ul>
 *   <li>拥有 "*" 或 "admin" 权限的用户视为管理员，跳过所有权限校验</li>
 *   <li>拥有 ROLE_ADMIN 角色的用户视为管理员</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Order(1)
public class PermissionAspect {

    private final UserPermissionProvider permissionProvider;

    public PermissionAspect(UserPermissionProvider permissionProvider) {
        this(permissionProvider, "jwt");
    }

    /**
     * @param permissionProvider 数据库权限提供者(必填)
     * @param permissionSource   历史遗留参数，已废弃，权限一律从数据库查询，不再使用该配置
     */
    public PermissionAspect(UserPermissionProvider permissionProvider, String permissionSource) {
        this.permissionProvider = permissionProvider;
        if (permissionSource != null && !"database".equalsIgnoreCase(permissionSource)) {
            log.info("[Permission] permissionSource='{}' 已废弃，权限统一从数据库(UserPermissionProvider)查询",
                    permissionSource);
        }
    }

    /**
     * 在标注了 @RequirePermission 的方法执行前校验权限
     */
    @Before("@annotation(com.liang.xz.resource.security.RequirePermission)")
    public void checkPermission(JoinPoint joinPoint) {
        // 0. 如果方法或类标注了 @PublicApi，跳过权限校验
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        if (method.isAnnotationPresent(PublicApi.class)
                || method.getDeclaringClass().isAnnotationPresent(PublicApi.class)) {
            log.debug("[Permission] @PublicApi 接口，跳过权限校验: {}", method.getName());
            return;
        }

        // 1. 获取注解参数
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);

        String[] requiredPermissions = annotation.value();
        RequirePermission.Logical logical = annotation.logical();

        if (requiredPermissions.length == 0) {
            return; // 无权限要求，放行
        }

        // 2. 获取当前用户的权限列表
        Set<String> userPermissions = resolveUserPermissions();

        // 3. 管理员角色拥有所有权限
        if (isAdmin(userPermissions)) {
            log.debug("[Permission] 管理员角色，拥有所有权限");
            return;
        }

        // 4. 权限校验
        boolean hasPermission;
        if (logical == RequirePermission.Logical.AND) {
            // AND逻辑: 必须全部满足
            hasPermission = Arrays.stream(requiredPermissions)
                    .allMatch(userPermissions::contains);
        } else {
            // OR逻辑(默认): 满足任一即可
            hasPermission = Arrays.stream(requiredPermissions)
                    .anyMatch(userPermissions::contains);
        }

        if (!hasPermission) {
            String userName = getCurrentUsername();
            log.warn("[Permission] 权限不足: user={}, required={}, userPerms={}",
                    userName, Arrays.toString(requiredPermissions), userPermissions);
            throw new AccessDeniedException(
                    "权限不足，缺少: " + String.join(", ", requiredPermissions));
        }

        log.debug("[Permission] 权限校验通过: required={}", Arrays.toString(requiredPermissions));
    }

    /**
     * 解析当前用户的权限列表
     * <p><b>权限一律从数据库查询</b>，不再从 JWT claims / GrantedAuthorities 的 PERM_ 前缀提取。</p>
     * <ol>
     *   <li>优先通过 {@link UserPermissionProvider#getPermissions(String, Long)} 从数据库查询用户权限;</li>
     *   <li>若未配置 {@code UserPermissionProvider}，则退化为空集(此时只有管理员可访问接口)。</li>
     * </ol>
     * <p>{@code permissionSource} 配置项已废弃，保留仅为向后兼容，不再影响权限来源。</p>
     */
    private Set<String> resolveUserPermissions() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Collections.emptySet();
        }

        // 权限一律从数据库查询，不依赖 JWT / GrantedAuthorities 中的 PERM_ 前缀
        if (permissionProvider != null) {
            String username = getCurrentUsername();
            Long userId = resolveUserIdFromAuth(authentication);
            if (username != null && !"anonymous".equals(username)) {
                Set<String> dbPerms = permissionProvider.getPermissions(username, userId);
                if (!CollectionUtils.isEmpty(dbPerms)) {
                    log.debug("[Permission] 从数据库获取权限: user={}, userId={}, perms={}",
                            username, userId, dbPerms.size());
                    return dbPerms;
                }
                log.debug("[Permission] 数据库未查询到用户权限: user={}, userId={}", username, userId);
            }
        } else {
            log.warn("[Permission] 未配置 UserPermissionProvider，无法从数据库加载权限，"
                    + "仅有管理员角色可访问受保护接口");
        }

        return Collections.emptySet();
    }

    /**
     * 判断是否为管理员
     */
    private boolean isAdmin(Set<String> userPermissions) {
        // 超级权限
        if (userPermissions.contains("*") || userPermissions.contains("admin")) {
            return true;
        }
        // ROLE_ADMIN 角色
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            boolean hasAdminRole = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(a -> "ROLE_ADMIN".equals(a) || "ROLE_admin".equalsIgnoreCase(a));
            if (hasAdminRole) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取当前用户名
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return authentication.getName();
    }

    /**
     * 从认证信息中解析用户ID
     */
    private Long resolveUserIdFromAuth(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String userIdStr = jwt.getClaimAsString("user_id");
            if (userIdStr != null) {
                try {
                    return Long.valueOf(userIdStr);
                } catch (NumberFormatException e) {
                    log.debug("[Permission] 无法解析user_id: {}", userIdStr);
                }
            }
        }
        return null;
    }
}
