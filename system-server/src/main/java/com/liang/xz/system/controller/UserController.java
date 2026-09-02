package com.liang.xz.system.controller;

import com.liang.xz.system.dto.ApiResponse;
import com.liang.xz.system.dto.BatchAssignRoleRequest;
import com.liang.xz.system.dto.ChangePasswordRequest;
import com.liang.xz.system.dto.CurrentUserResponse;
import com.liang.xz.system.dto.PageResponse;
import com.liang.xz.system.dto.UpdateProfileRequest;
import com.liang.xz.system.dto.UserAssignRoleRequest;
import com.liang.xz.system.dto.UserCreateRequest;
import com.liang.xz.system.dto.UserPageQuery;
import com.liang.xz.system.dto.UserResponse;
import com.liang.xz.system.dto.UserUpdateRequest;
import com.liang.xz.resource.security.RequirePermission;
import com.liang.xz.system.service.ICurrentUserService;
import com.liang.xz.system.service.IUserManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>用户管理接口 —— 用户CRUD、角色分配、当前用户信息</p>
 *
 * <p>注意: 用户注册/登录/SSO由auth-server负责，本模块负责用户管理和权限分配</p>
 * <p>权限说明:</p>
 * <ul>
 *   <li>/current 系列接口: 任何已认证用户均可访问(获取自己的信息)</li>
 *   <li>管理类接口: 需要对应的 system:user:* 权限</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户查询、启用/禁用、角色分配、当前用户信息")
public class UserController {

    private final IUserManageService userManageService;
    private final ICurrentUserService currentUserService;

    // ======================== 当前用户(无需额外权限) ========================

    @GetMapping("/current")
    @Operation(summary = "获取当前登录用户信息", description = "从JWT Token中解析当前用户身份/角色/权限")
    public ApiResponse<CurrentUserResponse> currentUser() {
        return ApiResponse.success(currentUserService.getCurrentUser());
    }

    @GetMapping("/current/menus")
    @Operation(summary = "获取当前用户的菜单树", description = "根据用户角色返回可访问的菜单树(前端动态路由)")
    public ApiResponse<?> currentUserMenus() {
        CurrentUserResponse user = currentUserService.getCurrentUser();
        if (user.getUserId() == null) {
            return ApiResponse.fail(401, "无法获取用户信息");
        }
        var menus = userManageService.getUserMenus(user.getUserId(), user.getPermissions());
        return ApiResponse.success(menus);
    }

    @PutMapping("/current/profile")
    @Operation(summary = "当前用户更新个人资料", description = "普通用户自服务更新昵称/邮箱/手机/头像")
    public ApiResponse<UserResponse> updateCurrentProfile(
            @Parameter(description = "个人资料更新请求") @Valid @RequestBody UpdateProfileRequest request) {
        CurrentUserResponse current = currentUserService.getCurrentUser();
        if (current.getUserId() == null) {
            return ApiResponse.fail(401, "无法获取用户信息");
        }
        UserResponse user = userManageService.updateCurrentProfile(current.getUserId(), request);
        return ApiResponse.success("个人资料更新成功", user);
    }

    @PutMapping("/current/password")
    @Operation(summary = "当前用户修改密码", description = "普通用户自服务修改登录密码")
    public ApiResponse<Void> changeCurrentPassword(
            @Parameter(description = "修改密码请求") @Valid @RequestBody ChangePasswordRequest request) {
        CurrentUserResponse current = currentUserService.getCurrentUser();
        if (current.getUserId() == null) {
            return ApiResponse.fail(401, "无法获取用户信息");
        }
        userManageService.changePassword(current.getUserId(), request.getOldPassword(), request.getNewPassword());
        return ApiResponse.success("密码修改成功", null);
    }

    // ======================== 用户查询(需要列表权限) ========================

