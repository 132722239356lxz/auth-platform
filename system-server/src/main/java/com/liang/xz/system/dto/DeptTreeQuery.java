package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>部门树查询条件</p>
 *
 * <p>统一封装部门树的多条件筛选参数，后端进行过滤后返回</p>
 *
 * @author auth-platform
 * @since 1.2.0
 */
@Data
@Schema(description = "部门树查询条件")
public class DeptTreeQuery {

    @Schema(description = "搜索关键词（部门名称/编码/负责人）")
    private String keyword;

    @Schema(description = "状态: true=启用 false=禁用")
    private Boolean enabled;
}
