package com.liang.xz.aiagent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>分析预警 VO</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertVO {
    private Long id;
    private String alertName;
    private String analysisType;
    private String dataSource;
    private String alertLevel;
    private String alertContent;
    private String analysisDetail;
    private String suggestion;
    private Boolean isRead;
    private Boolean resolved;
    private LocalDateTime resolvedAt;
    private String resolvedBy;
    private LocalDateTime createdAt;
}
