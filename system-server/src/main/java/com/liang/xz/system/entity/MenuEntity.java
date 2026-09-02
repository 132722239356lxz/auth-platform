package com.liang.xz.system.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>菜单实体 —— 映射 sys_menu 表</p>
 *
 * <p>支持多级菜单树结构，通过 parentId 关联父级</p>
 * <p>菜单类型:</p>
 * <ul>
 *   <li>0=目录 (一级菜单分组)</li>
 *   <li>1=菜单 (页面路由)</li>
 *   <li>2=按钮 (页面内操作权限)</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuEntity {

    @Schema(description = "菜单主键(自增)")
    private Long id;

    @Schema(description = "父菜单ID (0=顶级菜单)")
    private Long parentId;

    @Schema(description = "菜单名称 (如 用户管理)")
    private String menuName;

    @Schema(description = "菜单类型: 0=目录 1=菜单 2=按钮")
    private Integer menuType;

    @Schema(description = "路由路径 (如 /system/user)")
    private String path;

    @Schema(description = "组件路径 (如 system/user/index)")
    private String component;

    @Schema(description = "权限标识 (如 system:user:list, 按钮类型必填)")
    private String permission;

    @Schema(description = "菜单图标")
    private String icon;

    @Schema(description = "排序号")
    private Integer sortOrder;

    @Schema(description = "状态: true=启用, false=禁用")
    private Boolean enabled;

    @Schema(description = "是否外链: true=是, false=否")
    private Boolean isFrame;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
