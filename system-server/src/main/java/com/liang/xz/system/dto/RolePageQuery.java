package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 角色分页查询参数 —— 统一封装多条件筛选
 *
 * @author auth-platform
 * @since 2.0.0
 */
@Data
@Schema(description = "角色分页查询参数")
public class RolePageQuery {

    @Parameter(description = "搜索关键词(角色编码/名称)")
    private String keyword;

    @Parameter(description = "启用状态")
    private Boolean enabled;

    @Min(value = 1, message = "页码从1开始")
    @Parameter(description = "页码,从1开始")
    private Integer page = 1;

    @Min(value = 1, message = "每页至少1条")
    @Parameter(description = "每页条数")
    private Integer pageSize = 10;
}
