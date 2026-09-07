package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * <p>用户分配角色请求</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Schema(description = "用户分配角色请求")
public class UserAssignRoleRequest {

    @NotNull(message = "角色ID列表不能为空")
    @Schema(description = "角色ID列表", example = "[1, 2]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> roleIds;
}
