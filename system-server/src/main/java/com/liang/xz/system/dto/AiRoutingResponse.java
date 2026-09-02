package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * <p>AI 复杂度路由配置出参</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Schema(description = "AI 复杂度路由配置")
public class AiRoutingResponse {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "路由配置名称")
    private String routingName;

    @Schema(description = "关联供应商ID")
    private Long providerId;

    @Schema(description = "关联供应商编码，供应商已删除时为 null")
    private String providerCode;

    @Schema(description = "关联供应商名称，供应商已删除时为 null")
    private String providerName;

    @Schema(description = "简单任务使用的模型")
    private String simpleModel;

    @Schema(description = "中等任务使用的模型")
    private String mediumModel;

    @Schema(description = "复杂任务使用的模型")
    private String complexModel;

    @Schema(description = "简单任务最大 tokens")
    private Integer simpleMaxTokens;

    @Schema(description = "中等任务最大 tokens")
    private Integer mediumMaxTokens;

    @Schema(description = "复杂任务最大 tokens")
    private Integer complexMaxTokens;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "是否已配置完整路由(三个复杂度模型均非空)")
    private Boolean routingConfigured;

    @Schema(description = "该路由下关联的供应商数量")
    private Long providerCount;

    @Schema(description = "链路下一层级(用于前端展示路由链)")
    private Integer nextLevel;

    @Schema(description = "路由总层级数")
    private Integer totalLevels;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
