package com.liang.xz.aiagent.controller;

import com.liang.xz.aiagent.agent.BusinessAnalysisAgent;
import com.liang.xz.aiagent.dto.AlertVO;
import com.liang.xz.aiagent.dto.AnalysisRequest;
import com.liang.xz.aiagent.entity.AnalysisAlert;
import com.liang.xz.aiagent.service.AnalysisAlertService;
import com.liang.xz.common.core.model.R;
import com.liang.xz.resource.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * <p>业务数据分析预警 API</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Tag(name = "数据分析预警", description = "业务数据分析 / 趋势预测 / 异常检测 / 智能预警")
@RestController
@RequestMapping("/api/ai/analysis")
public class AnalysisAgentController {

    private final AnalysisAlertService analysisService;

    public AnalysisAgentController(AnalysisAlertService analysisService) {
        this.analysisService = analysisService;
    }

    @Operation(summary = "手动触发一次分析预警")
    @RequirePermission("ai:analysis:trigger")
    @PostMapping("/trigger")
    public R<Map<String, Object>> triggerAnalysis() {
        var result = analysisService.triggerAnalysis();
        return R.ok(Map.of(
                "success", true,
                "metricsCount", result.metrics().size(),
                "triggeredAlerts", result.evaluations().size(),
                "alerts", result.alerts().stream().map(this::toVO).toList(),
                "insight", result.llmInsight()
        ));
    }

    @Operation(summary = "即时分析问答", description = "输入自然语言的分析问题, AI采集数据后给出回答")
    @RequirePermission("ai:analysis:query")
    @PostMapping("/query")
    public R<Map<String, Object>> instantAnalysis(@RequestBody AnalysisRequest req) {
        var result = analysisService.instantAnalysis(req.getQuery());
        return R.ok(Map.of(
                "success", true,
                "query", req.getQuery(),
                "insight", result.llmInsight(),
                "metrics", result.metrics()
        ));
    }

    @Operation(summary = "获取当前关键业务指标")
    @RequirePermission("ai:analysis:list")
    @GetMapping("/metrics")
    public R<Map<String, Double>> getMetrics() {
        return R.ok(analysisService.getCurrentMetrics());
    }

    @Operation(summary = "分页条件查询预警列表")
    @RequirePermission("ai:analysis:list")
    @GetMapping("/alerts/list")
    public R<Map<String, Object>> queryAlerts(
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "预警级别 INFO/WARN/CRITICAL") @RequestParam(required = false) String level,
            @Parameter(description = "是否已处理") @RequestParam(required = false) Boolean resolved,
            @Parameter(description = "分析类型") @RequestParam(required = false) String analysisType,
            @Parameter(description = "开始时间") @RequestParam(required = false) String startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) String endTime,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {
        LocalDateTime start = (startTime != null && !startTime.isBlank()) ? LocalDateTime.parse(startTime) : null;
        LocalDateTime end = (endTime != null && !endTime.isBlank()) ? LocalDateTime.parse(endTime) : null;
        return R.ok(analysisService.queryAlerts(keyword, level, resolved, analysisType, start, end, page, size));
    }

    // ==================== 预警查询 ====================
    @Operation(summary = "获取未处理预警列表")
    @RequirePermission("ai:analysis:list")
    @GetMapping("/alerts")
    public R<List<AlertVO>> getUnresolvedAlerts() {
        List<AlertVO> alerts = analysisService.getUnresolvedAlerts().stream()
                .map(this::toVO).toList();
        return R.ok(alerts);
    }

    @Operation(summary = "按级别获取预警")
    @RequirePermission("ai:analysis:list")
    @GetMapping("/alerts/level/{level}")
    public R<List<AlertVO>> getAlertsByLevel(@PathVariable String level) {
        List<AlertVO> alerts = analysisService.getAlertsByLevel(level.toUpperCase()).stream()
                .map(this::toVO).toList();
        return R.ok(alerts);
    }

    @Operation(summary = "获取最近N条预警")
    @RequirePermission("ai:analysis:list")
    @GetMapping("/alerts/recent")
    public R<List<AlertVO>> getRecentAlerts(@RequestParam(defaultValue = "20") int n) {
        List<AlertVO> alerts = analysisService.getRecentAlerts(n).stream()
                .map(this::toVO).toList();
        return R.ok(alerts);
    }

    @Operation(summary = "获取预警汇总")
    @RequirePermission("ai:analysis:list")
    @GetMapping("/alerts/summary")
    public R<Map<String, Object>> getAlertSummary() {
        return R.ok(analysisService.getAlertSummary());
    }

    @Operation(summary = "处理预警")
    @RequirePermission("ai:analysis:resolve")
    @PutMapping("/alerts/{id}/resolve")
    public R<Map<String, Object>> resolveAlert(@PathVariable Long id,
                                             @RequestParam(defaultValue = "admin") String by) {
        analysisService.resolveAlert(id, by);
        return R.ok(Map.of("success", true));
    }

    @Operation(summary = "标记已读")
    @RequirePermission("ai:analysis:resolve")
    @PutMapping("/alerts/{id}/read")
    public R<Map<String, Object>> markRead(@PathVariable Long id) {
        analysisService.markAlertRead(id);
        return R.ok(Map.of("success", true));
    }

    private AlertVO toVO(AnalysisAlert a) {
        return AlertVO.builder()
                .id(a.getId())
                .alertName(a.getAlertName())
                .analysisType(a.getAnalysisType())
                .dataSource(a.getDataSource())
                .alertLevel(a.getAlertLevel())
                .alertContent(a.getAlertContent())
                .analysisDetail(a.getAnalysisDetail())
                .suggestion(a.getSuggestion())
                .isRead(a.getIsRead())
                .resolved(a.getResolved())
                .resolvedAt(a.getResolvedAt())
                .resolvedBy(a.getResolvedBy())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
