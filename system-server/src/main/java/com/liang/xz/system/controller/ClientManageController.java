package com.liang.xz.system.controller;

import com.liang.xz.common.core.crypto.CryptoManager;
import com.liang.xz.resource.security.RequirePermission;
import com.liang.xz.system.dto.*;
import com.liang.xz.system.service.ICurrentUserService;
import com.liang.xz.system.service.Oauth2ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * OAuth2 客户端管理接口
 */
@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
@Tag(name = "客户端管理", description = "第三方客户端的注册、上下线、密钥管理")
public class ClientManageController {

    private final Oauth2ClientService clientService;
    private final CryptoManager cryptoManager;
    private final ICurrentUserService currentUserService;

    @GetMapping
    @Operation(summary = "查询所有客户端")
    @RequirePermission("system:client:list")
    public ApiResponse<List<ClientResponse>> list() {
        return ApiResponse.success(clientService.listAll());
    }

    /**
     * 门户首页：查询当前登录用户可见的客户端列表。
     *
     * <p>该接口面向普通用户，用于在门户首页展示已授权访问的业务系统入口。
     * 不标注 {@link RequirePermission}，因此只要用户已登录即可访问，
     * 无需 {@code system:client:list} 管理权限。</p>
     */
    @GetMapping("/portal")
    @Operation(summary = "查询当前用户可见的客户端(门户)")
    public ApiResponse<List<ClientResponse>> listPortalClients() {
        Long userId = currentUserService.getCurrentUser().getUserId();
        return ApiResponse.success(clientService.listAccessibleClients(userId));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询客户端", description = "支持按关键词(ID/名称)和启用状态多条件筛选")
    @RequirePermission("system:client:list")
    public ApiResponse<PageResponse<ClientResponse>> page(@Valid ClientPageQuery query) {
        return ApiResponse.success(clientService.pageList(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "按主键ID查询客户端")
    @RequirePermission("system:client:list")
    public ApiResponse<ClientResponse> getById(
            @Parameter(description = "客户端主键ID") @PathVariable String id) {
        return clientService.getById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.fail(404, "客户端不存在"));
    }

    @PostMapping
    @Operation(summary = "注册新客户端")
    @RequirePermission("system:client:add")
    public ApiResponse<ClientResponse> create(
            @Parameter(description = "客户端注册信息") @Valid @RequestBody ClientRequest request) {
        try {
            return ApiResponse.success("客户端注册成功", clientService.create(request));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新客户端配置")
    @RequirePermission("system:client:edit")
    public ApiResponse<ClientResponse> update(
            @Parameter(description = "客户端主键ID") @PathVariable String id,
            @Parameter(description = "更新信息") @Valid @RequestBody ClientRequest request) {
        return clientService.update(id, request)
                .map(r -> ApiResponse.success("客户端更新成功", r))
                .orElse(ApiResponse.fail(404, "客户端不存在"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除客户端")
    @RequirePermission("system:client:delete")
    public ApiResponse<Void> delete(
            @Parameter(description = "客户端主键ID") @PathVariable String id) {
        boolean deleted = clientService.delete(id);
        if (deleted) {
            return ApiResponse.success("客户端已注销", null);
        }
        return ApiResponse.fail(404, "客户端不存在");
    }

    @PutMapping("/{id}/secret")
    @Operation(summary = "重置客户端密钥")
    @RequirePermission("system:client:secret")
    public ApiResponse<ClientResponse> resetSecret(
            @Parameter(description = "客户端主键ID") @PathVariable String id,
            @Parameter(description = "新密钥（留空自动生成）") @RequestBody Map<String, String> body) {
        String newSecret = body != null ? body.get("secret") : null;
        return clientService.resetSecret(id, newSecret)
                .map(r -> ApiResponse.success("密钥重置成功", r))
                .orElse(ApiResponse.fail(404, "客户端不存在"));
    }

    @PutMapping("/{id}/enable")
    @Operation(summary = "客户端上线")
    @RequirePermission("system:client:edit")
    public ApiResponse<ClientResponse> enable(
            @Parameter(description = "客户端主键ID") @PathVariable String id) {
        return clientService.enable(id)
                .map(r -> ApiResponse.success("客户端已上线", r))
                .orElse(ApiResponse.fail(404, "客户端不存在"));
    }

    @PutMapping("/{id}/disable")
    @Operation(summary = "客户端下线")
    @RequirePermission("system:client:edit")
    public ApiResponse<ClientResponse> disable(
            @Parameter(description = "客户端主键ID") @PathVariable String id) {
        return clientService.disable(id)
                .map(r -> ApiResponse.success("客户端已下线", r))
                .orElse(ApiResponse.fail(404, "客户端不存在"));
    }

    @GetMapping("/crypto-info")
    @Operation(summary = "查询当前加密配置")
    @RequirePermission("system:client:list")
    public ApiResponse<Map<String, String>> cryptoInfo() {
        return ApiResponse.success(Map.of(
                "currentAlgorithm", cryptoManager.getCurrentAlgorithm(),
                "supportedAlgorithms", "AES, SM4, DES"
        ));
    }
}
