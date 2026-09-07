package com.liang.xz.system.mapper;

import com.liang.xz.system.entity.MenuEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * <p>菜单表 MyBatis Mapper 接口</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Mapper
public interface MenuMapper {

    /**
     * 查询所有菜单
     */
    List<MenuEntity> findAll();

    /**
     * 按ID查询
     */
    Optional<MenuEntity> findById(@Param("id") Long id);

    /**
     * 查询用户权限标识列表
     */
    List<String> findPermissionsByUserId(@Param("userId") Long userId);

    /**
     * 查询角色关联的菜单ID列表
     */
    List<Long> findMenuIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 查询角色的权限列表
     */
    List<String> findPermissionsByRoleId(@Param("roleId") Long roleId);

    /**
     * 插入菜单
     */
    int insert(MenuEntity menu);

    /**
     * 更新菜单
     */
    int update(MenuEntity menu);

    /**
     * 删除角色的菜单关联
     */
    int deleteRoleMenuByMenuId(@Param("menuId") Long menuId);

    /**
     * 按ID删除
     */
    int deleteById(@Param("id") Long id);
}
