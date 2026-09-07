package com.liang.xz.system.service;

import com.liang.xz.common.core.token.CustomTokenClaims;
import com.liang.xz.system.cache.SystemCacheService;
import com.liang.xz.system.dto.CurrentUserResponse;
import com.liang.xz.system.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * <p>当前用户信息服务 —— 从JWT Token中解析用户身份和权限</p>
 *
 * <p>获取用户信息优先级:</p>
 * <ol>
 *   <li>从 SecurityContext 获取 JWT Authentication</li>
 *   <li>从 JWT Claims 提取 sub(用户名)、tenant_id、user_type</li>
 *   <li>查缓存获取 userId → 角色/权限（未命中走 DB 并回填缓存）</li>
 * </ol>
 *
 * <p>性能优化: 角色/权限查询优先走 Redis 缓存，避免每请求三表 JOIN</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrentUserService implements ICurrentUserService {

    private final RoleRepository roleRepository;
    private final com.liang.xz.system.repository.UserRepository userRepository;
    private final SystemCacheService cacheService;
    private final DeptService deptService;

    /**
     * 获取当前登录用户完整信息(用户名/角色/权限)
     * 优化: 角色和权限优先从 Redis 缓存获取，未命中才走 DB
     */
    public CurrentUserResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            log.warn("[CurrentUser] 未获取到JWT认证信息");
            return CurrentUserResponse.builder()
                    .username("anonymous")
                    .roles(Collections.emptyList())
                    .permissions(Collections.emptyList())
                    .build();
        }

        String username = jwt.getSubject();
        String tenantId = jwt.getClaimAsString(CustomTokenClaims.CLAIM_TENANT_ID);
        String userType = jwt.getClaimAsString(CustomTokenClaims.CLAIM_USER_TYPE);

        // 主查 username，兜底查 phone（兼容旧 token 以手机号作为 sub 的场景）
        com.liang.xz.system.entity.UserEntity userEntity =
                userRepository.findByUsername(username).orElse(null);
        if (userEntity == null) {
            userEntity = userRepository.findByPhone(username).orElse(null);
        }
        Long userId = userEntity != null ? userEntity.getId() : null;
        Long deptId = userEntity != null ? userEntity.getDeptId() : null;

        List<String> roles = Collections.emptyList();
        List<String> permissions = Collections.emptyList();

        if (userId != null) {
            // 1. 查缓存
            roles = cacheService.getUserRoles(userId);
            permissions = cacheService.getUserPermissions(userId);

            // 2. 缓存未命中 → 走 DB 并回填
            if (roles == null) {
                roles = roleRepository.findRoleCodesByUserId(userId);
                cacheService.putUserRoles(userId, roles);
            }
            if (permissions == null) {
                permissions = roleRepository.findPermissionsByUserId(userId);
                cacheService.putUserPermissions(userId, permissions);
            }
        }

        log.debug("[CurrentUser] 当前用户: username={}, userId={}, roles={}, permissions={}",
                username, userId, roles.size(), permissions.size());

        return CurrentUserResponse.builder()
                .username(username)
                .nickname(username)
                .userId(userId)
                .deptId(deptId)
                .deptName(deptService.getDeptNameById(deptId))
                .tenantId(tenantId != null ? tenantId : "default")
                .userType(userType != null ? userType : "user")
                .roles(roles)
                .permissions(permissions)
                .phone(userEntity != null ? userEntity.getPhone() : null)
                .email(userEntity != null ? userEntity.getEmail() : null)
                .build();
    }
}
