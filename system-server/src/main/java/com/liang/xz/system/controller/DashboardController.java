package com.liang.xz.system.controller;

import com.liang.xz.resource.security.RequirePermission;
import com.liang.xz.system.dto.ApiResponse;
import com.liang.xz.system.dto.DashboardResponse;
import com.liang.xz.system.repository.AuthorizationRecordRepository;
import com.liang.xz.system.repository.SubsystemTokenRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>系统仪表盘接口 —— 提供权限系统概览统计</p>
 *
 * @author auth-platform
 * @since 1.2.0
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "系统仪表盘", description = "系统运行数据概览统计，含用户/角色/菜单/部门/客户端等核心数据量")
public class DashboardController {

    private final JdbcTemplate jdbcTemplate;
    private final SubsystemTokenRepository subsystemTokenRepository;
    private final AuthorizationRecordRepository authRepository;
    private final DiscoveryClient discoveryClient;

    @GetMapping("/stats")
    @Operation(summary = "获取系统统计数据", description = "返回用户/角色/菜单/部门/字典/客户端/系统活跃Token/子系统活跃Token等核心数据的统计信息，用于仪表盘展示")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "统计数据",
                    content = @Content(schema = @Schema(implementation = DashboardResponse.class)))
    })
    @RequirePermission("system:dashboard:view")
    public ApiResponse<DashboardResponse> getStats() {
        long userCount = queryCount("sys_user");
        long roleCount = queryCount("sys_role");
        long menuCount = queryCount("sys_menu");
        long deptCount = queryCount("sys_dept");
        long dictTypeCount = queryCount("sys_dict_type");
        long dictDataCount = queryCount("sys_dict_data");
        long clientCount = queryCount("oauth2_registered_client");
        long subsystemActiveTokenCount = subsystemTokenRepository.countActive();
        long systemActiveTokenCount = authRepository.countActiveTokens();

        return ApiResponse.success(DashboardResponse.builder()
                .userCount(userCount)
                .roleCount(roleCount)
                .menuCount(menuCount)
                .deptCount(deptCount)
                .dictTypeCount(dictTypeCount)
                .dictDataCount(dictDataCount)
                .clientCount(clientCount)
                .subsystemActiveTokenCount(subsystemActiveTokenCount)
                .systemActiveTokenCount(systemActiveTokenCount)
                .activeTokenCount(subsystemActiveTokenCount + systemActiveTokenCount)
                .build());
    }

    @GetMapping("/services")
    @Operation(summary = "获取服务健康状态", description = "通过Nacos注册中心查询各微服务实例状态")
    public ApiResponse<List<Map<String, Object>>> getServiceHealth() {
        List<String> serviceNames = List.of(
                "gateway", "auth-server", "system-server", "auth-flow",
                "auth-message", "ai-agent-server", "log-server");

        List<Map<String, Object>> result = serviceNames.stream().map(name -> {
            List<ServiceInstance> instances = discoveryClient.getInstances(name);
            boolean running = instances != null && !instances.isEmpty();

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", formatServiceName(name));
            item.put("serviceId", name);
            item.put("status", running ? "running" : "down");
            item.put("instanceCount", instances != null ? instances.size() : 0);
            item.put("port", instances != null && !instances.isEmpty()
                    ? instances.get(0).getPort() : null);
            return item;
        }).toList();

        return ApiResponse.success(result);
    }

    private String formatServiceName(String serviceId) {
        return switch (serviceId) {
            case "gateway" -> "Gateway";
            case "auth-server" -> "Auth-Server";
            case "system-server" -> "System-Server";
            case "auth-flow" -> "Auth-Flow";
            case "auth-message" -> "Auth-Message";
            case "ai-agent-server" -> "AI-Agent";
            case "log-server" -> "Log-Server";
            default -> serviceId;
        };
    }

    private long queryCount(String table) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table, Long.class);
        return count != null ? count : 0;
    }
}
