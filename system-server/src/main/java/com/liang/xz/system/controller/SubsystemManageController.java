package com.liang.xz.system.controller;

import com.liang.xz.resource.security.RequirePermission;
import com.liang.xz.system.dto.ApiResponse;
import com.liang.xz.system.dto.SubsystemRequest;
import com.liang.xz.system.dto.SubsystemResponse;
import com.liang.xz.system.service.ICurrentUserService;
import com.liang.xz.system.service.SubsystemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客户端子系统管理接口
 * <p>
 * 一个 OAuth2 客户端可配置多个子系统，
 * 每个子系统独立管理名称、图标、URL（回调域名）、描述、排序和门户可见性。
 * </p>
 */
@RestController
@RequestMapping("/api/subsystems")
@RequiredArgsConstructor
@Tag(name = "子系统管理", description = "客户端子系统的增删改查")
public class SubsystemManageController {

    private final SubsystemService subsystemService;
    private final ICurrentUserService currentUserService;

    /**
     * 查询所有子系统（管理后台）
     */
    @GetMapping
    @Operation(summary = "查询所有子系统", description = "可按 clientId 参数筛选")
    @RequirePermission("system:client:list")
    public ApiResponse<List<SubsystemResponse>> list(
            @Parameter(description = "客户端标识(可选筛选)") @RequestParam(required = false) String clientId) {
        if (clientId != null && !clientId.isBlank()) {
            return ApiResponse.success(subsystemService.listByClientId(clientId));
        }
        return ApiResponse.success(subsystemService.listAll());
    }

    /**
     * 门户首页：查询当前登录用户可见的子系统列表。
     * <p>
     * 该接口面向普通用户，只要已登录即可访问，无需管理权限。
     * </p>
     */
    @GetMapping("/portal")
    @Operation(summary = "查询当前用户可见的子系统(门户)")
    public ApiResponse<List<SubsystemResponse>> listPortalSubsystems() {
        Long userId = currentUserService.getCurrentUser().getUserId();
        return ApiResponse.success(subsystemService.listPortalSubsystems(userId));
    }

    /**
     * 按ID查询子系统
     */
    @GetMapping("/{id}")
    @Operation(summary = "按ID查询子系统")
    @RequirePermission("system:client:list")
    public ApiResponse<SubsystemResponse> getById(
            @Parameter(description = "子系统ID") @PathVariable Long id) {
        return subsystemService.getById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.fail(404, "子系统不存在"));
    }

    /**
     * 创建子系统
     */
    @PostMapping
    @Operation(summary = "创建子系统")
    @RequirePermission("system:client:edit")
    public ApiResponse<SubsystemResponse> create(
            @Parameter(description = "子系统信息") @Valid @RequestBody SubsystemRequest request) {
        return ApiResponse.success("子系统创建成功", subsystemService.create(request));
    }

    /**
     * 更新子系统
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新子系统")
    @RequirePermission("system:client:edit")
    public ApiResponse<SubsystemResponse> update(
            @Parameter(description = "子系统ID") @PathVariable Long id,
            @Parameter(description = "更新信息") @Valid @RequestBody SubsystemRequest request) {
        return subsystemService.update(id, request)
                .map(r -> ApiResponse.success("子系统更新成功", r))
                .orElse(ApiResponse.fail(404, "子系统不存在"));
    }

    /**
     * 删除子系统
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除子系统")
    @RequirePermission("system:client:edit")
    public ApiResponse<Void> delete(
            @Parameter(description = "子系统ID") @PathVariable Long id) {
        boolean deleted = subsystemService.deleteById(id);
        if (deleted) {
            return ApiResponse.success("子系统已删除", null);
        }
        return ApiResponse.fail(404, "子系统不存在");
    }
}
