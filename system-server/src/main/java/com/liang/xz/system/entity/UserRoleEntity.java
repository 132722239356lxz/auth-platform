package com.liang.xz.system.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <p>用户-角色关联实体 —— 映射 sys_user_role 表</p>
 *
 * <p>N:N关联表，实现用户与角色的绑定</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleEntity {

    @Schema(description = "主键(自增)")
    private Long id;

    @Schema(description = "用户ID (关联 sys_user.id)")
    private Long userId;

    @Schema(description = "角色ID (关联 sys_role.id)")
    private Long roleId;
}
