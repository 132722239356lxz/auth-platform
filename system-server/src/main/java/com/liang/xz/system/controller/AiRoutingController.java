package com.liang.xz.system.controller;

import com.liang.xz.resource.security.RequirePermission;
import com.liang.xz.system.dto.AiProviderOptionResponse;
import com.liang.xz.system.dto.AiRoutingPageQuery;
import com.liang.xz.system.dto.AiRoutingRequest;
import com.liang.xz.system.dto.AiRoutingResponse;
import com.liang.xz.system.dto.ApiResponse;
import com.liang.xz.system.dto.PageResponse;
import com.liang.xz.system.service.IAiComplexityRoutingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>AI 复杂度路由配置管理接口</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/ai-routings")
@RequiredArgsConstructor
@Tag(name = "AI复杂度路由", description = "按任务复杂度为不同场景分配模型与 tokens 上限")
public class AiRoutingController {

    private final IAiComplexityRoutingService aiComplexityRoutingService;

    @GetMapping("/page")
    @Operation(summary = "分页查询路由配置")
    @RequirePermission("system:ai-routing:list")
    public ApiResponse<PageResponse<AiRoutingResponse>> page(AiRoutingPageQuery query) {
        return ApiResponse.success(aiComplexityRoutingService.page(query));
    }

    @GetMapping("/available-providers")
    @Operation(summary = "查询可绑定的供应商",
            description = "一个供应商至多绑定一条路由；编辑时传 excludeRoutingId 以保留自身已选供应商")
    @RequirePermission("system:ai-routing:list")
    public ApiResponse<List<AiProviderOptionResponse>> availableProviders(
            @Parameter(description = "编辑场景下当前路由ID") @RequestParam(required = false) Long excludeRoutingId) {
        return ApiResponse.success(aiComplexityRoutingService.listAvailableProviders(excludeRoutingId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "按ID查询路由配置")
    @RequirePermission("system:ai-routing:list")
    public ApiResponse<AiRoutingResponse> getById(
            @Parameter(description = "路由ID") @PathVariable Long id) {
        AiRoutingResponse response = aiComplexityRoutingService.getById(id);
        if (response == null) {
            return ApiResponse.fail(404, "路由配置不存在");
        }
        return ApiResponse.success(response);
    }

    @PostMapping
    @Operation(summary = "创建路由配置")
    @RequirePermission("system:ai-routing:add")
    public ApiResponse<AiRoutingResponse> create(
            @Parameter(description = "路由配置") @Valid @RequestBody AiRoutingRequest request) {
        try {
            return ApiResponse.success("路由配置创建成功", aiComplexityRoutingService.create(request));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新路由配置")
    @RequirePermission("system:ai-routing:edit")
    public ApiResponse<AiRoutingResponse> update(
            @Parameter(description = "路由ID") @PathVariable Long id,
            @Parameter(description = "路由配置") @Valid @RequestBody AiRoutingRequest request) {
        try {
            AiRoutingResponse response = aiComplexityRoutingService.update(id, request);
            if (response == null) {
                return ApiResponse.fail(404, "路由配置不存在");
            }
            return ApiResponse.success("路由配置更新成功", response);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除路由配置")
    @RequirePermission("system:ai-routing:delete")
    public ApiResponse<Void> delete(
            @Parameter(description = "路由ID") @PathVariable Long id) {
        boolean ok = aiComplexityRoutingService.delete(id);
        return ok ? ApiResponse.success("路由配置已删除", null)
                : ApiResponse.fail(404, "路由配置不存在");
    }
}
