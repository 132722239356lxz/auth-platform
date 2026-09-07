package com.liang.xz.flow.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>工作流定义实体 —— 映射 wf_definition 表</p>
 * <p>定义一种审批流程模板</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinition {

    @Schema(description = "主键(自增)")
    private Long id;

    @Schema(description = "流程定义Key, 如: PERMISSION_APPROVAL")
    private String definitionKey;

    @Schema(description = "流程名称")
    private String definitionName;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "分类: PERMISSION(权限申请)/RESOURCE(资源申请)/ROLE(角色申请)")
    private String category;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "状态: 0-草稿 1-启用 2-停用")
    private Integer status;

    @Schema(description = "创建人")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
