package com.liang.xz.system.cache;

import com.liang.xz.common.core.redis.RedisHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>system-server 业务缓存服务</p>
 *
 * <p>缓存策略说明:</p>
 * <pre>
 * | 缓存 Key                     | TTL    | 命中场景                        | 失效时机                    |
 * |------------------------------|--------|--------------------------------|---------------------------|
 * | system:user:roles:{userId}   | 10min  | 每请求 currentUser / 用户列表    | 用户-角色变更 / 角色禁用     |
 * | system:user:perms:{userId}   | 10min  | 每请求 currentUser              | 角色-菜单变更 / 用户-角色变更 |
 * | system:role:all              | 30min  | 角色列表查询                    | 角色 CRUD                  |
 * | system:role:detail:{id}      | 30min  | 角色详情查询                    | 角色更新/角色-菜单变更       |
 * | system:menu:all              | 1h     | 菜单树构建                      | 菜单 CRUD                  |
 * | system:user:detail:{id}      | 5min   | 用户详情查询                    | 用户信息变更                |
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemCacheService {

    private final RedisHelper redisHelper;

    // ==================== Key 前缀常量 ====================

    private static final String PREFIX_USER_ROLES      = "system:user:roles";
    private static final String PREFIX_USER_PERMISSIONS = "system:user:perms";
    private static final String PREFIX_ROLE_ALL         = "system:role:all";
    private static final String PREFIX_ROLE_DETAIL      = "system:role:detail";
    private static final String PREFIX_MENU_ALL         = "system:menu:all";
    private static final String PREFIX_USER_DETAIL      = "system:user:detail";

    // ==================== TTL 常量 ====================

    private static final long TTL_USER_ROLES      = 10;
    private static final long TTL_USER_PERMS      = 10;
    private static final long TTL_ROLE_ALL        = 30;
    private static final long TTL_ROLE_DETAIL     = 30;
    private static final long TTL_MENU_ALL        = 60;
    private static final long TTL_USER_DETAIL     = 5;

    // ==================== 用户角色缓存 ====================

    /**
     * 获取用户角色码列表（缓存优先）
     */
    @SuppressWarnings("unchecked")
    public List<String> getUserRoles(Long userId) {
        return redisHelper.get(buildKey(PREFIX_USER_ROLES, userId));
    }

    /**
     * 缓存用户角色码
     */
    public void putUserRoles(Long userId, List<String> roles) {
        redisHelper.set(buildKey(PREFIX_USER_ROLES, userId), roles, TTL_USER_ROLES, TimeUnit.MINUTES);
    }

    /**
     * 清除单个用户的角色缓存
     */
    public void evictUserRoles(Long userId) {
        redisHelper.delete(buildKey(PREFIX_USER_ROLES, userId));
    }

    // ==================== 用户权限缓存 ====================

    /**
     * 获取用户权限列表（缓存优先）
     */
    @SuppressWarnings("unchecked")
    public List<String> getUserPermissions(Long userId) {
        return redisHelper.get(buildKey(PREFIX_USER_PERMISSIONS, userId));
    }

    /**
     * 缓存用户权限
     */
    public void putUserPermissions(Long userId, List<String> permissions) {
        redisHelper.set(buildKey(PREFIX_USER_PERMISSIONS, userId), permissions, TTL_USER_PERMS, TimeUnit.MINUTES);
    }

    /**
     * 清除单个用户的权限缓存
     */
    public void evictUserPermissions(Long userId) {
        redisHelper.delete(buildKey(PREFIX_USER_PERMISSIONS, userId));
    }

    /**
     * 清除用户的角色+权限缓存（用户角色变更时调用）
     */
    public void evictUserAuthCache(Long userId) {
        evictUserRoles(userId);
        evictUserPermissions(userId);
    }

    /**
     * 角色-菜单关系变更时，清除所有关联用户的权限缓存
     */
    public void evictUserAuthCacheByRole(Long roleId, List<Long> userIds) {
        userIds.forEach(this::evictUserAuthCache);
        log.debug("[Cache] 清除角色 {} 关联的 {} 个用户权限缓存", roleId, userIds.size());
    }

    // ==================== 角色列表缓存 ====================

    @SuppressWarnings("unchecked")
    public <T> T getAllRoles() {
        return redisHelper.get(PREFIX_ROLE_ALL);
    }

    public void putAllRoles(Object roleList) {
        redisHelper.set(PREFIX_ROLE_ALL, roleList, TTL_ROLE_ALL, TimeUnit.MINUTES);
    }

    public void evictAllRoles() {
        redisHelper.delete(PREFIX_ROLE_ALL);
    }

    // ==================== 角色详情缓存 ====================

    @SuppressWarnings("unchecked")
    public <T> T getRoleDetail(Long roleId) {
        return redisHelper.get(buildKey(PREFIX_ROLE_DETAIL, roleId));
    }

    public void putRoleDetail(Long roleId, Object role) {
        redisHelper.set(buildKey(PREFIX_ROLE_DETAIL, roleId), role, TTL_ROLE_DETAIL, TimeUnit.MINUTES);
    }

    public void evictRoleDetail(Long roleId) {
        redisHelper.delete(buildKey(PREFIX_ROLE_DETAIL, roleId));
    }

    /**
     * 角色变更时清除所有角色相关缓存
     */
    public void evictRoleCache(Long roleId) {
        evictAllRoles();
        evictRoleDetail(roleId);
    }

    // ==================== 菜单缓存 ====================

    @SuppressWarnings("unchecked")
    public <T> T getAllMenus() {
        return redisHelper.get(PREFIX_MENU_ALL);
    }

    public void putAllMenus(Object menuList) {
        redisHelper.set(PREFIX_MENU_ALL, menuList, TTL_MENU_ALL, TimeUnit.MINUTES);
    }

    public void evictAllMenus() {
        redisHelper.delete(PREFIX_MENU_ALL);
    }

    // ==================== 用户详情缓存 ====================

    @SuppressWarnings("unchecked")
    public <T> T getUserDetail(Long userId) {
        return redisHelper.get(buildKey(PREFIX_USER_DETAIL, userId));
    }

    public void putUserDetail(Long userId, Object user) {
        redisHelper.set(buildKey(PREFIX_USER_DETAIL, userId), user, TTL_USER_DETAIL, TimeUnit.MINUTES);
    }

    public void evictUserDetail(Long userId) {
        redisHelper.delete(buildKey(PREFIX_USER_DETAIL, userId));
    }

    // ==================== 工具方法 ====================

    private String buildKey(String prefix, Object id) {
        return prefix + ":" + id;
    }
}
