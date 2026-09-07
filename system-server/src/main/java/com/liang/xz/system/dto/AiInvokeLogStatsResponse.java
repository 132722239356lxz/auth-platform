package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>AI 调用日志聚合统计响应</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Schema(description = "AI 调用日志统计")
public class AiInvokeLogStatsResponse {

    @Schema(description = "总调用次数")
    private Long total;

    @Schema(description = "成功次数")
    private Long success;

    @Schema(description = "缓存命中次数")
    private Long cacheHit;

    @Schema(description = "总 token 消耗")
    private Long totalTokens;

    @Schema(description = "平均耗时(毫秒)")
    private Double avgElapsedMs;

    @Schema(description = "缓存命中率(0~1)")
    private Double cacheHitRate;

    @Schema(description = "成功率(0~1)")
    private Double successRate;
}
