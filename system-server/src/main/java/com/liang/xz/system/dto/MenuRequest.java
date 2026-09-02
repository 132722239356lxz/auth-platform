package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * <p>菜单创建/更新请求</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Schema(description = "菜单请求")
public class MenuRequest {

    @Schema(description = "父菜单ID (0=顶级菜单)", example = "0")
    private Long parentId = 0L;

    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 50, message = "菜单名称长度不能超过50字符")
    @Schema(description = "菜单名称", example = "用户管理", requiredMode = Schema.RequiredMode.REQUIRED)
    private String menuName;

    @NotNull(message = "菜单类型不能为空")
    @Schema(description = "菜单类型: 0=目录 1=菜单 2=按钮", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer menuType;

    @Schema(description = "路由路径", example = "/system/user")
    private String path;

    @Schema(description = "组件路径", example = "system/user/index")
    private String component;

    @Schema(description = "权限标识(按钮类型必填)", example = "system:user:list")
    private String permission;

    @Schema(description = "菜单图标", example = "user")
    private String icon;

    @Schema(description = "排序号", example = "1")
    private Integer sortOrder;

    @Schema(description = "是否外链", example = "false")
    private Boolean isFrame = false;

    @Schema(description = "创建后自动分配给哪些角色(角色ID列表)，为空则不自动分配", example = "[1, 2]")
    private List<Long> autoAssignRoleIds;
}
