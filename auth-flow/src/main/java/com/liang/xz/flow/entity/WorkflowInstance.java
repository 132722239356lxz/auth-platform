package com.liang.xz.flow.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>工作流实例实体 —— 映射 wf_instance 表</p>
 * <p>一次具体的审批流程运行记录</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowInstance {

    @Schema(description = "主键(自增)")
    private Long id;

    @Schema(description = "关联的流程定义ID")
    private Long definitionId;

    @Schema(description = "流程标题")
    private String title;

    @Schema(description = "申请人")
    private String applicant;

    @Schema(description = "申请内容(JSON)")
    private String applyContent;

    @Schema(description = "状态: PENDING(审批中)/APPROVED(已通过)/REJECTED(已驳回)/WITHDRAWN(已撤回)")
    private String status;

    @Schema(description = "当前节点ID")
    private Long currentNodeId;

    @Schema(description = "当前审批人")
    private String currentApprover;

    @Schema(description = "审批链(节点ID列表, 逗号分隔)")
    private String approvalChain;

    @Schema(description = "审批记录(JSON数组)")
    private String approvalRecords;

    @Schema(description = "创建人")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "完成时间")
    private LocalDateTime finishTime;
}
