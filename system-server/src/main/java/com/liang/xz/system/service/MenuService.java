package com.liang.xz.system.service;

import com.liang.xz.system.cache.SystemCacheService;
import com.liang.xz.system.dto.MenuRequest;
import com.liang.xz.system.dto.MenuResponse;
import com.liang.xz.system.dto.MenuTreeQuery;
import com.liang.xz.system.entity.MenuEntity;
import com.liang.xz.system.repository.MenuRepository;
import com.liang.xz.system.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>菜单管理服务 —— 菜单树构建、CRUD</p>
 *
 * <p>缓存优化:</p>
 * <ul>
 *   <li>全量菜单列表缓存 1h（菜单变更频率极低）</li>
 *   <li>getMenuTree / getUserMenuTree 均从缓存读取菜单原始数据</li>
 *   <li>CRUD 后自动清除菜单缓存 + 级联清除角色详情缓存</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuService implements IMenuService {

    private final MenuRepository menuRepository;
    private final RoleRepository roleRepository;
    private final SystemCacheService cacheService;

    // ======================== 查询（支持缓存）========================

    /**
     * 获取完整的菜单树(用于管理端菜单管理页面，支持按 keyword/menuType/enabled 过滤)
     */
    public List<MenuResponse> getMenuTree(MenuTreeQuery query) {
        String keyword = query.getKeyword();
        Integer menuType = query.getMenuType();
        Boolean enabled = query.getEnabled();
        List<MenuEntity> allMenus = loadAllMenus();
        List<MenuEntity> filtered = allMenus.stream()
                .filter(m -> enabled == null || m.getEnabled() == enabled)
                .filter(m -> menuType == null || m.getMenuType().equals(menuType))
                .filter(m -> keyword == null || keyword.isBlank()
                        || matchesKeyword(m, keyword))
                .collect(Collectors.toList());
        return buildTree(filtered, 0L);
    }

    private boolean matchesKeyword(MenuEntity m, String keyword) {
        String kw = keyword.toLowerCase();
        return (m.getMenuName() != null && m.getMenuName().toLowerCase().contains(kw))
                || (m.getPermission() != null && m.getPermission().toLowerCase().contains(kw));
    }

    /**
     * 获取当前用户可访问的菜单树(根据用户-角色-菜单关联过滤)
     *
     * <p>逻辑:</p>
     * <ol>
     *   <li>通过 userId 查 sys_user_role 获取用户的所有角色ID</li>
     *   <li>通过 sys_role_menu 获取这些角色分配的所有菜单ID</li>
     *   <li>向上递归包含所有父级目录（保证树结构完整）</li>
     *   <li>只返回 menuType 为目录(0)/菜单(1) 的节点，过滤按钮(2)</li>
     *   <li>特殊用户(userType=admin 或持有 * 权限)返回全量菜单</li>
     * </ol>
     */
    public List<MenuResponse> getUserMenuTree(Long userId, List<String> permissions) {
        // 超级管理员 → 返回全量菜单(按钮除外)
        if (permissions != null && permissions.contains("*")) {
            List<MenuEntity> allMenus = loadAllMenus();
            return buildTree(allMenus.stream()
                    .filter(m -> m.getMenuType() != 2)
                    .collect(Collectors.toList()), 0L);
        }

        // 1. 获取用户所有角色的 ID
        List<Long> roleIds = roleRepository.findRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 收集所有角色分配的菜单 ID（去重）
        Set<Long> assignedMenuIds = new HashSet<>();
        for (Long roleId : roleIds) {
            assignedMenuIds.addAll(roleRepository.findMenuIdsByRoleId(roleId));
        }
        if (assignedMenuIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. 加载全量菜单，构建 id→parentId 映射用于向上查找祖先
        List<MenuEntity> allMenus = loadAllMenus();
        Map<Long, Long> idToParentId = new HashMap<>();
        for (MenuEntity m : allMenus) {
            idToParentId.put(m.getId(), m.getParentId());
        }

        // 4. 向上递归包含所有祖先目录（保证树结构完整）
        Set<Long> menuIdAll = new HashSet<>(assignedMenuIds);
        for (Long menuId : assignedMenuIds) {
            Long parentId = idToParentId.get(menuId);
            while (parentId != null && parentId != 0 && !menuIdAll.contains(parentId)) {
                menuIdAll.add(parentId);
                parentId = idToParentId.get(parentId);
            }
        }

        // 5. 按 menuIdAll 过滤，排除按钮类型(menuType=2)，只保留目录/菜单
        List<MenuEntity> accessibleMenus = allMenus.stream()
                .filter(m -> m.getMenuType() != 2)
                .filter(m -> menuIdAll.contains(m.getId()))
                .collect(Collectors.toList());

        // 如果用户没有分配任何非按钮菜单，检查是否需要回退到权限模式
        if (accessibleMenus.isEmpty() && permissions != null && !permissions.isEmpty()) {
            Set<String> permSet = new HashSet<>(permissions);
            return buildTree(allMenus.stream()
                    .filter(m -> m.getMenuType() != 2)
                    .filter(m -> m.getPermission() != null && permSet.contains(m.getPermission()))
                    .collect(Collectors.toList()), 0L);
        }

        return buildTree(accessibleMenus, 0L);
    }

    public Optional<MenuResponse> getById(Long id) {
        // 从缓存的全量列表中查找（菜单数据量小，全量缓存更高效）
        List<MenuEntity> allMenus = loadAllMenus();
        return allMenus.stream()
                .filter(m -> m.getId().equals(id))
                .findFirst()
                .map(this::toResponse);
    }

    /**
     * 加载全量菜单（缓存优先）
     */
    @SuppressWarnings("unchecked")
    private List<MenuEntity> loadAllMenus() {
        List<MenuEntity> cached = cacheService.getAllMenus();
        if (cached != null) {
            return cached;
        }
        List<MenuEntity> allMenus = menuRepository.findAll();
        cacheService.putAllMenus(allMenus);
        return allMenus;
    }

    // ======================== CRUD（自动清除缓存）========================

    @Transactional
    public MenuResponse create(MenuRequest request) {
        MenuEntity entity = toEntity(request);
        entity.setEnabled(true);
        long id = menuRepository.insert(entity);
        entity.setId(id);
        log.info("[Menu] 菜单创建成功: id={}, name={}", id, entity.getMenuName());

        if (request.getAutoAssignRoleIds() != null && !request.getAutoAssignRoleIds().isEmpty()) {
            for (Long roleId : request.getAutoAssignRoleIds()) {
                menuRepository.insertRoleMenu(roleId, id);
            }
            log.info("[Menu] 菜单 {} 自动分配给角色: {}", id, request.getAutoAssignRoleIds());
        }

        // 菜单变更 → 清除菜单缓存
        cacheService.evictAllMenus();
        return toResponse(entity);
    }

    @Transactional
    public Optional<MenuResponse> update(Long id, MenuRequest request) {
        return menuRepository.findById(id).map(existing -> {
            MenuEntity entity = toEntity(request);
            entity.setId(id);
            menuRepository.update(entity);
            log.info("[Menu] 菜单更新成功: id={}", id);

            // 菜单变更 → 清除菜单缓存
            cacheService.evictAllMenus();
            return toResponse(entity);
        });
    }

    @Transactional
    public boolean enable(Long id) {
        boolean ok = menuRepository.updateEnabled(id, true) > 0;
        if (ok) cacheService.evictAllMenus();
        return ok;
    }

    @Transactional
    public boolean disable(Long id) {
        boolean ok = menuRepository.updateEnabled(id, false) > 0;
        if (ok) cacheService.evictAllMenus();
        return ok;
    }

    @Transactional
    public boolean delete(Long id) {
        // 校验: 存在子菜单时禁止删除
        List<MenuEntity> children = menuRepository.findByParentId(id);
        if (!children.isEmpty()) {
            throw new IllegalArgumentException(
                    "菜单下存在 " + children.size() + " 个子菜单，请先删除子菜单后再操作");
        }
        menuRepository.deleteRoleMenuByMenuId(id);
        int result = menuRepository.deleteById(id);
        if (result > 0) {
            log.info("[Menu] 菜单删除成功: id={}", id);
            cacheService.evictAllMenus();
        }
        return result > 0;
    }

    // ======================== 辅助 ========================

    private List<MenuResponse> buildTree(List<MenuEntity> menus, Long parentId) {
        return menus.stream()
                .filter(m -> m.getParentId().equals(parentId))
                .sorted(Comparator.comparing(MenuEntity::getSortOrder))
                .map(m -> {
                    List<MenuResponse> children = buildTree(menus, m.getId());
                    return MenuResponse.builder()
                            .id(m.getId())
                            .parentId(m.getParentId())
                            .menuName(m.getMenuName())
                            .menuType(m.getMenuType())
                            .path(m.getPath())
                            .component(m.getComponent())
                            .permission(m.getPermission())
                            .icon(m.getIcon())
                            .sortOrder(m.getSortOrder())
                            .enabled(m.getEnabled())
                            .isFrame(m.getIsFrame())
                            .children(children.isEmpty() ? null : children)
                            .createTime(m.getCreateTime())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private MenuEntity toEntity(MenuRequest request) {
        MenuEntity e = new MenuEntity();
        e.setParentId(request.getParentId() != null ? request.getParentId() : 0L);
        e.setMenuName(request.getMenuName());
        e.setMenuType(request.getMenuType());
        e.setPath(request.getPath());
        e.setComponent(request.getComponent());
        e.setPermission(request.getPermission());
        e.setIcon(request.getIcon());
        e.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        e.setIsFrame(request.getIsFrame() != null ? request.getIsFrame() : false);
        return e;
    }

    private MenuResponse toResponse(MenuEntity e) {
        return MenuResponse.builder()
                .id(e.getId())
                .parentId(e.getParentId())
                .menuName(e.getMenuName())
                .menuType(e.getMenuType())
                .path(e.getPath())
                .component(e.getComponent())
                .permission(e.getPermission())
                .icon(e.getIcon())
                .sortOrder(e.getSortOrder())
                .enabled(e.getEnabled())
                .isFrame(e.getIsFrame())
                .createTime(e.getCreateTime())
                .build();
    }
}
