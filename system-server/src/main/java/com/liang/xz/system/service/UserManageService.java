package com.liang.xz.system.service;

import com.liang.xz.system.cache.SystemCacheService;
import com.liang.xz.system.dto.MenuResponse;
import com.liang.xz.system.dto.PageResponse;
import com.liang.xz.system.dto.UserCreateRequest;
import com.liang.xz.system.dto.UserPageQuery;
import com.liang.xz.system.dto.UserResponse;
import com.liang.xz.system.dto.UserUpdateRequest;
import com.liang.xz.system.entity.UserEntity;
import com.liang.xz.system.repository.RoleRepository;
import com.liang.xz.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * <p>用户管理服务 —— 用户CRUD、角色分配、菜单权限查询</p>
 *
 * <p>缓存优化:</p>
 * <ul>
 *   <li>getById: 用户详情缓存 5min</li>
 *   <li>toResponse: 角色码/部门名称优先从缓存获取，避免 N+1</li>
 *   <li>assignRoles / enable / disable / delete 后清除关联缓存</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserManageService implements IUserManageService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final MenuService menuService;
    private final SystemCacheService cacheService;
    private final DeptService deptService;
    private final PasswordEncoder passwordEncoder;
    private final UserChangeEventPublisher userChangeEventPublisher;

    // ======================== 查询 ========================

    public List<UserResponse> listAll() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public Optional<UserResponse> getById(Long id) {
        // 1. 查用户详情缓存
        UserResponse cached = cacheService.getUserDetail(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        // 2. 未命中走 DB
        Optional<UserResponse> result = userRepository.findById(id).map(this::toResponse);
        result.ifPresent(r -> cacheService.putUserDetail(id, r));
        return result;
    }

    public List<String> getUserRoles(Long userId) {
        // 优先缓存
        List<String> cached = cacheService.getUserRoles(userId);
        if (cached != null) {
            return cached;
        }
        List<String> roles = roleRepository.findRoleCodesByUserId(userId);
        cacheService.putUserRoles(userId, roles);
        return roles;
    }

    /**
     * 获取用户可访问的菜单树
     */
    public List<MenuResponse> getUserMenus(Long userId, List<String> permissions) {
        return menuService.getUserMenuTree(userId, permissions);
    }

    // ======================== 管理（自动清除缓存）========================

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("用户名 [" + request.getUsername() + "] 已存在");
        }

        UserEntity user = UserEntity.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname() != null ? request.getNickname() : request.getUsername())
                .email(request.getEmail())
                .phone(request.getPhone())
                .userType(request.getUserType() != null ? request.getUserType() : "user")
                .tenantId(request.getTenantId() != null ? request.getTenantId() : "default")
                .deptId(request.getDeptId())
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();

        userRepository.insert(user);
        log.info("[User] 用户创建成功: username={}", user.getUsername());

        // 初始化角色: 优先使用请求中的 roleIds，否则自动分配默认角色
        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            assignRoles(user.getId(), request.getRoleIds());
        } else {
            assignDefaultRole(user.getId());
        }

        // 广播用户创建事件到子系统
        userChangeEventPublisher.publishUserCreated(user);

        return toResponse(user);
    }

    /**
     * 为用户自动分配默认角色（编码 DEFAULT 或 ROLE_DEFAULT）
     */
    private void assignDefaultRole(Long userId) {
        roleRepository.findByRoleCode("ROLE_DEFAULT")
                .or(() -> roleRepository.findByRoleCode("DEFAULT"))
                .ifPresent(defaultRole -> {
                    roleRepository.insertUserRole(userId, defaultRole.getId());
                    cacheService.evictUserAuthCache(userId);
                    log.info("[User] 用户 {} 自动分配默认角色: {}", userId, defaultRole.getRoleCode());
                });
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: id=" + id));

        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            // 检查新用户名是否已被占用
            userRepository.findByUsername(request.getUsername())
                    .ifPresent(u -> {
                        if (!u.getId().equals(id)) {
                            throw new IllegalArgumentException("用户名 [" + request.getUsername() + "] 已被占用");
                        }
                    });
            user.setUsername(request.getUsername());
        }
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getUserType() != null) {
            user.setUserType(request.getUserType());
        }
        if (request.getDeptId() != null) {
            user.setDeptId(request.getDeptId());
        }

        userRepository.update(user);
        cacheService.evictUserDetail(id);
        log.info("[User] 用户更新成功: id={}, username={}", id, user.getUsername());

        // 广播用户更新事件到子系统
        userChangeEventPublisher.publishUserUpdated(user);

        return toResponse(user);
    }

    // ======================== 当前用户自服务 ========================

    /**
     * 当前用户更新个人资料(昵称/邮箱/手机/头像)
     */
    @Transactional
    public UserResponse updateCurrentProfile(Long userId, com.liang.xz.system.dto.UpdateProfileRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: id=" + userId));

        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }

        userRepository.update(user);
        cacheService.evictUserDetail(userId);
        cacheService.evictUserAuthCache(userId);
        log.info("[User] 用户更新个人资料: id={}", userId);
        return toResponse(user);
    }

    /**
     * 当前用户修改密码
     */
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: id=" + userId));

        if (oldPassword == null || oldPassword.isBlank()) {
            throw new IllegalArgumentException("原密码不能为空");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("新密码长度不能少于6位");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("原密码错误");
        }

        userRepository.updatePassword(userId, passwordEncoder.encode(newPassword));
        cacheService.evictUserAuthCache(userId);
        log.info("[User] 用户修改密码: id={}", userId);
    }

    @Transactional
    public boolean enable(Long id) {
        boolean ok = userRepository.updateEnabled(id, true) > 0;
        if (ok) {
            cacheService.evictUserDetail(id);
            // 广播用户状态变更事件到子系统
            userRepository.findById(id).ifPresent(userChangeEventPublisher::publishUserStatusChanged);
        }
        return ok;
    }

    @Transactional
    public boolean disable(Long id) {
        boolean ok = userRepository.updateEnabled(id, false) > 0;
        if (ok) {
            cacheService.evictUserDetail(id);
            // 禁用的用户应清除其权限缓存
            cacheService.evictUserAuthCache(id);
            // 广播用户状态变更事件到子系统
            userRepository.findById(id).ifPresent(userChangeEventPublisher::publishUserStatusChanged);
        }
        return ok;
    }

    @Transactional
    public boolean delete(Long id) {
        // 查询用户信息(用于事件广播)
        UserEntity user = userRepository.findById(id).orElse(null);

        roleRepository.deleteUserRoles(id);
        int result = userRepository.deleteById(id);
        if (result > 0) {
            cacheService.evictUserDetail(id);
            cacheService.evictUserAuthCache(id);
            log.info("[User] 用户删除成功: id={}", id);

            // 广播用户删除事件到子系统
            if (user != null) {
                userChangeEventPublisher.publishUserDeleted(user);
            }
        }
        return result > 0;
    }

    // ======================== 角色分配 ========================

    /**
     * 分页查询用户列表(支持多条件动态筛选)
     */
    public PageResponse<UserResponse> pageList(UserPageQuery query) {
        long total = userRepository.countByQuery(query);
        if (total == 0) {
            return PageResponse.empty(query.getPage(), query.getPageSize());
        }
        List<UserResponse> records = userRepository.findPageByQuery(query).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return PageResponse.of(records, total, query.getPage(), query.getPageSize());
    }

    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        roleRepository.deleteUserRoles(userId);
        for (Long roleId : roleIds) {
            roleRepository.insertUserRole(userId, roleId);
        }
        // 用户-角色关系变更 → 清除该用户的角色+权限缓存
        cacheService.evictUserAuthCache(userId);
        cacheService.evictUserDetail(userId);
        log.info("[User] 用户 {} 分配角色: {}", userId, roleIds);
    }

    /**
     * 批量分配角色: 将指定角色批量下发给多个用户
     */
    @Transactional
    public int batchAssignRoles(List<Long> userIds, List<Long> roleIds) {
        int count = 0;
        for (Long userId : userIds) {
            for (Long roleId : roleIds) {
                // 避免重复插入
                List<Long> existing = roleRepository.findRoleIdsByUserId(userId);
                if (!existing.contains(roleId)) {
                    roleRepository.insertUserRole(userId, roleId);
                    count++;
                }
            }
            cacheService.evictUserAuthCache(userId);
            cacheService.evictUserDetail(userId);
        }
        log.info("[User] 批量分配角色: {} 个用户, 角色ID {}, 共新增 {} 条关联",
                userIds.size(), roleIds, count);
        return count;
    }

    // ======================== 辅助 ========================

    private UserResponse toResponse(com.liang.xz.system.entity.UserEntity e) {
        // 角色码优先从缓存获取
        List<String> roleCodes = cacheService.getUserRoles(e.getId());
        if (roleCodes == null) {
            roleCodes = roleRepository.findRoleCodesByUserId(e.getId());
            cacheService.putUserRoles(e.getId(), roleCodes);
        }
        return UserResponse.builder()
                .id(e.getId())
                .username(e.getUsername())
                .nickname(e.getNickname())
                .email(e.getEmail())
                .phone(e.getPhone())
                .userType(e.getUserType())
                .tenantId(e.getTenantId())
                .deptId(e.getDeptId())
                .deptName(deptService.getDeptNameById(e.getDeptId()))
                .enabled(e.getEnabled())
                .roleCodes(roleCodes)
                .createTime(e.getCreateTime())
                .lastLoginTime(e.getLastLoginTime())
                .lastLoginIp(e.getLastLoginIp())
                .avatar(e.getAvatar())
                .build();
    }
}
