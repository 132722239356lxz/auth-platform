package com.liang.xz.log.controller;

import com.liang.xz.common.core.model.R;
import com.liang.xz.log.dto.LogQueryRequest;
import com.liang.xz.log.dto.LogStatsDTO;
import com.liang.xz.log.entity.LogRecord;
import com.liang.xz.log.service.LogQueryService;
import com.liang.xz.resource.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 日志查询与统计控制器
 *
 * @author liang
 */
@Tag(name = "日志查询", description = "日志检索/统计/追踪接口")
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogQueryController {

    private final LogQueryService logQueryService;

    @Operation(summary = "分页查询日志")
    @RequirePermission("log:list")
    @PostMapping("/query")
    public R<Map<String, Object>> query(@RequestBody LogQueryRequest request) {
        Map<String, Object> pageResult = logQueryService.queryPage(request);
        return R.ok(pageResult);
    }

    @Operation(summary = "按TraceId追踪调用链")
    @RequirePermission("log:list")
    @GetMapping("/trace/{traceId}")
    public R<List<LogRecord>> traceByTraceId(
            @Parameter(description = "链路追踪ID") @PathVariable String traceId) {
        List<LogRecord> chain = logQueryService.traceByTraceId(traceId);
        return R.ok(chain);
    }

    @Operation(summary = "获取日志统计概览")
    @RequirePermission("log:list")
    @GetMapping("/stats")
    public R<LogStatsDTO> stats(
            @Parameter(description = "开始时间") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        LogStatsDTO stats = logQueryService.stats(startTime, endTime);
        return R.ok(stats);
    }

    @Operation(summary = "清理过期日志")
    @RequirePermission("log:delete")
    @DeleteMapping("/clean")
    public R<Map<String, Object>> clean(
            @Parameter(description = "保留天数(默认30)") @RequestParam(defaultValue = "30") int retentionDays) {
        int deleted = logQueryService.cleanExpired(retentionDays, 500);
        return R.ok(Map.of("deleted", deleted, "retentionDays", retentionDays));
    }
}
