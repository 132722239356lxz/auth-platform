package com.liang.xz.system.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>AI 模型供应商配置实体 - 对应表 sys_ai_provider</p>
 *
 * <p>密钥字段(api_key/secret_key)在落库前由 Service 层加密, 本实体仅持有密文。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI 模型供应商配置实体")
public class SysAiProviderEntity {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "供应商编码(唯一, 如 openai/deepseek/qwen)")
    private String providerCode;

    @Schema(description = "供应商名称")
    private String providerName;

    @Schema(description = "供应商类型: openai/azure/anthropic/custom")
    private String providerType;

    @Schema(description = "API 基础地址")
    private String baseUrl;

    @Schema(description = "API Key 密文")
    private String apiKey;

    @Schema(description = "Secret Key 密文")
    private String secretKey;

    @Schema(description = "默认模型")
    private String defaultModel;

    @Schema(description = "向量模型(用于文本转向量, 如 text-embedding-3-small)")
    private String embeddingModel;

    @Schema(description = "支持的模型列表(逗号分隔存储)")
    private String models;

    @Schema(description = "请求超时时间(毫秒)")
    private Integer timeoutMs;

    @Schema(description = "最大重试次数")
    private Integer maxRetries;

    @Schema(description = "默认温度参数(0.00-2.00)")
    private BigDecimal temperature;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "是否主供应商")
    private Boolean isPrimary;

    @Schema(description = "优先级(数值越小优先级越高)")
    private Integer priority;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
