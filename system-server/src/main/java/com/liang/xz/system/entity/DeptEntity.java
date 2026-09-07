package com.liang.xz.system.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>部门实体 —— 映射 sys_dept 表</p>
 *
 * @author auth-platform
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeptEntity {

    @Schema(description = "部门主键(自增)")
    private Long id;

    @Schema(description = "父部门ID (0=顶级部门)")
    private Long parentId;

    @Schema(description = "部门名称")
    private String deptName;

    @Schema(description = "部门编码(唯一)")
    private String deptCode;

    @Schema(description = "部门负责人")
    private String leader;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "排序号")
    private Integer sortOrder;

    @Schema(description = "状态: true=启用, false=禁用")
    private Boolean enabled;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
