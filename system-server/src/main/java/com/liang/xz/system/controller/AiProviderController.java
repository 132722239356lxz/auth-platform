package com.liang.xz.system.controller;

import com.liang.xz.resource.security.RequirePermission;
import com.liang.xz.system.dto.AiProviderOptionResponse;
import com.liang.xz.system.dto.AiProviderPageQuery;
import com.liang.xz.system.dto.AiProviderRequest;
import com.liang.xz.system.dto.AiProviderResponse;
import com.liang.xz.system.dto.ApiResponse;
import com.liang.xz.system.dto.PageResponse;
import com.liang.xz.system.dto.ProviderTestRequest;
import com.liang.xz.system.dto.ProviderTestResponse;
import com.liang.xz.system.service.IAiProviderService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>AI 供应商配置管理接口</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/ai-providers")
@RequiredArgsConstructor
@Tag(name = "AI供应商配置", description = "AI 模型供应商的接入配置与连通性测试")
public class AiProviderController {

    private final IAiProviderService aiProviderService;

    @GetMapping("/page")
    @Operation(summary = "分页查询供应商配置")
    @RequirePermission("system:ai-provider:list")
    public ApiResponse<PageResponse<AiProviderResponse>> page(AiProviderPageQuery query) {
        return ApiResponse.success(aiProviderService.page(query));
    }

    @GetMapping("/options")
    @Operation(summary = "查询启用中的供应商下拉选项")
    @RequirePermission("system:ai-provider:list")
    public ApiResponse<List<AiProviderOptionResponse>> options() {
        return ApiResponse.success(aiProviderService.listOptions());
    }

    @GetMapping("/{id}")
    @Operation(summary = "按ID查询供应商配置")
    @RequirePermission("system:ai-provider:list")
    public ApiResponse<AiProviderResponse> getById(
            @Parameter(description = "供应商ID") @PathVariable Long id) {
        AiProviderResponse response = aiProviderService.getById(id);
        if (response == null) {
            return ApiResponse.fail(404, "供应商配置不存在");
        }
        return ApiResponse.success(response);
    }

    @PostMapping
    @Operation(summary = "创建供应商配置")
    @RequirePermission("system:ai-provider:add")
    public ApiResponse<AiProviderResponse> create(
            @Parameter(description = "供应商配置") @Valid @RequestBody AiProviderRequest request) {
        try {
            return ApiResponse.success("供应商配置创建成功", aiProviderService.create(request));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新供应商配置", description = "密钥留空表示保持原值不变")
    @RequirePermission("system:ai-provider:edit")
    public ApiResponse<AiProviderResponse> update(
            @Parameter(description = "供应商ID") @PathVariable Long id,
            @Parameter(description = "供应商配置") @Valid @RequestBody AiProviderRequest request) {
        try {
            AiProviderResponse response = aiProviderService.update(id, request);
            if (response == null) {
                return ApiResponse.fail(404, "供应商配置不存在");
            }
            return ApiResponse.success("供应商配置更新成功", response);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除供应商配置", description = "已被复杂度路由引用的供应商不允许删除")
    @RequirePermission("system:ai-provider:delete")
    public ApiResponse<Void> delete(
            @Parameter(description = "供应商ID") @PathVariable Long id) {
        try {
            boolean ok = aiProviderService.delete(id);
            return ok ? ApiResponse.success("供应商配置已删除", null)
                    : ApiResponse.fail(404, "供应商配置不存在");
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @PostMapping("/test")
    @Operation(summary = "按表单参数探测连通性",
            description = "探测失败属于业务结果，接口仍返回成功，具体成败见 data.success")
    @RequirePermission("system:ai-provider:test")
    public ApiResponse<ProviderTestResponse> test(
            @Parameter(description = "探测参数") @Valid @RequestBody ProviderTestRequest request) {
        return ApiResponse.success(aiProviderService.test(request));
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "按已保存配置探测连通性",
            description = "探测失败属于业务结果，接口仍返回成功，具体成败见 data.success")
    @RequirePermission("system:ai-provider:test")
    public ApiResponse<ProviderTestResponse> testById(
            @Parameter(description = "供应商ID") @PathVariable Long id) {
        try {
            return ApiResponse.success(aiProviderService.testById(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }
}
