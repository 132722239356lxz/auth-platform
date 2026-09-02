package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * <p>部门新增/编辑请求</p>
 *
 * @author auth-platform
 * @since 1.2.0
 */
@Data
@Schema(description = "部门请求")
public class DeptRequest {

    @NotBlank(message = "部门名称不能为空")
    @Schema(description = "部门名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deptName;

    @NotBlank(message = "部门编码不能为空")
    @Schema(description = "部门编码(唯一)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deptCode;

    @Schema(description = "父部门ID(0=顶级)")
    private Long parentId = 0L;

    @Schema(description = "负责人")
    private String leader;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "排序号")
    private Integer sortOrder = 0;

    @Schema(description = "状态: true=启用 false=禁用")
    private Boolean enabled = true;
}
