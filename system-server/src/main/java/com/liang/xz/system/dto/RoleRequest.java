package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * <p>角色创建/更新请求</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Schema(description = "角色请求")
public class RoleRequest {

    @NotBlank(message = "角色编码不能为空")
    @Size(min = 2, max = 50, message = "角色编码长度需在2-50字符之间")
    @Schema(description = "角色编码(唯一)", example = "ROLE_ADMIN", requiredMode = Schema.RequiredMode.REQUIRED)
    private String roleCode;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 50, message = "角色名称长度不能超过50字符")
    @Schema(description = "角色名称", example = "系统管理员", requiredMode = Schema.RequiredMode.REQUIRED)
    private String roleName;

    @Schema(description = "角色描述", example = "拥有系统所有权限")
    private String description;

    @Schema(description = "排序号", example = "1")
    private Integer sortOrder;

    @Schema(description = "关联的菜单ID列表")
    private List<Long> menuIds;
}
