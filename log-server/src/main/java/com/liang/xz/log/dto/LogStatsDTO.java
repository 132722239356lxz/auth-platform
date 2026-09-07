package com.liang.xz.log.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 日志统计概览
 *
 * @author liang
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogStatsDTO {

    /** 时间段内总日志数 */
    private Long totalCount;

    /** 各级别分布 */
    private Map<String, Long> levelDistribution;

    /** 各模块分布 */
    private Map<String, Long> moduleDistribution;

    /** 各分类分布 */
    private Map<String, Long> categoryDistribution;

    /** 错误数(ERROR级别) */
    private Long errorCount;

    /** 错误率 */
    private Double errorRate;

    /** TOP错误指纹(top5) */
    private List<ErrorFingerprintTop> topErrorFingerprints;

    /** 平均耗时(ms) */
    private Double avgCostTime;

    /** P99耗时(ms) */
    private Long p99CostTime;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorFingerprintTop {
        private String fingerprint;
        private String exceptionType;
        private String summary;
        private Long count;
        private String lastOccurTime;
        private Boolean hasSolution;
    }
}
