package com.liang.xz.log.service;

import com.liang.xz.log.dto.LogAnalysisResult;
import com.liang.xz.log.dto.LogAnalysisResult.SimilarError;
import com.liang.xz.log.dto.LogAnalysisResult.SolutionRecommendation;
import com.liang.xz.log.dto.LogAnalysisResult.TimeBucket;
import com.liang.xz.log.entity.ErrorSolution;
import com.liang.xz.log.entity.LogRecord;
import com.liang.xz.log.repository.ErrorSolutionRepository;
import com.liang.xz.log.repository.LogRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 日志分析服务
 * <p>
 * 核心能力:
 * 1. 错误指纹匹配 - 快速定位同类错误
 * 2. 上下文串联 - 通过 traceId 还原完整调用链
 * 3. 相似度排序 - 对匹配结果按相似度排序
 * 4. 解决方案推荐 - 从知识库匹配已知方案
 * 5. 智能分析建议 - 基于错误模式生成AI分析建议
 *
 * @author liang
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogAnalysisService {

    private final LogRecordRepository logRecordRepository;
    private final ErrorSolutionRepository errorSolutionRepository;
    private final LogQueryService logQueryService;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_SIMILAR = 20;

    // ======================== 按错误指纹分析 ========================

    /**
     * 对指定错误指纹进行深度分析
     *
     * @param fingerprint 错误指纹
     * @return 完整分析结果
     */
    public LogAnalysisResult analyzeByFingerprint(String fingerprint) {
        List<LogRecord> similarLogs = logRecordRepository
                .findByErrorFingerprint(fingerprint, MAX_SIMILAR);

        if (similarLogs.isEmpty()) {
            return LogAnalysisResult.builder()
                    .targetFingerprint(fingerprint)
                    .similarCount(0)
                    .recommendation(SolutionRecommendation.builder()
                            .hasKnownSolution(false)
                            .aiSuggestion("未找到该指纹对应的错误记录, 请确认指纹是否正确")
                            .build())
                    .build();
        }

        // ── 1. 整理相似错误 ──
        List<SimilarError> similarErrors = new ArrayList<>();
        for (LogRecord lr : similarLogs) {
            similarErrors.add(SimilarError.builder()
                    .logId(lr.getId())
                    .module(lr.getModule())
                    .exceptionType(lr.getExceptionType())
                    .message(lr.getMessage())
                    .stackTrace(extractTopStack(lr.getExceptionStack()))
                    .logTime(lr.getLogTime() != null ? lr.getLogTime().format(DT_FMT) : null)
                    .traceId(lr.getTraceId())
                    .similarity(computeSimilarity(lr))
                    .build());
        }

        // ── 2. 时间分布 ──
        List<TimeBucket> timeDistribution = computeTimeDistribution(similarLogs);

        // ── 3. 查询已知解决方案 ──
        Optional<ErrorSolution> solutionOpt = errorSolutionRepository
                .findByFingerprint(fingerprint);

        SolutionRecommendation recommendation;
        if (solutionOpt.isPresent()) {
            ErrorSolution es = solutionOpt.get();
            recommendation = SolutionRecommendation.builder()
                    .hasKnownSolution(true)
                    .rootCause(es.getRootCause())
                    .solution(es.getSolution())
                    .steps(es.getSteps() != null
                            ? Arrays.asList(es.getSteps().split("\n"))
                            : Collections.emptyList())
                    .referenceUrl(es.getReferenceUrl())
                    .aiSuggestion(generateAiSuggestion(similarLogs.get(0), es))
                    .relatedTraceIds(similarErrors.stream()
                            .map(SimilarError::getTraceId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList())
                    .build();
        } else {
            // 无已知方案, 生成AI分析建议
            recommendation = SolutionRecommendation.builder()
                    .hasKnownSolution(false)
                    .rootCause(inferRootCause(similarLogs))
                    .aiSuggestion(generateAiSuggestionWithoutSolution(similarLogs))
                    .relatedTraceIds(similarErrors.stream()
                            .map(SimilarError::getTraceId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList())
                    .build();
        }

        return LogAnalysisResult.builder()
                .targetFingerprint(fingerprint)
                .exceptionType(similarLogs.get(0).getExceptionType())
                .exceptionSummary(similarLogs.get(0).getMessage())
                .similarCount(similarLogs.size())
                .similarErrors(similarErrors)
                .timeDistribution(timeDistribution)
                .recommendation(recommendation)
                .build();
    }

    // ======================== 按 TraceId 上下文分析 ========================

    /**
     * 按 traceId 进行上下文分析: 还原调用链 + 查找链中所有错误
     */
    public LogAnalysisResult analyzeByTraceId(String traceId) {
        List<LogRecord> chain = logRecordRepository.findByTraceId(traceId);
        if (chain.isEmpty()) {
            return LogAnalysisResult.builder()
                    .recommendation(SolutionRecommendation.builder()
                            .hasKnownSolution(false)
                            .aiSuggestion("未找到该 traceId 的调用链记录")
                            .build())
                    .build();
        }

        // 找出链中所有错误
        List<LogRecord> errors = chain.stream()
                .filter(l -> "ERROR".equalsIgnoreCase(l.getLevel()))
                .toList();

        // 构建上下文
        List<SimilarError> similarErrors = chain.stream()
                .map(lr -> SimilarError.builder()
                        .logId(lr.getId())
                        .module(lr.getModule())
                        .exceptionType(lr.getExceptionType())
                        .message(lr.getMessage())
                        .logTime(lr.getLogTime() != null ? lr.getLogTime().format(DT_FMT) : null)
                        .traceId(lr.getTraceId())
                        .build())
                .toList();

        // 对每个错误查找解决方案
        String primaryFingerprint = !errors.isEmpty() ? errors.get(0).getErrorFingerprint() : null;
        SolutionRecommendation recommendation = SolutionRecommendation.builder()
                .hasKnownSolution(false)
                .aiSuggestion(buildContextAnalysisSuggestion(chain, errors))
                .relatedTraceIds(List.of(traceId))
                .build();

        if (primaryFingerprint != null) {
            errorSolutionRepository.findByFingerprint(primaryFingerprint)
                    .ifPresent(es -> {
                        recommendation.setHasKnownSolution(true);
                        recommendation.setRootCause(es.getRootCause());
                        recommendation.setSolution(es.getSolution());
                        recommendation.setReferenceUrl(es.getReferenceUrl());
                    });
        }

        return LogAnalysisResult.builder()
                .targetFingerprint(primaryFingerprint)
                .similarCount(chain.size())
                .similarErrors(similarErrors)
                .recommendation(recommendation)
                .build();
    }

    // ======================== 全文搜索相似错误 ========================

    /**
     * 基于异常类型 + 关键词, 全文检索相似错误
     */
    public List<SimilarError> searchSimilarErrors(String exceptionType, String keyword, int limit) {
        // 利用已持久化的错误指纹, 先找同类型的
        List<LogRecord> candidates = new ArrayList<>();
        if (exceptionType != null && !exceptionType.isEmpty()) {
            // 通过 repository 查询同类型的最近错误
            List<LogRecord> all = logRecordRepository.findByErrorFingerprint(
                    LogCollectService.computeErrorFingerprint(exceptionType, ""), limit);
            candidates.addAll(all);
        }

        // 按关键词二次过滤
        if (keyword != null && !keyword.isEmpty()) {
            candidates = candidates.stream()
                    .filter(lr -> lr.getMessage() != null
                            && lr.getMessage().contains(keyword))
                    .collect(Collectors.toList());
        }

        return candidates.stream()
                .map(lr -> SimilarError.builder()
                        .logId(lr.getId())
                        .module(lr.getModule())
                        .exceptionType(lr.getExceptionType())
                        .message(lr.getMessage())
                        .logTime(lr.getLogTime() != null ? lr.getLogTime().format(DT_FMT) : null)
                        .traceId(lr.getTraceId())
                        .similarity(computeSimilarity(lr))
                        .build())
                .sorted((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()))
                .limit(limit)
                .toList();
    }

    // ======================== AI 分析建议方法 (模拟LLM分析逻辑) ========================

    /**
     * 有已知方案时的 AI 补充分析
     */
    private String generateAiSuggestion(LogRecord sample, ErrorSolution solution) {
        StringBuilder sb = new StringBuilder();
        sb.append("【AI 分析总结】\n");
        sb.append("✅ 该错误已有对应的解决方案。\n\n");
        sb.append("错误类型: ").append(sample.getExceptionType()).append("\n");
        sb.append("来源模块: ").append(sample.getModule()).append("\n");
        sb.append("解决方案已沉淀在知识库中, 建议按步骤执行。\n");

        if (sample.getExceptionStack() != null) {
            sb.append("\n【建议排查方向】\n");
            analyzeExceptionDirection(sample, sb);
        }

        return sb.toString();
    }

    /**
     * 无已知方案时的 AI 智能分析
     */
    private String generateAiSuggestionWithoutSolution(List<LogRecord> similarLogs) {
        StringBuilder sb = new StringBuilder();
        sb.append("【AI 智能分析】\n");
        sb.append("⚠️ 该错误暂无已知解决方案, 以下为自动分析结果:\n\n");

        if (similarLogs.isEmpty()) return sb.toString();

        LogRecord sample = similarLogs.get(0);

        // 1. 错误类型分析
        sb.append("▎错误模式: ").append(sample.getExceptionType()).append("\n");
        String exceptionClass = sample.getExceptionType();
        if (exceptionClass != null) {
            if (exceptionClass.contains("NullPointerException")) {
                sb.append("▎分析: 存在空指针异常, 建议检查相关对象是否为 null, 添加防御性判空。\n");
            } else if (exceptionClass.contains("TimeoutException") || exceptionClass.contains("Timeout")) {
                sb.append("▎分析: 请求超时, 建议检查下游服务可用性, 适当调整超时时间配置。\n");
            } else if (exceptionClass.contains("OutOfMemoryError")) {
                sb.append("▎分析: 内存溢出, 建议检查是否存在内存泄漏, 分析堆内存使用情况。\n");
            } else if (exceptionClass.contains("SQLException") || exceptionClass.contains("DataAccessException")) {
                sb.append("▎分析: 数据库异常, 建议检查SQL语句、连接池配置、数据库负载。\n");
            } else if (exceptionClass.contains("ConnectException")) {
                sb.append("▎分析: 连接异常, 建议检查网络连通性和目标服务端口。\n");
            } else if (exceptionClass.contains("UnauthorizedException") || exceptionClass.contains("AccessDenied")) {
                sb.append("▎分析: 认证/鉴权异常, 建议检查Token有效性、用户权限配置。\n");
            }
        }

        // 2. 频率分析
        sb.append("\n▎发生频率: 该指纹下已有 ").append(similarLogs.size()).append(" 条记录\n");
        if (similarLogs.size() >= 5) {
            sb.append("⚠️ 该错误已反复出现, 建议优先排查!\n");
        }

        // 3. 模块分布
        Set<String> modules = similarLogs.stream()
                .map(LogRecord::getModule)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        sb.append("▎影响模块: ").append(String.join(", ", modules)).append("\n");

        // 4. 推荐操作
        sb.append("\n【建议操作】\n");
        sb.append("1. 点击相关 traceId 还原完整调用链上下文\n");
        sb.append("2. 检查模块 ").append(sample.getModule()).append(" 的详细日志\n");
        sb.append("3. 排查完成后可通过 API 录入解决方案, 沉淀到知识库\n");
        sb.append("4. 如需更深入分析, 可接入 LLM API 进行智能诊断\n");

        if (sample.getExceptionStack() != null) {
            sb.append("\n【堆栈快速分析】\n");
            analyzeExceptionDirection(sample, sb);
        }

        return sb.toString();
    }

    /**
     * 构建调用链上下文分析建议
     */
    private String buildContextAnalysisSuggestion(List<LogRecord> chain,
                                                    List<LogRecord> errors) {
        StringBuilder sb = new StringBuilder();
        sb.append("【调用链上下文分析】\n");
        sb.append("链中共 ").append(chain.size()).append(" 条日志, ");
        sb.append("其中错误 ").append(errors.size()).append(" 处\n\n");

        sb.append("▎调用链路:\n");
        for (LogRecord lr : chain) {
            String icon = "ERROR".equalsIgnoreCase(lr.getLevel()) ? "❌" : "  ➜";
            sb.append("  ").append(icon).append(" [").append(lr.getModule()).append("] ")
                    .append(lr.getClassName()).append(".")
                    .append(lr.getMethodName())
                    .append(" - ").append(lr.getLevel());

            if (lr.getCostTime() != null) {
                sb.append(" (").append(lr.getCostTime()).append("ms)");
            }
            if (lr.getMessage() != null) {
                String msg = lr.getMessage().length() > 80
                        ? lr.getMessage().substring(0, 80) + "..."
                        : lr.getMessage();
                sb.append(" -> ").append(msg);
            }
            sb.append("\n");
        }

        if (!errors.isEmpty()) {
            sb.append("\n▎错误源于: ").append(errors.get(0).getModule())
                    .append(" / ").append(errors.get(0).getExceptionType()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 根据异常堆栈推荐排查方向
     */
    private void analyzeExceptionDirection(LogRecord sample, StringBuilder sb) {
        String stack = sample.getExceptionStack();
        if (stack == null) return;

        if (stack.contains("com.liang")) {
            sb.append("  ➜ 异常发生在业务代码层, 建议检查对应模块的业务逻辑\n");
        }
        if (stack.contains("org.springframework.security")) {
            sb.append("  ➜ 涉及 Spring Security, 建议检查认证配置/权限规则\n");
        }
        if (stack.contains("java.sql") || stack.contains("org.springframework.jdbc")) {
            sb.append("  ➜ 数据库访问层异常, 建议检查 SQL 语法、数据源配置、连接池状态\n");
        }
        if (stack.contains("redis") || stack.contains("Redis")) {
            sb.append("  ➜ Redis 相关异常, 建议检查 Redis 连接、内存、超时配置\n");
        }
        if (stack.contains("Connection refused") || stack.contains("connect timed out")) {
            sb.append("  ➜ 网络连接被拒绝/超时, 建议检查目标服务是否存活\n");
        }
        if (stack.contains("OutOfMemoryError") || stack.contains("GC overhead")) {
            sb.append("  ➜ 内存问题, 建议使用 jmap/jstack 分析内存和线程状态\n");
        }
    }

    /**
     * 推断根因 (无已知方案时的自动推断)
     */
    private String inferRootCause(List<LogRecord> similarLogs) {
        if (similarLogs.isEmpty()) return "无法推断";
        LogRecord sample = similarLogs.get(0);
        StringBuilder cause = new StringBuilder("[自动推断] ");

        if (sample.getExceptionType() != null) {
            cause.append("异常类型: ").append(sample.getExceptionType()).append("。");
        }
        if (sample.getModule() != null) {
            cause.append("发生在模块: ").append(sample.getModule()).append("。");
        }
        if (similarLogs.size() > 1) {
            cause.append("该异常已出现 ").append(similarLogs.size()).append(" 次, 可能为重复性问题。");
        }

        return cause.toString();
    }

    // ======================== 辅助方法 ========================

    private String extractTopStack(String stack) {
        if (stack == null || stack.isEmpty()) return null;
        String[] lines = stack.split("\n");
        int count = 0;
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (count >= 3) break;
            sb.append(line.trim()).append("\n");
            count++;
        }
        return sb.toString().trim();
    }

    private double computeSimilarity(LogRecord logRecord) {
        // 基于错误指纹的匹配度 (指纹相同 = 1.0, 说明完全匹配)
        if (logRecord.getErrorFingerprint() != null) {
            return 1.0;
        }
        // 仅有异常类型相同 = 0.5
        if (logRecord.getExceptionType() != null) {
            return 0.5;
        }
        return 0.3;
    }

    private List<TimeBucket> computeTimeDistribution(List<LogRecord> similarLogs) {
        // 按小时统计
        Map<String, Long> hourMap = new LinkedHashMap<>();
        DateTimeFormatter hourFmt = DateTimeFormatter.ofPattern("MM-dd HH:00");
        for (LogRecord lr : similarLogs) {
            if (lr.getLogTime() != null) {
                String key = lr.getLogTime().format(hourFmt);
                hourMap.merge(key, 1L, Long::sum);
            }
        }
        return hourMap.entrySet().stream()
                .map(e -> TimeBucket.builder().label(e.getKey()).count(e.getValue()).build())
                .toList();
    }
}
