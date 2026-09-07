package com.liang.xz.system.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>菜单树查询条件</p>
 *
 * <p>统一封装菜单树的多条件筛选参数，后端进行过滤后返回</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Schema(description = "菜单树查询条件")
public class MenuTreeQuery {

    @Schema(description = "搜索关键词（菜单名称/权限标识）")
    private String keyword;

    @Schema(description = "菜单类型: 1=目录 2=菜单 3=按钮")
    private Integer menuType;

    @Schema(description = "状态: true=启用 false=禁用")
    private Boolean enabled;
}
