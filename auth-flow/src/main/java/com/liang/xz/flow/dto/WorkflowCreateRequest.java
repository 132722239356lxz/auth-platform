package com.liang.xz.flow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 创建工作流请求(支持串行/并行/条件节点)
 */
@Data
@Schema(description = "创建工作流定义请求")
public class WorkflowCreateRequest {

    @NotBlank(message = "流程Key不能为空")
    @Schema(description = "流程定义Key", example = "PERMISSION_APPROVAL")
    private String definitionKey;

    @NotBlank(message = "流程名称不能为空")
    @Schema(description = "流程名称", example = "权限申请审批")
    private String definitionName;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "分类: PERMISSION/RESOURCE/ROLE", example = "PERMISSION")
    private String category;

    @NotEmpty(message = "审批节点不能为空")
    @Schema(description = "审批节点列表")
    private List<NodeRequest> nodes;

    /**
     * 节点定义 —— 支持串行审批/并行审批/条件分支
     */
    @Data
    @Schema(description = "节点定义")
    public static class NodeRequest {

        @Schema(description = "节点名称", example = "部门经理审批")
        private String nodeName;

        @Schema(description = "节点类型: START/APPROVAL/CONDITION/PARALLEL_START/PARALLEL_END/CALLBACK/END",
                example = "APPROVAL")
        private String nodeType;

        @Schema(description = "执行模式: SERIAL(串行)/PARALLEL(并行)", example = "SERIAL")
        private String execMode;

        @Schema(description = "并行分组标识(同组并行节点同时审批, execMode=PARALLEL时填写)", example = "group1")
        private String parallelGroup;

        @Schema(description = "父节点sortOrder(并行子节点指向 PARALLEL_START 的 sortOrder)")
        private Long parentNodeId;

        @Schema(description = "条件表达式(SpEL, nodeType=CONDITION必填)",
                example = "#applyContent['level'] >= 3")
        private String conditionExpression;

        @Schema(description = "条件不满足时: REJECT(驳回结束)/SKIP(跳过继续)", example = "REJECT")
        private String onConditionFail;

        @Schema(description = "审批人策略: SPECIFIC/ROLE_BASED/DEPARTMENT_LEADER", example = "SPECIFIC")
        private String approverStrategy;

        @Schema(description = "指定审批人, 多个逗号分隔")
        private String approvers;

        @Schema(description = "审批角色", example = "ROLE_MANAGER")
        private String approverRole;

        @Schema(description = "排序号")
        private Integer sortOrder;

        @Schema(description = "超时时间(小时)")
        private Integer timeoutHours;

        @Schema(description = "会签模式: true=全部通过才流转")
        private Boolean countersign;

        @Schema(description = "驳回策略: TO_PREV(驳回到上一节点)/TO_START(驳回到发起人)", example = "TO_PREV")
        private String rejectStrategy;
    }
}
