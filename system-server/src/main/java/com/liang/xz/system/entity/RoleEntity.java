package com.liang.xz.system.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>角色实体 —— 映射 sys_role 表</p>
 *
 * <p>RBAC模型中的角色层，用于权限聚合分配</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleEntity {

    @Schema(description = "角色主键(自增)")
    private Long id;

    @Schema(description = "角色编码(唯一, 如 ROLE_ADMIN)")
    private String roleCode;

    @Schema(description = "角色名称(如 系统管理员)")
    private String roleName;

    @Schema(description = "角色描述")
    private String description;

    @Schema(description = "排序号")
    private Integer sortOrder;

    @Schema(description = "状态: true=启用, false=禁用")
    private Boolean enabled;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
