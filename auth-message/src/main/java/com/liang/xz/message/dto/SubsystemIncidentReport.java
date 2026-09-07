package com.liang.xz.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * <p>子系统异常上报请求</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Schema(description = "子系统异常上报请求")
public class SubsystemIncidentReport {

    @NotBlank(message = "子系统标识不能为空")
    @Schema(description = "子系统标识(client_id), 如 subsystem-a", example = "subsystem-a")
    private String subsystem;

    @Schema(description = "子系统名称", example = "子系统A-业务系统")
    private String subsystemName;

    @Schema(description = "异常类型: ERROR/HEALTH/SECURITY", example = "ERROR")
    private String incidentType;

    @Schema(description = "级别: ERROR/WARN/CRITICAL", example = "CRITICAL")
    private String level;

    @NotBlank(message = "标题不能为空")
    @Schema(description = "异常标题", example = "订单服务数据库连接失败")
    private String title;

    @Schema(description = "异常描述")
    private String content;

    @Schema(description = "异常堆栈")
    private String stackTrace;

    @Schema(description = "关联日志traceId(可到log-server按traceId追踪完整日志)")
    private String traceId;
}
