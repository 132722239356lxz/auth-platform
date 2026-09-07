package com.liang.xz.flow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * <p>按表单提交审批申请请求</p>
 *
 * <p>用户在前端选择表单后，根据表单 schema 填写字段，提交时只需传表单标识和字段 JSON。
 * 后端根据 {@code formKey} 找到绑定的流程定义并发起审批。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Schema(description = "按表单提交审批申请请求")
public class WorkflowSubmitByFormRequest {

    @NotBlank(message = "表单Key不能为空")
    @Schema(description = "表单唯一标识", example = "SUBSYSTEM_VISIBILITY")
    private String formKey;

    @NotBlank(message = "申请标题不能为空")
    @Schema(description = "申请标题", example = "liangxz 的子系统可见权限申请")
    private String title;

    @Schema(description = "申请内容(JSON，由表单字段值组成)", example = "{\"title\":\"...\",\"reason\":\"...\"}")
    private String applyContent;

    @Schema(description = "申请人用户名，前端传入；未传时尝试从请求头 X-User 读取", example = "zhangsan")
    private String applicant;
}
