package com.liang.xz.aiagent.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>分析预警记录实体 —— 映射 ai_analysis_alert 表</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisAlert {

    @Schema(description = "主键(自增)")
    private Long id;

    @Schema(description = "预警名称")
    private String alertName;

    @Schema(description = "分析类型: TREND/ANOMALY/THRESHOLD/PREDICTION/INSIGHT")
    private String analysisType;

    @Schema(description = "数据来源")
    private String dataSource;

    @Schema(description = "预警级别: INFO/WARN/CRITICAL")
    private String alertLevel;

    @Schema(description = "预警内容")
    private String alertContent;

    @Schema(description = "分析详情")
    private String analysisDetail;

    @Schema(description = "改进建议")
    private String suggestion;

    @Schema(description = "指标数据(JSON)")
    private String metricsJson;

    @Schema(description = "是否已读")
    private Boolean isRead;

    @Schema(description = "是否已解决")
    private Boolean resolved;

    @Schema(description = "解决时间")
    private LocalDateTime resolvedAt;

    @Schema(description = "解决人")
    private String resolvedBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
