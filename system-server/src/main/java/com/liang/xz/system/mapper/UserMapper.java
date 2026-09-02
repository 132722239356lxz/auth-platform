package com.liang.xz.system.mapper;

import com.liang.xz.system.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * <p>用户表 MyBatis Mapper 接口</p>
 * <p>面向接口编程: Controller → IUserManageService → UserMapper(接口)</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Mapper
public interface UserMapper {

    /**
     * 查询所有用户
     */
    List<UserEntity> findAll();

    /**
     * 按ID查询
     */
    Optional<UserEntity> findById(@Param("id") Long id);

    /**
     * 按用户名查询
     */
    Optional<UserEntity> findByUsername(@Param("username") String username);

    /**
     * 分页条件查询
     */
    List<UserEntity> findByQuery(@Param("keyword") String keyword,
                                 @Param("enabled") Boolean enabled,
                                 @Param("userType") String userType,
                                 @Param("deptId") Long deptId,
                                 @Param("roleId") Long roleId,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    /**
     * 条件统计
     */
    long countByQuery(@Param("keyword") String keyword,
                      @Param("enabled") Boolean enabled,
                      @Param("userType") String userType,
                      @Param("deptId") Long deptId,
                      @Param("roleId") Long roleId);

    /**
     * 插入用户
     */
    int insert(UserEntity user);

    /**
     * 更新用户
     */
    int update(UserEntity user);

    /**
     * 更新密码
     */
    int updatePassword(@Param("id") Long id, @Param("password") String password);

    /**
     * 更新启用状态
     */
    int updateEnabled(@Param("id") Long id, @Param("enabled") Boolean enabled);

    /**
     * 按ID删除
     */
    int deleteById(@Param("id") Long id);
}
