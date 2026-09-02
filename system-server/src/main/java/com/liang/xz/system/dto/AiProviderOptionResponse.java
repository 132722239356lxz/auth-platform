package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * <p>AI 供应商下拉选项 —— 供路由配置页面选择关联供应商</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Schema(description = "AI 供应商下拉选项")
public class AiProviderOptionResponse {

    @Schema(description = "供应商ID")
    private Long id;

    @Schema(description = "供应商编码")
    private String providerCode;

    @Schema(description = "供应商名称")
    private String providerName;

    @Schema(description = "供应商类型")
    private String providerType;

    @Schema(description = "默认模型")
    private String defaultModel;

    @Schema(description = "向量模型(用于文本转向量)")
    private String embeddingModel;

    @Schema(description = "支持的模型列表，供路由页面提供模型候选")
    private List<String> models;
}
