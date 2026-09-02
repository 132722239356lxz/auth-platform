package com.liang.xz.system.controller;

import com.liang.xz.system.dto.ApiResponse;
import com.liang.xz.system.dto.PageResponse;
import com.liang.xz.system.dto.RolePageQuery;
import com.liang.xz.system.dto.RoleRequest;
import com.liang.xz.system.dto.RoleResponse;
import com.liang.xz.resource.security.RequirePermission;
import com.liang.xz.system.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * <p>角色管理接口</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@Tag(name = "角色管理", description = "角色的分页多条件查询、创建、编辑、启用/禁用、删除及菜单权限分配")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @Operation(summary = "查询所有角色", description = "返回所有角色列表(含关联的菜单ID)")
    @RequirePermission("system:role:list")
    public ApiResponse<List<RoleResponse>> list() {
        return ApiResponse.success(roleService.listAll());
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询角色", description = "支持按关键词(编码/名称)和启用状态多条件筛选")
    @RequirePermission("system:role:list")
    public ApiResponse<PageResponse<RoleResponse>> page(@Valid RolePageQuery query) {
        return ApiResponse.success(roleService.pageList(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "按ID查询角色")
    @RequirePermission("system:role:list")
    public ApiResponse<RoleResponse> getById(
            @Parameter(description = "角色ID") @PathVariable Long id) {
        return roleService.getById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.fail(404, "角色不存在"));
    }

    @PostMapping
    @Operation(summary = "创建新角色", description = "创建角色并分配菜单权限")
    @RequirePermission("system:role:add")
    public ApiResponse<RoleResponse> create(
            @Parameter(description = "角色信息") @Valid @RequestBody RoleRequest request) {
        try {
            return ApiResponse.success("角色创建成功", roleService.create(request));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新角色", description = "更新角色信息并重新分配菜单权限")
    @RequirePermission("system:role:edit")
    public ApiResponse<RoleResponse> update(
            @Parameter(description = "角色ID") @PathVariable Long id,
            @Parameter(description = "角色信息") @Valid @RequestBody RoleRequest request) {
        return roleService.update(id, request)
                .map(r -> ApiResponse.success("角色更新成功", r))
                .orElse(ApiResponse.fail(404, "角色不存在"));
    }

    @PutMapping("/{id}/enable")
    @Operation(summary = "启用角色")
    @RequirePermission("system:role:edit")
    public ApiResponse<Void> enable(
            @Parameter(description = "角色ID") @PathVariable Long id) {
        boolean ok = roleService.enable(id);
        return ok ? ApiResponse.success("角色已启用", null)
                : ApiResponse.fail(404, "角色不存在");
    }

    @PutMapping("/{id}/disable")
    @Operation(summary = "禁用角色")
    @RequirePermission("system:role:edit")
    public ApiResponse<Void> disable(
            @Parameter(description = "角色ID") @PathVariable Long id) {
        boolean ok = roleService.disable(id);
        return ok ? ApiResponse.success("角色已禁用", null)
                : ApiResponse.fail(404, "角色不存在");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除角色", description = "删除前校验关联用户，存在关联用户禁止删除")
    @RequirePermission("system:role:delete")
    public ApiResponse<Void> delete(
            @Parameter(description = "角色ID") @PathVariable Long id) {
        try {
            boolean ok = roleService.delete(id);
            return ok ? ApiResponse.success("角色已删除", null)
                    : ApiResponse.fail(404, "角色不存在");
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @PutMapping("/{id}/menus")
    @Operation(summary = "分配角色菜单权限", description = "重新设置角色关联的菜单/按钮权限")
    @RequirePermission("system:role:edit")
    public ApiResponse<Void> assignMenus(
            @Parameter(description = "角色ID") @PathVariable Long id,
            @Parameter(description = "菜单ID列表") @RequestBody Map<String, List<Long>> body) {
        List<Long> menuIds = body.get("menuIds");
        if (menuIds == null) {
            return ApiResponse.fail(400, "menuIds不能为空");
        }
        roleService.assignMenus(id, menuIds);
        return ApiResponse.success("菜单权限分配成功", null);
    }
}
