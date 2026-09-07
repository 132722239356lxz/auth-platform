package com.liang.xz.system.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <p>角色-菜单关联实体 —— 映射 sys_role_menu 表</p>
 *
 * <p>N:N关联表，实现角色与菜单(含按钮权限)的绑定</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleMenuEntity {

    @Schema(description = "主键(自增)")
    private Long id;

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "菜单ID(含按钮)")
    private Long menuId;
}
