package com.liang.xz.flow.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>审批表单定义</p>
 *
 * <p>一个表单绑定一个流程定义({@code definitionKey})，通过 {@code schemaJson}
 * 描述前端需要渲染的字段。新增审批场景只需新增表单定义与对应流程定义，
 * 无需改动前端代码。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowForm {

    private Long id;

    /**
     * 表单唯一标识，如 SUBSYSTEM_VISIBILITY。
     */
    private String formKey;

    /**
     * 表单展示名称。
     */
    private String formName;

    /**
     * 绑定的流程定义Key。
     */
    private String definitionKey;

    /**
     * 表单字段Schema，JSON数组。
     */
    private String schemaJson;

    /**
     * 图标标识。
     */
    private String icon;

    /**
     * 排序号。
     */
    private Integer sortOrder;

    /**
     * 状态：0-停用 1-启用。
     */
    private Integer status;

    /**
     * 业务场景类型，用于审批通过后路由处理器（如 SUBSYSTEM_VISIBILITY / ROLE）。
     */
    private String applyType;

    /**
     * 创建人。
     */
    private String createdBy;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;
}
