package com.liang.xz.system.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>AI 复杂度路由配置实体 - 对应表 sys_ai_complexity_routing</p>
 *
 * <p>按简单/中等/复杂三档分别绑定模型与最大 Token, 实现按请求复杂度路由不同模型。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI 复杂度路由配置实体")
public class SysAiComplexityRoutingEntity {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "路由名称")
    private String routingName;

    @Schema(description = "关联供应商ID")
    private Long providerId;

    @Schema(description = "关联供应商编码(JOIN 冗余字段)")
    private String providerCode;

    @Schema(description = "关联供应商名称(JOIN 冗余字段)")
    private String providerName;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "简单档模型")
    private String simpleModel;

    @Schema(description = "中等档模型")
    private String mediumModel;

    @Schema(description = "复杂档模型")
    private String complexModel;

    @Schema(description = "简单档最大 Token")
    private Integer simpleMaxTokens;

    @Schema(description = "中等档最大 Token")
    private Integer mediumMaxTokens;

    @Schema(description = "复杂档最大 Token")
    private Integer complexMaxTokens;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
