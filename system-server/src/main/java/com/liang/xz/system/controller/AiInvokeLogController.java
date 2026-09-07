package com.liang.xz.system.controller;

import com.liang.xz.resource.security.RequirePermission;
import com.liang.xz.system.dto.AiInvokeLogPageQuery;
import com.liang.xz.system.dto.AiInvokeLogResponse;
import com.liang.xz.system.dto.AiInvokeLogStatsResponse;
import com.liang.xz.system.dto.ApiResponse;
import com.liang.xz.system.dto.PageResponse;
import com.liang.xz.system.service.IAiInvokeLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * <p>AI 调用记录管理接口</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/ai-invoke-logs")
@RequiredArgsConstructor
public class AiInvokeLogController {

    private final IAiInvokeLogService aiInvokeLogService;

    @RequirePermission("system:ai-invoke-log:list")
    @PostMapping("/page")
    public ApiResponse<PageResponse<AiInvokeLogResponse>> page(@RequestBody AiInvokeLogPageQuery query) {
        return ApiResponse.success(aiInvokeLogService.page(query));
    }

    @RequirePermission("system:ai-invoke-log:query")
    @GetMapping("/{id}")
    public ApiResponse<AiInvokeLogResponse> detail(@PathVariable("id") Long id) {
        AiInvokeLogResponse resp = aiInvokeLogService.detail(id);
        if (resp == null) {
            return ApiResponse.fail(404, "记录不存在");
        }
        return ApiResponse.success(resp);
    }

    @RequirePermission("system:ai-invoke-log:list")
    @GetMapping("/stats")
    public ApiResponse<AiInvokeLogStatsResponse> stats(
            @RequestParam(value = "start", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
            @RequestParam(value = "end", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end) {
        return ApiResponse.success(aiInvokeLogService.stats(start, end));
    }

    @RequirePermission("system:ai-invoke-log:delete")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        aiInvokeLogService.delete(id);
        return ApiResponse.success((Void) null);
    }

    @RequirePermission("system:ai-invoke-log:clear")
    @DeleteMapping("/clear")
    public ApiResponse<Void> clear() {
        aiInvokeLogService.clear();
        return ApiResponse.success((Void) null);
    }
}
