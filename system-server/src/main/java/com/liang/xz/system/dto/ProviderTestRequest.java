package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * <p>供应商连通性探测入参 —— 用于表单尚未保存时先行测试</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Schema(description = "供应商连通性探测入参")
public class ProviderTestRequest {

    @Schema(description = "已保存的供应商ID。传了 ID 且 apiKey 为空时，用库中已保存的密钥探测")
    private Long providerId;

    @Size(max = 32, message = "供应商类型长度不能超过32")
    @Schema(description = "供应商类型: openai/azure/anthropic/ollama/custom", example = "openai")
    private String providerType;

    @Size(max = 512, message = "Base URL 长度不能超过512")
    @Schema(description = "API 基础地址", example = "https://api.deepseek.com/v1")
    private String baseUrl;

    @Size(max = 256, message = "API Key 长度不能超过256")
    @Schema(description = "API Key 明文。留空则回退到 providerId 对应的已存密钥")
    private String apiKey;

    @Schema(description = "探测超时时间(毫秒)，默认取供应商配置或 10000", example = "10000")
    private Integer timeoutMs;
}
