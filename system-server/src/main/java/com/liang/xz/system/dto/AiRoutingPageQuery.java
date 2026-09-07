package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * <p>AI 复杂度路由配置分页查询参数</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Schema(description = "AI 复杂度路由配置查询参数")
public class AiRoutingPageQuery {

    @Schema(description = "关键词(路由名称/供应商编码/供应商名称)")
    private String keyword;

    @Schema(description = "关联供应商ID")
    private Long providerId;

    @Schema(description = "状态：true=启用，false=禁用")
    private Boolean enabled;

    @Min(value = 1, message = "页码从1开始")
    @Schema(description = "页码", example = "1")
    private Integer page = 1;

    @Min(value = 1, message = "每页至少1条")
    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;
}
