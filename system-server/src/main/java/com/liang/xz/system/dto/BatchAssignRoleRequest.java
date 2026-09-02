package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * <p>批量分配角色请求</p>
 *
 * <p>支持将多个用户批量分配到指定角色，实现权限下发</p>
 *
 * @author auth-platform
 * @since 2.1.0
 */
@Data
@Schema(description = "批量分配角色请求")
public class BatchAssignRoleRequest {

    @NotNull(message = "用户ID列表不能为空")
    @NotEmpty(message = "至少选择一个用户")
    @Schema(description = "目标用户ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> userIds;

    @NotNull(message = "角色ID列表不能为空")
    @NotEmpty(message = "至少选择一个角色")
    @Schema(description = "要分配的角色ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> roleIds;
}
