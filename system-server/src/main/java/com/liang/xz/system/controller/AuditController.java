package com.liang.xz.system.controller;

import com.liang.xz.resource.security.RequirePermission;
import com.liang.xz.system.dto.ApiResponse;
import com.liang.xz.system.dto.AuditPageQuery;
import com.liang.xz.system.dto.AuthorizationRecordResponse;
import com.liang.xz.system.dto.PageResponse;
import com.liang.xz.system.dto.RevokeLogRequest;
import com.liang.xz.system.dto.RevokeLogResponse;
import com.liang.xz.system.service.AuthorizationAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 授权审计与Token吊销接口
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Tag(name = "授权审计", description = "授权记录查询、Token吊销、安全审计日志")
public class AuditController {

    private final AuthorizationAuditService auditService;

    @GetMapping("/authorizations/client/{clientId}")
    @Operation(summary = "查询某客户端的所有授权记录")
    @RequirePermission("system:audit:list")
    public ApiResponse<List<AuthorizationRecordResponse>> findRecordsByClient(
            @Parameter(description = "客户端ID") @PathVariable String clientId) {
        return ApiResponse.success(auditService.findRecordsByClient(clientId));
    }

    @GetMapping("/authorizations/user/{principalName}")
    @Operation(summary = "查询某用户的所有授权记录")
    @RequirePermission("system:audit:list")
    public ApiResponse<List<AuthorizationRecordResponse>> findRecordsByUser(
            @Parameter(description = "用户名") @PathVariable String principalName) {
        return ApiResponse.success(auditService.findRecordsByPrincipal(principalName));
    }

    @GetMapping("/authorizations/user/{principalName}/client/{clientId}")
    @Operation(summary = "查询某用户在某个客户端下的授权记录")
    @RequirePermission("system:audit:list")
    public ApiResponse<List<AuthorizationRecordResponse>> findRecordsByUserAndClient(
            @Parameter(description = "用户名") @PathVariable String principalName,
            @Parameter(description = "客户端ID") @PathVariable String clientId) {
        return ApiResponse.success(
                auditService.findRecordsByPrincipalAndClient(principalName, clientId));
    }

    @PostMapping("/revoke")
    @Operation(summary = "强制吊销Token")
    @RequirePermission("system:audit:revoke")
    public ApiResponse<Map<String, Object>> forceRevoke(
            @Parameter(description = "吊销请求") @Valid @RequestBody RevokeLogRequest request) {
        Map<String, Object> result = auditService.forceRevoke(request);
        return ApiResponse.success("Token吊销完成", result);
    }

    @GetMapping("/revoke-logs")
    @Operation(summary = "查询吊销日志(分页)")
    @RequirePermission("system:audit:list")
    public ApiResponse<List<RevokeLogResponse>> findRevokeLogs(
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "偏移量") @RequestParam(defaultValue = "0") int offset) {
        return ApiResponse.success(auditService.findRevokeLogs(limit, offset));
    }

    @GetMapping("/revoke-logs/page")
    @Operation(summary = "分页查询吊销日志", description = "支持按客户端ID、用户名、吊销类型等多条件筛选")
    @RequirePermission("system:audit:list")
    public ApiResponse<PageResponse<RevokeLogResponse>> pageRevokeLogs(@Valid AuditPageQuery query) {
        return ApiResponse.success(auditService.pageRevokeLogs(query));
    }

    @GetMapping("/revoke-logs/user/{userId}")
    @Operation(summary = "按用户查询吊销日志")
    @RequirePermission("system:audit:list")
    public ApiResponse<List<RevokeLogResponse>> findRevokeLogsByUser(
            @Parameter(description = "用户ID") @PathVariable String userId) {
        return ApiResponse.success(auditService.findRevokeLogsByUser(userId));
    }

    @GetMapping("/revoke-logs/client/{clientId}")
    @Operation(summary = "按客户端查询吊销日志")
    @RequirePermission("system:audit:list")
    public ApiResponse<List<RevokeLogResponse>> findRevokeLogsByClient(
            @Parameter(description = "客户端ID") @PathVariable String clientId) {
        return ApiResponse.success(auditService.findRevokeLogsByClient(clientId));
    }

    @GetMapping("/revoke-logs/type/{revokeType}")
    @Operation(summary = "按吊销类型查询")
    @RequirePermission("system:audit:list")
    public ApiResponse<List<RevokeLogResponse>> findRevokeLogsByType(
            @Parameter(description = "吊销类型") @PathVariable int revokeType) {
        return ApiResponse.success(auditService.findRevokeLogsByType(revokeType));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "门户中台概览统计")
    @RequirePermission("system:audit:list")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.success(auditService.getDashboardStats());
    }

    @GetMapping("/dashboard/client/{clientId}")
    @Operation(summary = "客户端活跃Token统计")
    @RequirePermission("system:audit:list")
    public ApiResponse<Map<String, Object>> clientStats(
            @Parameter(description = "客户端ID") @PathVariable String clientId) {
        return ApiResponse.success(auditService.getClientStats(clientId));
    }
}
