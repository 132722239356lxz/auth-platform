package com.liang.xz.system.service;

import com.liang.xz.system.cache.SystemCacheService;
import com.liang.xz.system.dto.PageResponse;
import com.liang.xz.system.dto.RolePageQuery;
import com.liang.xz.system.dto.RoleRequest;
import com.liang.xz.system.dto.RoleResponse;
import com.liang.xz.system.entity.RoleEntity;
import com.liang.xz.system.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * <p>角色管理服务 —— 角色CRUD、菜单权限分配</p>
 *
 * <p>缓存优化:</p>
 * <ul>
 *   <li>listAll: 优先走 Redis 缓存（30min），未命中走 DB + 回填</li>
 *   <li>getById: 优先走详情缓存，未命中走 DB + 回填</li>
 *   <li>CRUD 后自动清除相关缓存</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService implements IRoleService {

    private final RoleRepository roleRepository;
    private final SystemCacheService cacheService;

    // ======================== 查询（支持缓存）========================

    @SuppressWarnings("unchecked")
    public List<RoleResponse> listAll() {
        List<RoleResponse> cached = cacheService.getAllRoles();
        if (cached != null) {
            return cached;
        }
        List<RoleResponse> list = roleRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        cacheService.putAllRoles(list);
        return list;
    }

    /**
     * 分页查询角色，支持多条件动态筛选
     */
    public PageResponse<RoleResponse> pageList(RolePageQuery query) {
        long total = roleRepository.countByKeyword(query.getKeyword(), query.getEnabled());
        if (total == 0) {
            return PageResponse.empty(query.getPage(), query.getPageSize());
        }
        int offset = (query.getPage() - 1) * query.getPageSize();
        List<RoleResponse> records = roleRepository.findPageByKeyword(
                query.getKeyword(), query.getEnabled(), offset, query.getPageSize()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return PageResponse.of(records, total, query.getPage(), query.getPageSize());
    }

    public Optional<RoleResponse> getById(Long id) {
        RoleResponse cached = cacheService.getRoleDetail(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<RoleResponse> result = roleRepository.findById(id).map(this::toResponse);
        result.ifPresent(r -> cacheService.putRoleDetail(id, r));
        return result;
    }

    // ======================== CRUD（自动清除缓存）========================

    @Transactional
    public RoleResponse create(RoleRequest request) {
        if (roleRepository.findByRoleCode(request.getRoleCode()).isPresent()) {
            throw new IllegalArgumentException("角色编码 [" + request.getRoleCode() + "] 已存在");
        }

        RoleEntity entity = RoleEntity.builder()
                .roleCode(request.getRoleCode())
                .roleName(request.getRoleName())
                .description(request.getDescription())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .enabled(true)
                .build();

        long id = roleRepository.insert(entity);
        entity.setId(id);

        if (request.getMenuIds() != null && !request.getMenuIds().isEmpty()) {
            assignMenus(id, request.getMenuIds());
        }

        // 新建角色，清除角色列表缓存即可
        cacheService.evictAllRoles();
        log.info("[Role] 角色创建成功: id={}, code={}", id, entity.getRoleCode());
        return toResponse(entity);
    }

    @Transactional
    public Optional<RoleResponse> update(Long id, RoleRequest request) {
        return roleRepository.findById(id).map(existing -> {
            RoleEntity entity = RoleEntity.builder()
                    .id(id)
                    .roleCode(request.getRoleCode())
                    .roleName(request.getRoleName())
                    .description(request.getDescription())
                    .sortOrder(request.getSortOrder())
                    .enabled(existing.getEnabled())
                    .build();
            roleRepository.update(entity);

            if (request.getMenuIds() != null) {
                assignMenus(id, request.getMenuIds());
            }

            // 角色变化 → 清除角色缓存 + 级联清除所有关联用户的权限缓存
            cacheService.evictRoleCache(id);
            List<Long> affectedUserIds = roleRepository.findUserIdsByRoleId(id);
            cacheService.evictUserAuthCacheByRole(id, affectedUserIds);
            log.info("[Role] 角色更新成功: id={}", id);
            return toResponse(entity);
        });
    }

    @Transactional
    public boolean enable(Long id) {
        boolean ok = roleRepository.updateEnabled(id, true) > 0;
        if (ok) cacheService.evictRoleCache(id);
        return ok;
    }

    @Transactional
    public boolean disable(Long id) {
        boolean ok = roleRepository.updateEnabled(id, false) > 0;
        if (ok) {
            cacheService.evictRoleCache(id);
            // 禁用角色 → 清除关联用户的权限缓存
            List<Long> affectedUserIds = roleRepository.findUserIdsByRoleId(id);
            cacheService.evictUserAuthCacheByRole(id, affectedUserIds);
        }
        return ok;
    }

    @Transactional
    public boolean delete(Long id) {
        // 校验: 角色下存在关联用户时禁止删除
        List<Long> affectedUserIds = roleRepository.findUserIdsByRoleId(id);
        if (!affectedUserIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "角色下存在 " + affectedUserIds.size() + " 个关联用户，请先解除用户角色关联后再删除");
        }
        roleRepository.deleteRoleMenus(id);
        int result = roleRepository.deleteById(id);
        if (result > 0) {
            cacheService.evictRoleCache(id);
            cacheService.evictUserAuthCacheByRole(id, affectedUserIds);
            log.info("[Role] 角色删除成功: id={}", id);
        }
        return result > 0;
    }

    // ======================== 菜单权限分配 ========================

    /**
     * 为角色分配菜单权限(先清空再分配)，并清除关联缓存
     */
    public void assignMenus(Long roleId, List<Long> menuIds) {
        roleRepository.deleteRoleMenus(roleId);
        for (Long menuId : menuIds) {
            roleRepository.insertRoleMenu(roleId, menuId);
        }
        // 角色-菜单关系变化 → 清除角色详情 + 关联用户权限缓存
        cacheService.evictRoleDetail(roleId);
        List<Long> affectedUserIds = roleRepository.findUserIdsByRoleId(roleId);
        cacheService.evictUserAuthCacheByRole(roleId, affectedUserIds);
        log.info("[Role] 角色 {} 分配菜单: {}", roleId, menuIds);
    }

    // ======================== 辅助 ========================

    private RoleResponse toResponse(RoleEntity e) {
        List<Long> menuIds = roleRepository.findMenuIdsByRoleId(e.getId());
        return RoleResponse.builder()
                .id(e.getId())
                .roleCode(e.getRoleCode())
                .roleName(e.getRoleName())
                .description(e.getDescription())
                .sortOrder(e.getSortOrder())
                .enabled(e.getEnabled())
                .menuIds(menuIds)
                .createTime(e.getCreateTime())
                .updateTime(e.getUpdateTime())
                .build();
    }
}
