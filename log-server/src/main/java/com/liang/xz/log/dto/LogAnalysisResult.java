package com.liang.xz.log.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 日志分析结果
 *
 * @author liang
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogAnalysisResult {

    /** 目标错误指纹 */
    private String targetFingerprint;

    /** 异常类型 */
    private String exceptionType;

    /** 异常摘要 */
    private String exceptionSummary;

    /** 相似错误数量 */
    private Integer similarCount;

    /** 相似错误列表(含上下文) */
    private List<SimilarError> similarErrors;

    /** 时间分布(错误发生趋势) */
    private List<TimeBucket> timeDistribution;

    /** 推荐解决方案 */
    private SolutionRecommendation recommendation;

    /** ──────── 内嵌类 ──────── */

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimilarError {
        /** 日志ID */
        private Long logId;
        /** 来源模块 */
        private String module;
        /** 异常类型 */
        private String exceptionType;
        /** 错误消息 */
        private String message;
        /** 堆栈截断(前3层) */
        private String stackTrace;
        /** 发生时间 */
        private String logTime;
        /** 链路追踪ID */
        private String traceId;
        /** 相似度(0-1) */
        private Double similarity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeBucket {
        /** 时间段标签 */
        private String label;
        /** 错误数 */
        private Long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SolutionRecommendation {
        /** 是否有已知方案 */
        private Boolean hasKnownSolution;
        /** 根因分析 */
        private String rootCause;
        /** 解决方案 */
        private String solution;
        /** 详细步骤 */
        private List<String> steps;
        /** 参考链接 */
        private String referenceUrl;
        /** AI 分析建议 */
        private String aiSuggestion;
        /** 相关日志的 traceId 列表(用于上下文串联) */
        private List<String> relatedTraceIds;
    }
}
