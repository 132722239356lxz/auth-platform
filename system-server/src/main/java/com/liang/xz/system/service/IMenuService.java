package com.liang.xz.system.service;

import com.liang.xz.system.dto.MenuRequest;
import com.liang.xz.system.dto.MenuResponse;
import com.liang.xz.system.dto.MenuTreeQuery;

import java.util.List;
import java.util.Optional;

/**
 * <p>菜单管理服务接口</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public interface IMenuService {

    /**
     * 获取菜单树（支持筛选条件）
     */
    List<MenuResponse> getMenuTree(MenuTreeQuery query);

    /**
     * 获取用户的菜单树
     */
    List<MenuResponse> getUserMenuTree(Long userId, List<String> permissions);

    /**
     * 按ID查询菜单
     */
    Optional<MenuResponse> getById(Long id);

    /**
     * 创建菜单
     */
    MenuResponse create(MenuRequest request);

    /**
     * 更新菜单
     */
    Optional<MenuResponse> update(Long id, MenuRequest request);

    /**
     * 删除菜单
     */
    boolean delete(Long id);
}
