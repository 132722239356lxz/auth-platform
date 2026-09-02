package com.liang.xz.system.controller;

import com.liang.xz.resource.security.RequirePermission;
import com.liang.xz.system.dto.*;
import com.liang.xz.system.service.SubsystemTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 子系统Token管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/subsystem")
@RequiredArgsConstructor
@Tag(name = "子系统Token管理", description = "子系统自签Token的记录、刷新、吊销、查询")
public class SubsystemTokenController {

    private final SubsystemTokenService tokenService;

    @PostMapping("/tokens")
    @Operation(summary = "记录子系统Token")
    @RequirePermission("system:subsystem:add")
    public ApiResponse<SubsystemTokenResponse> recordToken(
            @Parameter(description = "子系统Token信息") @Valid @RequestBody SubsystemTokenRequest request) {
        try {
            SubsystemTokenResponse response = tokenService.recordToken(request);
            return ApiResponse.success("Token记录成功", response);
        } catch (Exception e) {
            log.error("[SubsystemToken] 记录Token失败: {}", e.getMessage());
            return ApiResponse.fail(500, "记录Token失败: " + e.getMessage());
        }
    }

    @PostMapping("/tokens/refresh")
    @Operation(summary = "刷新子系统Token")
    @RequirePermission("system:subsystem:refresh")
    public ApiResponse<SubsystemTokenResponse> refreshToken(
            @Parameter(description = "Token刷新请求") @Valid @RequestBody TokenRefreshRequest request) {
        try {
            SubsystemTokenResponse response = tokenService.refreshToken(request);
            return ApiResponse.success("Token刷新成功", response);
        } catch (IllegalArgumentException e) {
            log.warn("[SubsystemToken] Token刷新失败: {}", e.getMessage());
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("[SubsystemToken] Token刷新异常: {}", e.getMessage());
            return ApiResponse.fail(500, "Token刷新失败: " + e.getMessage());
        }
    }

    @PostMapping("/tokens/{id}/revoke")
    @Operation(summary = "吊销单个Token")
    @RequirePermission("system:subsystem:revoke")
    public ApiResponse<Void> revokeToken(
            @Parameter(description = "Token记录ID") @PathVariable Long id,
            @Parameter(description = "吊销原因") @RequestParam(defaultValue = "1") int reason,
            @Parameter(description = "备注") @RequestParam(defaultValue = "") String remark) {
        boolean success = tokenService.revokeToken(id, reason, remark);
        if (success) {
            return ApiResponse.success("Token已吊销", null);
        }
        return ApiResponse.fail(404, "Token不存在");
    }

    @PostMapping("/tokens/revoke-batch")
    @Operation(summary = "批量吊销Token")
    @RequirePermission("system:subsystem:revoke")
    public ApiResponse<Map<String, Object>> revokeAllActive(
            @Parameter(description = "客户端ID") @RequestParam String clientId,
            @Parameter(description = "用户名") @RequestParam String username,
            @Parameter(description = "吊销原因") @RequestParam(defaultValue = "1") int reason,
            @Parameter(description = "备注") @RequestParam(defaultValue = "") String remark) {
        try {
            Map<String, Object> result = tokenService.revokeAllActive(clientId, username, reason, remark);
            return ApiResponse.success("批量吊销完成", result);
        } catch (Exception e) {
            log.error("[SubsystemToken] 批量吊销失败: {}", e.getMessage());
            return ApiResponse.fail(500, "批量吊销失败: " + e.getMessage());
        }
    }

    @GetMapping("/tokens/{id}")
    @Operation(summary = "按ID查询Token记录")
    @RequirePermission("system:subsystem:list")
    public ApiResponse<SubsystemTokenResponse> getById(
            @Parameter(description = "Token记录ID") @PathVariable Long id) {
        SubsystemTokenResponse response = tokenService.findById(id);
        if (response != null) {
            return ApiResponse.success(response);
        }
        return ApiResponse.fail(404, "Token记录不存在");
    }

    @GetMapping("/tokens")
    @Operation(summary = "按客户端+用户查询Token记录")
    @RequirePermission("system:subsystem:list")
    public ApiResponse<List<SubsystemTokenResponse>> listByClientAndUser(
            @Parameter(description = "客户端ID") @RequestParam String clientId,
            @Parameter(description = "用户名") @RequestParam String username,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "偏移量") @RequestParam(defaultValue = "0") int offset) {
        return ApiResponse.success(
                tokenService.findAllByClientAndUser(clientId, username, limit, offset));
    }

    @GetMapping("/tokens/page")
    @Operation(summary = "分页查询Token记录", description = "支持按客户端ID和用户名多条件筛选")
    @RequirePermission("system:subsystem:list")
    public ApiResponse<PageResponse<SubsystemTokenResponse>> pageTokens(@Valid SubsystemTokenPageQuery query) {
        return ApiResponse.success(tokenService.pageList(query));
    }

    @GetMapping("/tokens/active")
    @Operation(summary = "查询活跃Token")
    @RequirePermission("system:subsystem:list")
    public ApiResponse<List<SubsystemTokenResponse>> listActive(
            @Parameter(description = "客户端ID") @RequestParam String clientId,
            @Parameter(description = "用户名") @RequestParam String username) {
        return ApiResponse.success(tokenService.findActiveByClientAndUser(clientId, username));
    }

    @GetMapping("/tokens/by-client/{clientId}")
    @Operation(summary = "按客户端查询Token记录")
    @RequirePermission("system:subsystem:list")
    public ApiResponse<List<SubsystemTokenResponse>> listByClient(
            @Parameter(description = "客户端ID") @PathVariable String clientId,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "偏移量") @RequestParam(defaultValue = "0") int offset) {
        return ApiResponse.success(tokenService.findByClientId(clientId, limit, offset));
    }

    @GetMapping("/tokens/by-user/{username}")
    @Operation(summary = "按用户查询Token记录")
    @RequirePermission("system:subsystem:list")
    public ApiResponse<List<SubsystemTokenResponse>> listByUser(
            @Parameter(description = "用户名") @PathVariable String username,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "偏移量") @RequestParam(defaultValue = "0") int offset) {
        return ApiResponse.success(tokenService.findByUsername(username, limit, offset));
    }

    @GetMapping("/stats")
    @Operation(summary = "子系统Token概览统计")
    @RequirePermission("system:subsystem:list")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.success(tokenService.getStats());
    }
}
