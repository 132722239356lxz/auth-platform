package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <p>AI 供应商配置分页查询 DTO</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI 供应商配置分页查询")
public class AiProviderPageQuery {

    @Schema(description = "页码(从1开始)", example = "1")
    private Integer page = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "供应商名称模糊查询")
    private String providerName;

    @Schema(description = "供应商类型")
    private String providerType;

    @Schema(description = "是否启用: true/false")
    private Boolean enabled;
}
