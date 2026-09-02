package com.liang.xz.system.controller;

import com.liang.xz.system.dto.ApiResponse;
import com.liang.xz.system.service.UserSubsystemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户-子系统可见性管理
 * <p>
 * 管理门户用户对各子系统的可见性关系
 *
 * @author auth-platform
 */
@RestController
@RequestMapping("/api/system/user-subsystems")
@RequiredArgsConstructor
@Tag(name = "用户子系统管理", description = "用户-子系统可见性关系管理")
public class UserSubsystemController {

    private final UserSubsystemService userSubsystemService;

    @GetMapping
    @Operation(summary = "查询用户可见的子系统列表")
    public ApiResponse<List<Map<String, Object>>> getUserSubsystems(@RequestParam Long userId) {
        return ApiResponse.success(userSubsystemService.getUserSubsystems(userId));
    }

    @PutMapping("/{userId}")
    @Operation(summary = "批量分配用户可见子系统")
    public ApiResponse<Void> assignSubsystems(@PathVariable Long userId,
                                               @RequestBody Map<String, List<String>> body) {
        userSubsystemService.assignSubsystems(userId, body.get("clientIds"));
        return ApiResponse.success("分配成功", null);
    }

    @GetMapping("/subsystems")
    @Operation(summary = "查询所有已注册子系统")
    public ApiResponse<List<Map<String, Object>>> listAllSubsystems() {
        return ApiResponse.success(userSubsystemService.listAllSubsystems());
    }

    @GetMapping("/users")
    @Operation(summary = "查询某子系统下的用户列表")
    public ApiResponse<Map<String, Object>> getSubsystemUsers(
            @RequestParam String clientId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(userSubsystemService.getSubsystemUsers(clientId, page, size));
    }

    @PostMapping("/grant")
    @Operation(summary = "给用户授权子系统可见")
    public ApiResponse<Void> grant(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        String clientId = (String) body.get("clientId");
        Long grantedBy = body.get("grantedBy") != null ? Long.valueOf(body.get("grantedBy").toString()) : null;
        userSubsystemService.grant(userId, clientId, grantedBy);
        return ApiResponse.success("授权成功", null);
    }

    @DeleteMapping("/{userId}/{clientId}")
    @Operation(summary = "撤销用户子系统可见")
    public ApiResponse<Void> revoke(@PathVariable Long userId, @PathVariable String clientId) {
        userSubsystemService.revoke(userId, clientId);
        return ApiResponse.success("撤销成功", null);
    }
}
