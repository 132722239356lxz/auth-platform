package com.liang.xz.flow.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>审批任务实体 —— 映射 wf_task 表</p>
 * <p>每个节点生成的待审批任务</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowTask {

    @Schema(description = "主键(自增)")
    private Long id;

    @Schema(description = "关联的流程实例ID")
    private Long instanceId;

    @Schema(description = "关联的节点ID")
    private Long nodeId;

    @Schema(description = "节点名称(冗余)")
    private String nodeName;

    @Schema(description = "待审批人")
    private String approver;

    @Schema(description = "状态: PENDING(待审批)/APPROVED(已通过)/REJECTED(已驳回)/TRANSFERRED(已转交)")
    private String status;

    @Schema(description = "审批意见")
    private String comment;

    @Schema(description = "审批时间")
    private LocalDateTime approveTime;

    @Schema(description = "转交来源人(当被转交时记录)")
    private String transferredFrom;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
