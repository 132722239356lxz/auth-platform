package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * <p>AI 供应商配置新增/编辑请求 DTO</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI 供应商配置请求")
public class AiProviderRequest {

    @Schema(description = "主键(编辑时必填)")
    private Long id;

    @NotBlank(message = "供应商编码不能为空")
    @Schema(description = "供应商编码(唯一, 如 openai/deepseek/qwen)", example = "openai")
    private String providerCode;

    @NotBlank(message = "供应商名称不能为空")
    @Schema(description = "供应商名称", example = "OpenAI")
    private String providerName;

    @NotBlank(message = "供应商类型不能为空")
    @Schema(description = "供应商类型: openai/azure/anthropic/custom", example = "openai")
    private String providerType;

    @NotBlank(message = "API 基础地址不能为空")
    @Schema(description = "API 基础地址", example = "https://api.openai.com/v1")
    private String baseUrl;

    @Schema(description = "API Key(新增必填, 编辑时若留空且 clearSecretKey=false 则保留原值)")
    private String apiKey;

    @Schema(description = "Secret Key(可选)")
    private String secretKey;

    @Schema(description = "默认模型", example = "gpt-4")
    private String defaultModel;

    @Schema(description = "向量模型(用于文本转向量)", example = "text-embedding-3-small")
    private String embeddingModel;

    @Schema(description = "支持的模型列表", example = "[\"gpt-4\",\"gpt-3.5-turbo\"]")
    private List<String> models;

    @Schema(description = "是否主供应商(默认 true=是)")
    private Boolean isPrimary;

    @Schema(description = "优先级(数值越小优先级越高)")
    private Integer priority;

    @NotNull(message = "超时时间不能为空")
    @Schema(description = "请求超时时间(毫秒)", example = "30000")
    private Integer timeoutMs;

    @NotNull(message = "最大重试次数不能为空")
    @Schema(description = "最大重试次数", example = "3")
    private Integer maxRetries;

    @Schema(description = "默认温度参数(0.00-2.00)", example = "0.70")
    private BigDecimal temperature;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

    @Schema(description = "是否清空已保存的密钥(编辑时传 true 则忽略 apiKey/secretKey 并置空)")
    private Boolean clearSecretKey;

    @Schema(description = "备注")
    private String remark;
}
