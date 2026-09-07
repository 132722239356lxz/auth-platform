package com.liang.xz.flow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流详情VO(支持串行/并行/条件分支展示)
 */
@Data
@Builder
@Schema(description = "工作流详情")
public class WorkflowVO {

    @Schema(description = "流程定义")
    private DefinitionVO definition;

    @Schema(description = "流程实例")
    private InstanceVO instance;

    @Schema(description = "审批节点列表")
    private List<NodeVO> nodes;

    @Schema(description = "审批任务列表")
    private List<TaskVO> tasks;

    @Data
    @Builder
    @Schema(description = "流程定义")
    public static class DefinitionVO {
        private Long id;
        private String definitionKey;
        private String definitionName;
        private String category;
        private Integer status;
        private LocalDateTime createTime;
    }

    @Data
    @Builder
    @Schema(description = "流程实例")
    public static class InstanceVO {
        private Long id;
        private String title;
        private String applicant;
        private String applyContent;
        private String status;
        private String currentApprover;
        private String currentNodeName;
        private LocalDateTime createTime;
        private LocalDateTime finishTime;
    }

    @Data
    @Builder
    @Schema(description = "审批节点")
    public static class NodeVO {
        private Long id;
        private String nodeName;
        private String nodeType;
        private String execMode;
        private String parallelGroup;
        private String conditionExpression;
        private String onConditionFail;
        private String approvers;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @Schema(description = "审批任务")
    public static class TaskVO {
        private Long id;
        private String nodeName;
        private String approver;
        private String status;
        private String comment;
        private LocalDateTime createTime;
        private LocalDateTime approveTime;
    }

    @Data
    @Builder
    @Schema(description = "我的审批记录")
    public static class ApprovalRecordVO {
        private Long taskId;
        private Long instanceId;
        private String title;
        private String applicant;
        private String nodeName;
        private String action;
        private String comment;
        private LocalDateTime approveTime;
        private String instanceStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "流程定义详情(含节点)")
    public static class DefinitionDetailVO {
        private Long id;
        private String definitionKey;
        private String definitionName;
        private String description;
        private String category;
        private Integer version;
        private Integer status;
        private LocalDateTime createTime;
        private List<NodeVO> nodes;
    }
}
