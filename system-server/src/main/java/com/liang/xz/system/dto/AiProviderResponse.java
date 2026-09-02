package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>AI 供应商配置响应 DTO</p>
 *
 * <p>密钥字段返回脱敏后的掩码, 不暴露明文; 如需重新设置密钥请在更新请求中传明文。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI 供应商配置响应")
public class AiProviderResponse {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "供应商编码(唯一)")
    private String providerCode;

    @Schema(description = "供应商名称")
    private String providerName;

    @Schema(description = "供应商类型: openai/azure/anthropic/custom")
    private String providerType;

    @Schema(description = "API 基础地址")
    private String baseUrl;

    @Schema(description = "API Key 脱敏掩码")
    private String apiKeyMasked;

    @Schema(description = "是否配置 API Key")
    private Boolean apiKeyConfigured;

    @Schema(description = "Secret Key 脱敏掩码")
    private String secretKeyMasked;

    @Schema(description = "是否配置 Secret Key")
    private Boolean secretKeyConfigured;

    @Schema(description = "默认模型")
    private String defaultModel;

    @Schema(description = "向量模型(用于文本转向量)")
    private String embeddingModel;

    @Schema(description = "支持的模型列表")
    private List<String> models;

    @Schema(description = "是否主供应商")
    private Boolean isPrimary;

    @Schema(description = "优先级(数值越小越高)")
    private Integer priority;

    @Schema(description = "请求超时时间(毫秒)")
    private Integer timeoutMs;

    @Schema(description = "最大重试次数")
    private Integer maxRetries;

    @Schema(description = "默认温度参数(0.00-2.00)")
    private BigDecimal temperature;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
