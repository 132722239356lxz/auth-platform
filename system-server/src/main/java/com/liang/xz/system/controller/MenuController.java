package com.liang.xz.system.controller;

import com.liang.xz.system.dto.ApiResponse;
import com.liang.xz.system.dto.MenuRequest;
import com.liang.xz.system.dto.MenuResponse;
import com.liang.xz.system.dto.MenuTreeQuery;
import com.liang.xz.resource.security.RequirePermission;
import com.liang.xz.system.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>菜单管理接口</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
@Tag(name = "菜单管理", description = "动态菜单树的增删改查，支持目录/菜单/按钮三级，支持启用/禁用及前端多条件筛选")
public class MenuController {

    private final MenuService menuService;

    @GetMapping("/tree")
    @Operation(summary = "获取完整菜单树", description = "返回所有菜单的树形结构(用于管理端菜单管理页面)，支持 keyword/menuType/enabled 过滤")
    @RequirePermission("system:menu:list")
    public ApiResponse<List<MenuResponse>> getMenuTree(@Valid MenuTreeQuery query) {
        return ApiResponse.success(menuService.getMenuTree(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "按ID查询菜单")
    @RequirePermission("system:menu:list")
    public ApiResponse<MenuResponse> getById(
            @Parameter(description = "菜单ID") @PathVariable Long id) {
        return menuService.getById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.fail(404, "菜单不存在"));
    }

    @PostMapping
    @Operation(summary = "创建菜单", description = "创建目录/菜单/按钮，按钮类型需要填写权限标识")
    @RequirePermission("system:menu:add")
    public ApiResponse<MenuResponse> create(
            @Parameter(description = "菜单信息") @Valid @RequestBody MenuRequest request) {
        return ApiResponse.success("菜单创建成功", menuService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新菜单")
    @RequirePermission("system:menu:edit")
    public ApiResponse<MenuResponse> update(
            @Parameter(description = "菜单ID") @PathVariable Long id,
            @Parameter(description = "菜单信息") @Valid @RequestBody MenuRequest request) {
        return menuService.update(id, request)
                .map(r -> ApiResponse.success("菜单更新成功", r))
                .orElse(ApiResponse.fail(404, "菜单不存在"));
    }

    @PutMapping("/{id}/enable")
    @Operation(summary = "启用菜单")
    @RequirePermission("system:menu:edit")
    public ApiResponse<Void> enable(
            @Parameter(description = "菜单ID") @PathVariable Long id) {
        boolean ok = menuService.enable(id);
        return ok ? ApiResponse.success("菜单已启用", null)
                : ApiResponse.fail(404, "菜单不存在");
    }

    @PutMapping("/{id}/disable")
    @Operation(summary = "禁用菜单")
    @RequirePermission("system:menu:edit")
    public ApiResponse<Void> disable(
            @Parameter(description = "菜单ID") @PathVariable Long id) {
        boolean ok = menuService.disable(id);
        return ok ? ApiResponse.success("菜单已禁用", null)
                : ApiResponse.fail(404, "菜单不存在");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除菜单", description = "存在子菜单时禁止删除，删除同时清除角色-菜单关联")
    @RequirePermission("system:menu:delete")
    public ApiResponse<Void> delete(
            @Parameter(description = "菜单ID") @PathVariable Long id) {
        try {
            boolean ok = menuService.delete(id);
            return ok ? ApiResponse.success("菜单已删除", null)
                    : ApiResponse.fail(404, "菜单不存在");
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }
}
