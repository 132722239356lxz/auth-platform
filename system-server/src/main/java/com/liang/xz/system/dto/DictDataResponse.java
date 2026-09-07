package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>字典数据响应</p>
 *
 * @author auth-platform
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字典数据响应")
public class DictDataResponse {

    @Schema(description = "字典数据ID")
    private Long id;

    @Schema(description = "字典类型ID")
    private Long typeId;

    @Schema(description = "字典标签")
    private String dictLabel;

    @Schema(description = "字典值")
    private String dictValue;

    @Schema(description = "排序号")
    private Integer sortOrder;

    @Schema(description = "CSS样式类")
    private String cssClass;

    @Schema(description = "列表样式类")
    private String listClass;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
