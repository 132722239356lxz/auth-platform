package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * <p>AI 复杂度路由配置新增/编辑入参</p>
 *
 * <p>必填校验放在 Service 层抛 IllegalArgumentException，原因同 {@link AiProviderRequest}。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Schema(description = "AI 复杂度路由配置入参")
public class AiRoutingRequest {

    @Size(max = 128, message = "路由名称长度不能超过128")
    @Schema(description = "路由配置名称", example = "默认路由")
    private String routingName;

    @Schema(description = "关联供应商ID")
    private Long providerId;

    @Schema(description = "关联供应商编码(可选, 用于按编码关联)")
    private String providerCode;

    @Schema(description = "关联供应商名称(冗余存储, 便于展示)")
    private String providerName;

    @Size(max = 128, message = "简单任务模型长度不能超过128")
    @Schema(description = "简单任务使用的模型")
    private String simpleModel;

    @Size(max = 128, message = "中等任务模型长度不能超过128")
    @Schema(description = "中等任务使用的模型")
    private String mediumModel;

    @Size(max = 128, message = "复杂任务模型长度不能超过128")
    @Schema(description = "复杂任务使用的模型")
    private String complexModel;

    @Min(value = 1, message = "简单任务 tokens 至少为1")
    @Max(value = 1000000, message = "简单任务 tokens 过大")
    @Schema(description = "简单任务最大 tokens", example = "1024")
    private Integer simpleMaxTokens;

    @Min(value = 1, message = "中等任务 tokens 至少为1")
    @Max(value = 1000000, message = "中等任务 tokens 过大")
    @Schema(description = "中等任务最大 tokens", example = "2048")
    private Integer mediumMaxTokens;

    @Min(value = 1, message = "复杂任务 tokens 至少为1")
    @Max(value = 1000000, message = "复杂任务 tokens 过大")
    @Schema(description = "复杂任务最大 tokens", example = "4096")
    private Integer complexMaxTokens;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

    @Size(max = 512, message = "备注长度不能超过512")
    @Schema(description = "备注")
    private String remark;
}
