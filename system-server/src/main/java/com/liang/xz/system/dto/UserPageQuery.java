package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>用户分页查询条件</p>
 *
 * <p>统一封装用户列表的多条件筛选参数，支持动态 SQL 组装</p>
 *
 * @author auth-platform
 * @since 1.2.0
 */
@Data
@Schema(description = "用户分页查询条件")
public class UserPageQuery {

    @Schema(description = "搜索关键词(用户名/昵称/邮箱)")
    private String keyword;

    @Schema(description = "状态: true=启用 false=禁用")
    private Boolean enabled;

    @Schema(description = "用户类型: admin/user/service")
    private String userType;

    @Schema(description = "部门ID")
    private Long deptId;

    @Schema(description = "角色ID（按角色筛选用户）")
    private Long roleId;

    @Schema(description = "页码,从1开始", example = "1")
    private int page = 1;

    @Schema(description = "每页条数", example = "10")
    private int pageSize = 10;
}
