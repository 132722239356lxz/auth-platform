package com.liang.xz.system.mapper;

import com.liang.xz.system.entity.RoleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * <p>角色表 MyBatis Mapper 接口</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Mapper
public interface RoleMapper {

    /**
     * 查询所有角色
     */
    List<RoleEntity> findAll();

    /**
     * 按ID查询
     */
    Optional<RoleEntity> findById(@Param("id") Long id);

    /**
     * 按角色编码查询
     */
    Optional<RoleEntity> findByRoleCode(@Param("roleCode") String roleCode);

    /**
     * 分页条件查询
     */
    List<RoleEntity> findByQuery(@Param("keyword") String keyword,
                                 @Param("enabled") Boolean enabled,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    /**
     * 条件统计
     */
    long countByQuery(@Param("keyword") String keyword,
                      @Param("enabled") Boolean enabled);

    /**
     * 查询用户的角色编码列表
     */
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * 查询用户的角色ID列表
     */
    List<Long> findRoleIdsByUserId(@Param("userId") Long userId);

    /**
     * 插入角色
     */
    int insert(RoleEntity role);

    /**
     * 更新角色
     */
    int update(RoleEntity role);

    /**
     * 插入用户角色关联
     */
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    /**
     * 插入角色菜单关联
     */
    int insertRoleMenu(@Param("roleId") Long roleId, @Param("menuId") Long menuId);

    /**
     * 删除角色的所有菜单关联
     */
    int deleteRoleMenus(@Param("roleId") Long roleId);

    /**
     * 删除用户的所有角色关联
     */
    int deleteUserRoles(@Param("userId") Long userId);

    /**
     * 按ID删除
     */
    int deleteById(@Param("id") Long id);
}
