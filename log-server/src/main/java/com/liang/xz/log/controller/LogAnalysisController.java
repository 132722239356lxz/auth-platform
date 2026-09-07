package com.liang.xz.log.controller;

import com.liang.xz.common.core.model.R;
import com.liang.xz.log.dto.LogAnalysisResult;
import com.liang.xz.log.dto.LogAnalysisResult.SimilarError;
import com.liang.xz.log.dto.SolutionSaveRequest;
import com.liang.xz.log.entity.ErrorSolution;
import com.liang.xz.log.service.LogAnalysisService;
import com.liang.xz.log.service.SolutionService;
import com.liang.xz.resource.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 日志分析 + 解决方案管理控制器
 *
 * @author liang
 */
@Tag(name = "AI日志分析", description = "AI驱动的错误分析/解决方案管理")
@RestController
@RequestMapping("/api/logs/analysis")
@RequiredArgsConstructor
public class LogAnalysisController {

    private final LogAnalysisService logAnalysisService;
    private final SolutionService solutionService;

    // ======================== AI 分析 ========================

    @Operation(summary = "按错误指纹分析 - 查找同类错误并推荐解决方案")
    @RequirePermission("log:analysis:list")
    @GetMapping("/fingerprint/{fingerprint}")
    public R<LogAnalysisResult> analyzeByFingerprint(
            @Parameter(description = "错误指纹") @PathVariable String fingerprint) {
        LogAnalysisResult result = logAnalysisService.analyzeByFingerprint(fingerprint);
        return R.ok(result);
    }

    @Operation(summary = "按TraceId分析 - 还原调用链上下文")
    @RequirePermission("log:analysis:list")
    @GetMapping("/trace/{traceId}")
    public R<LogAnalysisResult> analyzeByTraceId(
            @Parameter(description = "链路追踪ID") @PathVariable String traceId) {
        LogAnalysisResult result = logAnalysisService.analyzeByTraceId(traceId);
        return R.ok(result);
    }

    @Operation(summary = "搜索相似错误")
    @RequirePermission("log:analysis:list")
    @GetMapping("/similar")
    public R<List<SimilarError>> searchSimilar(
            @Parameter(description = "异常类型") @RequestParam(required = false) String exceptionType,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "返回条数") @RequestParam(defaultValue = "10") int limit) {
        List<SimilarError> errors = logAnalysisService.searchSimilarErrors(exceptionType, keyword, limit);
        return R.ok(errors);
    }

    // ======================== 解决方案管理 ========================

    @Operation(summary = "保存或更新解决方案")
    @RequirePermission("log:analysis:edit")
    @PostMapping("/solution")
    public R<Map<String, Object>> saveSolution(@RequestBody SolutionSaveRequest request) {
        ErrorSolution solution = solutionService.saveOrUpdate(request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", solution.getId());
        data.put("fingerprint", solution.getErrorFingerprint());
        return R.ok("保存成功", data);
    }

    @Operation(summary = "按指纹查询解决方案")
    @RequirePermission("log:analysis:list")
    @GetMapping("/solution/{fingerprint}")
    public R<Map<String, Object>> getSolution(
            @Parameter(description = "错误指纹") @PathVariable String fingerprint) {
        return solutionService.findByFingerprint(fingerprint)
                .map(s -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("fingerprint", s.getErrorFingerprint());
                    data.put("rootCause", s.getRootCause());
                    data.put("solution", s.getSolution());
                    data.put("steps", s.getSteps());
                    data.put("referenceUrl", s.getReferenceUrl());
                    data.put("resolveCount", s.getResolveCount());
                    data.put("status", s.getStatus());
                    return R.ok(data);
                })
                .orElse(R.fail(404, "未找到该指纹的解决方案"));
    }

    @Operation(summary = "标记解决方案已使用(次数+1)")
    @RequirePermission("log:analysis:edit")
    @PutMapping("/solution/{fingerprint}/resolved")
    public R<Void> markResolved(
            @Parameter(description = "错误指纹") @PathVariable String fingerprint) {
        solutionService.incrementResolveCount(fingerprint);
        return R.ok(null);
    }

    @Operation(summary = "热门解决方案 TOP N")
    @RequirePermission("log:analysis:list")
    @GetMapping("/solution/top")
    public R<List<ErrorSolution>> topSolutions(
            @Parameter(description = "返回条数") @RequestParam(defaultValue = "10") int limit) {
        List<ErrorSolution> solutions = solutionService.findTopSolutions(limit);
        return R.ok(solutions);
    }
}
