package com.liang.xz.system.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>字典数据实体 —— 映射 sys_dict_data 表</p>
 *
 * @author auth-platform
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictDataEntity {

    @Schema(description = "字典数据主键(自增)")
    private Long id;

    @Schema(description = "字典类型ID(关联 sys_dict_type.id)")
    private Long typeId;

    @Schema(description = "字典标签(显示值)")
    private String dictLabel;

    @Schema(description = "字典值(存储值)")
    private String dictValue;

    @Schema(description = "排序号")
    private Integer sortOrder;

    @Schema(description = "CSS样式类名")
    private String cssClass;

    @Schema(description = "列表展示样式")
    private String listClass;

    @Schema(description = "状态: true=启用, false=禁用")
    private Boolean enabled;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
