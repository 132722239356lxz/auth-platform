package com.liang.xz.flow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 审批操作请求
 */
@Data
@Schema(description = "审批操作请求")
public class ApprovalRequest {

    @NotNull(message = "任务ID不能为空")
    @Schema(description = "审批任务ID")
    private Long taskId;

    @NotBlank(message = "审批动作不能为空")
    @Schema(description = "动作: APPROVE/REJECT/TRANSFER", example = "APPROVE")
    private String action;

    @Schema(description = "审批意见")
    private String comment;

    @Schema(description = "转交目标人(action=TRANSFER时必填)")
    private String transferTo;
}
