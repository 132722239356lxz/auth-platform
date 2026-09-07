package com.liang.xz.system.service;

import com.liang.xz.system.dto.PageResponse;
import com.liang.xz.system.dto.RolePageQuery;
import com.liang.xz.system.dto.RoleRequest;
import com.liang.xz.system.dto.RoleResponse;

import java.util.List;
import java.util.Optional;

/**
 * <p>角色管理服务接口</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public interface IRoleService {

    /**
     * 查询所有角色
     */
    List<RoleResponse> listAll();

    /**
     * 按ID查询角色
     */
    Optional<RoleResponse> getById(Long id);

    /**
     * 分页查询角色
     */
    PageResponse<RoleResponse> pageList(RolePageQuery query);

    /**
     * 创建角色
     */
    RoleResponse create(RoleRequest request);

    /**
     * 更新角色
     */
    Optional<RoleResponse> update(Long id, RoleRequest request);

    /**
     * 启用角色
     */
    boolean enable(Long id);

    /**
     * 禁用角色
     */
    boolean disable(Long id);

    /**
     * 删除角色
     */
    boolean delete(Long id);

    /**
     * 分配角色菜单权限
     */
    void assignMenus(Long roleId, List<Long> menuIds);
}
