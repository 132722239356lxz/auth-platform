package com.liang.xz.aiagent.config;

import lombok.Builder;
import lombok.Data;

/**
 * <p>数据库中的供应商配置（供 ai-agent-server 运行时动态读取）</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
public class DbProviderConfig {

    /** 供应商ID */
    private Long id;

    /** 供应商编码 */
    private String providerCode;

    /** 供应商名称 */
    private String providerName;

    /** 供应商类型 (openai, azure, qwen, deepseek, etc.) */
    private String providerType;

    /** API Base URL */
    private String baseUrl;

    /** API Key (已解密) */
    private String apiKey;

    /** Secret Key (已解密，可能为空) */
    private String secretKey;

    /** 默认模型 */
    private String defaultModel;

    /** 嵌入专用模型（若供应商同时提供 embedding，则用于向量化；为空时回退默认模型） */
    private String embeddingModel;

    /** 单次请求最大 Token 数 */
    private Integer maxTokens;

    /** 支持的模型列表(JSON 数组) */
    private String models;

    /** 是否主供应商 */
    private boolean isPrimary;

    /** 优先级(数值越小越高) */
    private int priority;

    /** 请求超时(毫秒) */
    private int timeoutMs;

    /** 最大重试次数 */
    private int maxRetries;

    /** 温度参数 */
    private Double temperature;

    /**
     * 时间单位转换: 毫秒 → 秒
     */
    public long getTimeoutSeconds() {
        return timeoutMs > 0 ? timeoutMs / 1000 : 30;
    }
}
