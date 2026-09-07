package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * <p>字典数据请求</p>
 *
 * @author auth-platform
 * @since 1.2.0
 */
@Data
@Schema(description = "字典数据请求")
public class DictDataRequest {

    @NotNull(message = "字典类型ID不能为空")
    @Schema(description = "字典类型ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long typeId;

    @NotBlank(message = "字典标签不能为空")
    @Size(max = 50, message = "字典标签长度不能超过50字符")
    @Schema(description = "字典标签(显示名称)", example = "启用", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dictLabel;

    @NotBlank(message = "字典值不能为空")
    @Size(max = 50, message = "字典值长度不能超过50字符")
    @Schema(description = "字典值", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dictValue;

    @Schema(description = "排序号", example = "1")
    private Integer sortOrder;

    @Schema(description = "CSS样式类", example = "success")
    private String cssClass;

    @Schema(description = "列表样式类", example = "default")
    private String listClass;

    @Schema(description = "备注")
    private String remark;
}
