package com.liang.xz.system.service;

import com.liang.xz.system.dto.MenuResponse;
import com.liang.xz.system.dto.PageResponse;
import com.liang.xz.system.dto.UpdateProfileRequest;
import com.liang.xz.system.dto.UserCreateRequest;
import com.liang.xz.system.dto.UserPageQuery;
import com.liang.xz.system.dto.UserResponse;
import com.liang.xz.system.dto.UserUpdateRequest;

import java.util.List;
import java.util.Optional;

/**
 * <p>用户管理服务接口</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public interface IUserManageService {

    /**
     * 查询所有用户
     */
    List<UserResponse> listAll();

    /**
     * 按ID查询用户
     */
    Optional<UserResponse> getById(Long id);

    /**
     * 获取用户角色列表
     */
    List<String> getUserRoles(Long userId);

    /**
     * 获取用户可访问的菜单树
     */
    List<MenuResponse> getUserMenus(Long userId, List<String> permissions);

    /**
     * 分页查询用户列表
     */
    PageResponse<UserResponse> pageList(UserPageQuery query);

    /**
     * 创建用户
     */
    UserResponse create(UserCreateRequest request);

    /**
     * 更新用户
     */
    UserResponse update(Long id, UserUpdateRequest request);

    /**
     * 更新当前用户个人资料
     */
    UserResponse updateCurrentProfile(Long userId, UpdateProfileRequest request);

    /**
     * 修改密码
     */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /**
     * 启用用户
     */
    boolean enable(Long id);

    /**
     * 禁用用户
     */
    boolean disable(Long id);

    /**
     * 删除用户
     */
    boolean delete(Long id);

    /**
     * 分配用户角色
     */
    void assignRoles(Long userId, List<Long> roleIds);

    /**
     * 批量分配角色
     */
    int batchAssignRoles(List<Long> userIds, List<Long> roleIds);
}
