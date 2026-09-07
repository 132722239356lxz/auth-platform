package com.liang.xz.message.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>子系统异常反馈实体 —— 映射 subsystem_incident 表</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubsystemIncident {

    @Schema(description = "主键(自增)")
    private Long id;

    @Schema(description = "子系统标识(client_id)")
    private String subsystem;

    @Schema(description = "子系统名称")
    private String subsystemName;

    @Schema(description = "异常类型: ERROR/HEALTH/SECURITY")
    private String incidentType;

    @Schema(description = "级别: ERROR/WARN/CRITICAL")
    private String level;

    @Schema(description = "异常标题")
    private String title;

    @Schema(description = "异常描述")
    private String content;

    @Schema(description = "异常堆栈")
    private String stackTrace;

    @Schema(description = "关联日志traceId")
    private String traceId;

    @Schema(description = "状态: PENDING/RESOLVED/IGNORED")
    private String status;

    @Schema(description = "上报时间")
    private LocalDateTime reportedAt;

    @Schema(description = "处理时间")
    private LocalDateTime resolvedAt;

    @Schema(description = "处理人")
    private String resolver;

    @Schema(description = "处理说明")
    private String resolveNote;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
