package com.liang.xz.log.controller;

import com.liang.xz.common.core.annotation.PublicApi;
import com.liang.xz.common.core.model.R;
import com.liang.xz.log.dto.LogReportRequest;
import com.liang.xz.log.service.LogCollectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 日志收集控制器
 * <p>
 * 对外提供日志上报接口, 供各模块通过 HTTP 上报日志到 log-server
 *
 * @author liang
 */
@Tag(name = "日志收集", description = "接收各模块上报的日志")
@RestController
@RequestMapping("/api/logs/collect")
@PublicApi
@RequiredArgsConstructor
public class LogCollectController {

    private final LogCollectService logCollectService;

    @Operation(summary = "批量上报日志")
    @PostMapping("/batch")
    public R<Map<String, Object>> collectBatch(@Valid @RequestBody LogReportRequest request) {
        logCollectService.collectBatch(request);
        int count = request.getEntries() != null ? request.getEntries().size() : 0;
        return R.ok(Map.of("count", count));
    }

    @Operation(summary = "单条上报日志(异步)")
    @PostMapping("/single")
    public R<Void> collectSingle(@RequestBody LogReportRequest.LogEntry entry) {
        logCollectService.collectSingle(entry);
        return R.ok(null);
    }

    @Operation(summary = "单条上报日志(同步, 确保落盘)")
    @PostMapping("/single/sync")
    public R<Void> collectSingleSync(@RequestBody LogReportRequest.LogEntry entry) {
        logCollectService.collectSingleSync(entry);
        return R.ok(null);
    }
}
