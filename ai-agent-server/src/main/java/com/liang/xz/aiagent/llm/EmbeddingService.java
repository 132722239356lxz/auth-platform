package com.liang.xz.aiagent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.aiagent.config.AiProperties;
import com.liang.xz.aiagent.config.DbProviderConfig;
import com.liang.xz.aiagent.repository.DbProviderConfigRepository;
import com.liang.xz.common.core.crypto.CryptoManager;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>文本向量化服务</p>
 *
 * <p>策略：三级降级
 * <ol>
 *     <li>数据库供应商（{@link AiProperties.EmbeddingConfig#getProviderCode()} 指向 sys_ai_provider 表中的启用供应商）：
 *         <ul>
 *             <li>先用 {@code GET {baseUrl}/health} 探活，校验返回的 model 与配置一致；</li>
 *             <li>通过后使用 OpenAI 兼容的 {@code POST {baseUrl}/embeddings} 调通。</li>
 *         </ul>
 *     </li>
 *     <li>默认模型 {@code Qwen3-Embedding-0.6B} @ {@code http://127.0.0.1:8000}：
 *         <ul>
 *             <li>使用自定义协议 {@code POST /embed}，请求体
 *             {@code {"texts":[...], "prompt_name": "...(可选)", "normalize_embeddings": true}}；
 *             未指定提示词时省略 {@code prompt_name} 字段；</li>
 *             <li>响应 {@code {"dim":1024, "count":N, "embeddings":[[...], ...]}}。</li>
 *         </ul>
 *     </li>
 *     <li>默认模型仍不可用：抛错，向调用方返回失败。</li>
 * </ol>
 * </p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
public class EmbeddingService {

    /** 向量后端默认服务端口（Qwen3-Embedding-0.6B 部署在 8000） */
    static final String DEFAULT_BASE_URL = "http://127.0.0.1:8000";

    /** 向量后端默认模型名 */
    static final String DEFAULT_MODEL = "Qwen3-Embedding-0.6B";

    /** 健康检查超时（秒）。健康检查要快，避免拖慢主流程 */
    private static final long HEALTH_TIMEOUT_SEC = 3L;

    private final OkHttpClient httpClient;
    private final OkHttpClient healthClient;
    /** 默认向量模型专用客户端：读超时更长（大批量 embedding 推理耗时久） */
    private final OkHttpClient defaultHttpClient;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;
    private final DbProviderConfigRepository configRepository;
    private final CryptoManager cryptoManager;

    public EmbeddingService(AiProperties aiProperties, ObjectMapper objectMapper,
                            DbProviderConfigRepository configRepository, CryptoManager cryptoManager) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.configRepository = configRepository;
        this.cryptoManager = cryptoManager;
        long timeout = 30L;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(timeout, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(8, 1, TimeUnit.MINUTES))
                .build();
        // 健康检查专用客户端，超时更短，避免被大模型生成卡住
        this.healthClient = new OkHttpClient.Builder()
                .connectTimeout(HEALTH_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(HEALTH_TIMEOUT_SEC, TimeUnit.SECONDS)
                .writeTimeout(HEALTH_TIMEOUT_SEC, TimeUnit.SECONDS)
                .build();
        // 默认向量模型客户端：读超时放宽到 120s，避免大批量 embedding 推理超时
        this.defaultHttpClient = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(120L, TimeUnit.SECONDS)
                .writeTimeout(timeout, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(8, 1, TimeUnit.MINUTES))
                .build();
    }

    /**
     * 单条文本向量化
     *
     * @param text 待向量化的文本
     * @return 维度向量（{@link Double}）
     */
    public List<Double> embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("向量化内容不能为空");
        }
        ResolvedEmbeddingProvider provider = resolveProvider();
        try {
            return callConfiguredModel(List.of(text), provider).get(0);
        } catch (Exception configuredEx) {
            log.warn("[EmbeddingService] 嵌入供应商不可用 (url={}, model={}): {} - 切换默认模型 {} @ {}",
                    provider.baseUrl, provider.model, configuredEx.getMessage(),
                    DEFAULT_MODEL, DEFAULT_BASE_URL);
            try {
                return callDefaultModel(List.of(text)).get(0);
            } catch (Exception defaultEx) {
                log.error("[EmbeddingService] 默认向量模型也调用失败 ({}): {}",
                        DEFAULT_MODEL, defaultEx.getMessage());
                throw new IllegalStateException(
                        "文本向量化失败：嵌入供应商和默认模型均不可用 - " + defaultEx.getMessage(),
                        defaultEx);
            }
        }
    }

    /** 单次 embedding 请求的文本分批上限，避免大批量推理触发读超时 */
    private static final int BATCH_SIZE = 16;

    /**
     * 批量向量化
     *
     * <p>为降低单次请求因文本过多导致服务端推理超时，按 {@link #BATCH_SIZE} 分批调用，
     * 空文本在结果中对应位置补 {@code null}，保持与输入顺序一致。</p>
     *
     * @param texts 待向量化的文本列表
     * @return 与输入顺序一一对应的向量列表
     */
    public List<List<Double>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        // 记录有效文本的原始下标，便于回填；空文本结果置 null
        List<Integer> validIndexes = new ArrayList<>(texts.size());
        List<String> valid = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            String t = texts.get(i);
            if (t != null && !t.isBlank()) {
                valid.add(t);
                validIndexes.add(i);
            }
        }
        if (valid.isEmpty()) {
            throw new IllegalArgumentException("向量化内容不能为空");
        }
        ResolvedEmbeddingProvider provider = resolveProvider();
        List<List<Double>> batchResult;
        try {
            batchResult = callConfiguredModelInBatches(valid, validIndexes, provider);
        } catch (Exception configuredEx) {
            log.warn("[EmbeddingService] 嵌入供应商不可用 (url={}, model={}): {} - 切换默认模型 {} @ {}",
                    provider.baseUrl, provider.model, configuredEx.getMessage(),
                    DEFAULT_MODEL, DEFAULT_BASE_URL);
            try {
                batchResult = callDefaultModelInBatches(valid, validIndexes);
            } catch (Exception defaultEx) {
                log.error("[EmbeddingService] 默认向量模型也调用失败 ({}): {}",
                        DEFAULT_MODEL, defaultEx.getMessage());
                throw new IllegalStateException(
                        "批量向量化失败：嵌入供应商和默认模型均不可用 - " + defaultEx.getMessage(),
                        defaultEx);
            }
        }
        // 按原始顺序回填，空文本位置为 null
        List<List<Double>> result = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            result.add(null);
        }
        for (int k = 0; k < validIndexes.size(); k++) {
            result.set(validIndexes.get(k), batchResult.get(k));
        }
        return result;
    }

    private List<List<Double>> callConfiguredModelInBatches(List<String> valid,
                                                            List<Integer> validIndexes,
                                                            ResolvedEmbeddingProvider provider) throws IOException {
        List<List<Double>> merged = new ArrayList<>(valid.size());
        for (int from = 0; from < valid.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, valid.size());
            merged.addAll(callConfiguredModel(valid.subList(from, to), provider));
        }
        return merged;
    }

    private List<List<Double>> callDefaultModelInBatches(List<String> valid,
                                                        List<Integer> validIndexes) throws IOException {
        List<List<Double>> merged = new ArrayList<>(valid.size());
        for (int from = 0; from < valid.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, valid.size());
            merged.addAll(callDefaultModel(valid.subList(from, to)));
        }
        return merged;
    }


    /**
     * 解析嵌入供应商：优先使用数据库中 providerCode 指向的启用供应商；
     * 不存在或不可用则回退到默认配置（EmbeddingConfig 的 standalone 配置）。
     */
    private ResolvedEmbeddingProvider resolveProvider() {
        AiProperties.EmbeddingConfig embedding = aiProperties.getEmbedding();
        String providerCode = embedding.getProviderCode();
        if (providerCode != null && !providerCode.isBlank()) {
            DbProviderConfig dbProvider = configRepository.findByCodeAndEnabled(providerCode);
            if (dbProvider != null) {
                String apiKey = cryptoManager.decrypt(dbProvider.getApiKey());
                String baseUrl = dbProvider.getBaseUrl();
                String model = dbProvider.getEmbeddingModel();
                if (model == null || model.isBlank()) {
                    model = embedding.getModel();
                }
                if (baseUrl != null && !baseUrl.isBlank() && apiKey != null) {
                    return new ResolvedEmbeddingProvider(baseUrl, apiKey, model, true);
                }
                log.warn("[EmbeddingService] 数据库嵌入供应商({}) 配置不完整，回退默认配置", providerCode);
            } else {
                log.warn("[EmbeddingService] 未找到启用的嵌入供应商({})，回退默认配置", providerCode);
            }
        }
        // 回退到默认配置
        return new ResolvedEmbeddingProvider(embedding.getBaseUrl(), embedding.getApiKey(),
                embedding.getModel(), embedding.isStandalone());
    }

    // ========================= 配置模型：OpenAI 兼容 /embeddings =========================

    private List<List<Double>> callConfiguredModel(List<String> texts,
                                                   ResolvedEmbeddingProvider cfg) throws IOException {
        if (cfg == null) {
            throw new IllegalStateException("嵌入供应商未解析");
        }
        if (cfg.baseUrl == null || cfg.baseUrl.isBlank()) {
            throw new IllegalStateException("嵌入供应商 baseUrl 未配置");
        }
        if (cfg.model == null || cfg.model.isBlank()) {
            throw new IllegalStateException("嵌入供应商 model 未配置");
        }
        // 先探活
        checkHealth(cfg.baseUrl, cfg.model);
        // OpenAI 兼容
        String url = cfg.baseUrl.replaceAll("/+$", "") + "/embeddings";
        Map<String, Object> body = Map.of(
                "model", cfg.model,
                "input", texts
        );
        String json = objectMapper.writeValueAsString(body);
        Request httpRequest = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + cfg.apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();
        try (Response response = defaultHttpClient.newCall(httpRequest).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + truncate(responseBody, 256));
            }
            EmbeddingResponse embResponse = objectMapper.readValue(responseBody, EmbeddingResponse.class);
            if (embResponse.getData() == null || embResponse.getData().isEmpty()) {
                throw new IOException("返回 data 为空: " + truncate(responseBody, 256));
            }
            List<List<Double>> result = new ArrayList<>();
            for (EmbeddingResponse.EmbeddingData d : embResponse.getData()) {
                result.add(d.getEmbedding());
            }
            return result;
        }
    }

    /** 解析后的嵌入供应商（运行时） */
    private static class ResolvedEmbeddingProvider {
        final String baseUrl;
        final String apiKey;
        final String model;
        final boolean standalone;

        ResolvedEmbeddingProvider(String baseUrl, String apiKey, String model, boolean standalone) {
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.model = model;
            this.standalone = standalone;
        }
    }

    // ======================= 默认模型（Qwen3）：POST /embed =======================

    private List<List<Double>> callDefaultModel(List<String> texts) throws IOException {
        String url = DEFAULT_BASE_URL + "/embed";
        // 默认模型协议：{ texts: [...], prompt_name: <可选>, normalize_embeddings: true }
        // 注意：服务端要求 prompt_name 不能传 null（传 null 会报 JSON 格式错误），
        // 未指定提示词时直接省略该字段，由服务端按缺省处理。
        Map<String, Object> body = new HashMap<>();
        body.put("texts", texts);
        body.put("normalize_embeddings", true);
        String json = objectMapper.writeValueAsString(body);
        Request httpRequest = new Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();
        try (Response response = defaultHttpClient.newCall(httpRequest).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + truncate(responseBody, 256));
            }
            // 响应结构 { dim, count, embeddings: [[float, ...], ...] }
            QwenEmbeddingResponse resp = objectMapper.readValue(responseBody, QwenEmbeddingResponse.class);
            if (resp.embeddings == null || resp.embeddings.isEmpty()) {
                throw new IOException("响应 embeddings 为空: " + truncate(responseBody, 256));
            }
            // 把 float[] 转为 List<Double>，保持与配置模型返回类型一致
            List<List<Double>> result = new ArrayList<>(resp.embeddings.size());
            for (float[] one : resp.embeddings) {
                List<Double> converted = new ArrayList<>(one.length);
                for (float v : one) {
                    converted.add((double) v);
                }
                result.add(converted);
            }
            return result;
        }
    }

    // ========================= 健康检查 =========================

    /**
     * 健康检查：GET {baseUrl}/health，校验 status=ok 且返回的 model 与预期一致。
     *
     * <p>只校验配置模型；默认模型在调用阶段才正式触达。</p>
     */
    private void checkHealth(String baseUrl, String expectedModel) throws IOException {
        String url = baseUrl.replaceAll("/+$", "") + "/health";
        Request req = new Request.Builder().url(url).get().build();
        try (Response response = healthClient.newCall(req).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("健康检查返回 HTTP " + response.code() + ": " + truncate(body, 256));
            }
            QwenHealthResponse hr;
            try {
                hr = objectMapper.readValue(body, QwenHealthResponse.class);
            } catch (Exception parseEx) {
                // 允许非 Qwen 协议：仅校验 2xx，不要求 model 字段
                log.debug("[EmbeddingService] 健康响应不是 Qwen 标准格式，忽略模型名校验: {}", truncate(body, 128));
                return;
            }
            if (hr.status != null && !"ok".equalsIgnoreCase(hr.status)) {
                throw new IOException("健康状态非 ok: " + hr.status);
            }
            if (expectedModel != null && !expectedModel.isBlank()
                    && hr.model != null && !hr.model.isBlank()
                    && !hr.model.equalsIgnoreCase(expectedModel)) {
                // 当前配置的模型名与健康返回的不一致：不算彻底失败，但打 warn，留给上层选择
                log.warn("[EmbeddingService] 健康返回的 model={} 与配置 model={} 不一致，但仍尝试调用", hr.model, expectedModel);
            }
        }
    }

    // ========================= 内部类 =========================

    /**
     * Qwen 默认向量服务 /health 响应
     */
    public static class QwenHealthResponse {
        public String status;
        public String model;
        public String device;
    }

    /**
     * Qwen 默认向量服务 /embed 响应
     */
    public static class QwenEmbeddingResponse {
        public Integer dim;
        public Integer count;
        public List<float[]> embeddings;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
