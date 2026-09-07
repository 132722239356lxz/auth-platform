package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * <p>字典类型请求</p>
 *
 * @author auth-platform
 * @since 1.2.0
 */
@Data
@Schema(description = "字典类型请求")
public class DictTypeRequest {

    @NotBlank(message = "字典名称不能为空")
    @Size(max = 50, message = "字典名称长度不能超过50字符")
    @Schema(description = "字典名称", example = "用户状态", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dictName;

    @NotBlank(message = "字典类型不能为空")
    @Size(max = 50, message = "字典类型长度不能超过50字符")
    @Schema(description = "字典类型(唯一标识)", example = "sys_user_status", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dictType;

    @Schema(description = "描述", example = "用户启用/禁用状态")
    private String description;
}