    @GetMapping
    @Operation(summary = "查询所有用户")
    @RequirePermission("system:user:list")
    public ApiResponse<List<UserResponse>> list() {
        return ApiResponse.success(userManageService.listAll());
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询用户", description = "支持按用户名/昵称/邮箱模糊搜索、状态、用户类型、部门等多条件筛选")
    @RequirePermission("system:user:list")
    public ApiResponse<PageResponse<UserResponse>> page(@Valid UserPageQuery query) {
        return ApiResponse.success(userManageService.pageList(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "按ID查询用户")
    @RequirePermission("system:user:list")
    public ApiResponse<UserResponse> getById(
            @Parameter(description = "用户ID") @PathVariable Long id) {
        return userManageService.getById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.fail(404, "用户不存在"));
    }

    // ======================== 用户管理(需要编辑/删除权限) ========================

    @PostMapping
    @Operation(summary = "新增用户", description = "管理员创建新用户")
    @RequirePermission("system:user:edit")
    public ApiResponse<UserResponse> create(
            @Parameter(description = "用户创建请求") @Valid @RequestBody UserCreateRequest request) {
        UserResponse user = userManageService.create(request);
        return ApiResponse.success("用户创建成功", user);
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改用户", description = "管理员修改用户信息")
    @RequirePermission("system:user:edit")
    public ApiResponse<UserResponse> update(
            @Parameter(description = "用户ID") @PathVariable Long id,
            @Parameter(description = "用户修改请求") @Valid @RequestBody UserUpdateRequest request) {
        UserResponse user = userManageService.update(id, request);
        return ApiResponse.success("用户修改成功", user);
    }

    @PutMapping("/{id}/enable")
    @Operation(summary = "启用用户")
    @RequirePermission("system:user:edit")
    public ApiResponse<Void> enable(
            @Parameter(description = "用户ID") @PathVariable Long id) {
        boolean ok = userManageService.enable(id);
        return ok ? ApiResponse.success("用户已启用", null)
                : ApiResponse.fail(404, "用户不存在");
    }

    @PutMapping("/{id}/disable")
    @Operation(summary = "禁用用户")
    @RequirePermission("system:user:edit")
    public ApiResponse<Void> disable(
            @Parameter(description = "用户ID") @PathVariable Long id) {
        boolean ok = userManageService.disable(id);
        return ok ? ApiResponse.success("用户已禁用", null)
                : ApiResponse.fail(404, "用户不存在");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户")
    @RequirePermission("system:user:delete")
    public ApiResponse<Void> delete(
            @Parameter(description = "用户ID") @PathVariable Long id) {
        boolean ok = userManageService.delete(id);
        return ok ? ApiResponse.success("用户已删除", null)
                : ApiResponse.fail(404, "用户不存在");
    }

    // ======================== 角色分配(需要编辑权限) ========================

    @GetMapping("/{id}/roles")
    @Operation(summary = "查询用户的角色")
    @RequirePermission("system:user:list")
    public ApiResponse<List<String>> getUserRoles(
            @Parameter(description = "用户ID") @PathVariable Long id) {
        return ApiResponse.success(userManageService.getUserRoles(id));
    }

    @PutMapping("/{id}/roles")
    @Operation(summary = "分配用户角色", description = "重新设置用户的角色列表")
    @RequirePermission("system:user:edit")
    public ApiResponse<Void> assignRoles(
            @Parameter(description = "用户ID") @PathVariable Long id,
            @Parameter(description = "角色ID列表") @Valid @RequestBody UserAssignRoleRequest request) {
        userManageService.assignRoles(id, request.getRoleIds());
        return ApiResponse.success("角色分配成功", null);
    }

    @PutMapping("/batch/roles")
    @Operation(summary = "批量分配角色", description = "将指定角色批量下发给多个用户，支持权限批量下发")
    @RequirePermission("system:user:edit")
    public ApiResponse<Integer> batchAssignRoles(
            @Parameter(description = "批量分配请求") @Valid @RequestBody BatchAssignRoleRequest request) {
        int count = userManageService.batchAssignRoles(request.getUserIds(), request.getRoleIds());
        return ApiResponse.success("批量角色分配成功，共新增 " + count + " 条关联", count);
    }
}
