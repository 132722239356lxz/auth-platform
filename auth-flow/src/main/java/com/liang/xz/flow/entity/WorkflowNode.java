package com.liang.xz.flow.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>工作流节点实体 —— 映射 wf_node 表</p>
 * <p>支持串行/并行/条件分支</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNode {

    @Schema(description = "主键(自增)")
    private Long id;

    @Schema(description = "所属流程定义ID")
    private Long definitionId;

    @Schema(description = "节点名称")
    private String nodeName;

    @Schema(description = "节点类型: START/APPROVAL/CONDITION/PARALLEL_START/PARALLEL_END/CALLBACK/END")
    private String nodeType;

    @Schema(description = "执行模式: SERIAL(串行)/PARALLEL(并行)")
    private String execMode;

    @Schema(description = "并行分组标识(同一group的PARALLEL节点同时审批)")
    private String parallelGroup;

    @Schema(description = "父节点ID(并行节点所属的PARALLEL_START节点ID)")
    private Long parentNodeId;

    @Schema(description = "条件表达式(SpEL格式, nodeType=CONDITION时必填)")
    private String conditionExpression;

    @Schema(description = "条件不满足时的操作: REJECT(驳回结束)/SKIP(跳过此节点继续)")
    private String onConditionFail;

    @Schema(description = "审批人策略")
    private String approverStrategy;

    @Schema(description = "审批人(多个逗号分隔)")
    private String approvers;

    @Schema(description = "审批角色")
    private String approverRole;

    @Schema(description = "排序号")
    private Integer sortOrder;

    @Schema(description = "超时时间(小时)")
    private Integer timeoutHours;

    @Schema(description = "会签模式")
    private Boolean countersign;

    @Schema(description = "驳回策略: TO_PREV/TO_START")
    private String rejectStrategy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    // 便捷方法
    public boolean isParallel() {
        return "PARALLEL".equalsIgnoreCase(execMode);
    }

    public boolean isCondition() {
        return "CONDITION".equalsIgnoreCase(nodeType);
    }

    public boolean isParallelStart() {
        return "PARALLEL_START".equalsIgnoreCase(nodeType);
    }

    public boolean isParallelEnd() {
        return "PARALLEL_END".equalsIgnoreCase(nodeType);
    }

    public boolean isApproval() {
        return "APPROVAL".equalsIgnoreCase(nodeType);
    }
}
