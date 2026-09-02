package com.liang.xz.aiagent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.aiagent.config.DbProviderConfig;
import com.liang.xz.common.core.util.BeijingTimeUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>LLM 客户端 — 支持 OpenAI / Azure OpenAI / Ollama / vLLM 等兼容 API</p>
 * <p>核心能力: Chat Completion / Embedding / 流式对话 / 视觉多模态</p>
 *
 * <p>配置来源：供应商与模型均由页面配置（数据库 sys_ai_provider 表），通过
 * {@link ProviderFailoverService#getPrimaryProvider()} 获取主供应商配置，不再从静态配置文件读取。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
public class LlmClient {

    private static final int DEFAULT_TIMEOUT = 120;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final EmbeddingService embeddingService;
    private final ProviderFailoverService providerFailoverService;

    public LlmClient(ObjectMapper objectMapper, EmbeddingService embeddingService,
                     ProviderFailoverService providerFailoverService) {
        this.objectMapper = objectMapper;
        this.embeddingService = embeddingService;
        this.providerFailoverService = providerFailoverService;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .build();
    }

    /**
     * 获取主供应商配置（用于视觉/多模态等直接依赖具体供应商能力的场景）。
     * 供应商未配置时返回 null，调用方应做降级处理。
     */
    private DbProviderConfig primaryProvider() {
        return providerFailoverService.getPrimaryProvider();
    }

    // ======================== Chat Completion ========================

    /**
     * 发送视觉对话请求(多模态)，支持图片 URL 或 base64 data URL
     */
    public String visionChat(String systemPrompt, String userMessage, List<String> imageUrls) {
        DbProviderConfig provider = primaryProvider();
        if (provider == null) {
            return "[LLM Error] 未配置主供应商（请从页面配置 AI 供应商）";
        }
        String model = provider.getDefaultModel();
        double temperature = provider.getTemperature() != null ? provider.getTemperature() : 0.7;
        int maxTokens = provider.getMaxTokens() != null ? provider.getMaxTokens() : 4096;
        List<ChatRequest.Message> messages = new ArrayList<>();
        messages.add(ChatRequest.Message.builder().role("system").content(systemPrompt).build());

        List<ChatRequest.ContentPart> parts = new ArrayList<>();
        parts.add(ChatRequest.ContentPart.builder().type("text").text(userMessage).build());
        if (imageUrls != null) {
            for (String url : imageUrls) {
                if (url != null && !url.isBlank()) {
                    parts.add(ChatRequest.ContentPart.builder()
                            .type("image_url")
                            .imageUrl(ChatRequest.ImageUrl.builder().url(url).detail("auto").build())
                            .build());
                }
            }
        }
        messages.add(ChatRequest.Message.builder().role("user").contentArray(parts).build());

        ChatRequest request = ChatRequest.builder()
                .model(model)
                .messages(messages)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .stream(false)
                .build();
        return chat(request);
    }

    /**
     * 发送对话请求并获取回复
     */
    public String chat(String systemPrompt, String userMessage) {
        ChatRequest request = ChatRequest.builder()
                .model(primaryModel())
                .messages(List.of(
                        ChatRequest.Message.builder().role("system").content(systemPrompt).build(),
                        ChatRequest.Message.builder().role("user").content(userMessage).build()))
                .temperature(0.7)
                .maxTokens(4096)
                .stream(false)
                .build();
        return chat(request);
    }

    /**
     * 发送自定义对话请求
     */
    public String chat(ChatRequest request) {
        DbProviderConfig provider = primaryProvider();
        if (provider == null) {
            return "[LLM Error] 未配置主供应商（请从页面配置 AI 供应商）";
        }
        String baseUrl = provider.getBaseUrl();
        String apiKey = providerFailoverService.decryptApiKeyProvider(provider);
        String model = request.getModel() != null && !request.getModel().isBlank()
                ? request.getModel() : provider.getDefaultModel();
        try {
            String url = baseUrl.replaceAll("/+$", "") + "/chat/completions";
            String json = objectMapper.writeValueAsString(request.toRequestBody());
            log.debug("LLM Request -> {}", json);

            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
            Request httpRequest = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.error("[LLM调用失败] 时间={} model={} HTTP状态码={} 响应体={}",
                            BeijingTimeUtil.formatNow(), model, response.code(), responseBody);
                    return "[LLM Error] HTTP " + response.code() + ": " + responseBody;
                }
                ChatResponse chatResponse = objectMapper.readValue(responseBody, ChatResponse.class);
                log.debug("LLM Response tokens: {}", chatResponse.getUsage());
                return chatResponse.getContent();
            }
        } catch (IOException e) {
            log.error("[LLM调用失败] 时间={} model={} provider={} 失败原因={}",
                    BeijingTimeUtil.formatNow(), model, baseUrl, e.getMessage(), e);
            return "[LLM Connection Error] " + e.getMessage();
        }
    }

    /**
     * 多轮对话
     */
    public String multiTurnChat(List<ChatRequest.Message> messages) {
        DbProviderConfig provider = primaryProvider();
        String model = provider != null ? provider.getDefaultModel() : "";
        ChatRequest request = ChatRequest.builder()
                .model(model)
                .messages(messages)
                .temperature(0.7)
                .maxTokens(4096)
                .stream(false)
                .build();
        return chat(request);
    }

    private String primaryModel() {
        DbProviderConfig provider = primaryProvider();
        return provider != null ? provider.getDefaultModel() : "";
    }


    // ======================== 图片/文件 AI 分析 ========================

    /**
     * 调用视觉模型分析图片内容（base64），返回文字描述
     */
    public String describeImage(byte[] imageBytes, String mimeType) {
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        String dataUrl = "data:" + (mimeType != null ? mimeType : "image/png") + ";base64," + base64;
        return visionChat(
                "你是一个专业的图像分析助手。请仔细观察并详细描述图片中的所有可见内容，"
                        + "包括但不限于：文字信息、物体、人物、场景、图表数据、界面元素、颜色、布局等。"
                        + "请用中文输出，尽可能详尽。",
                "请详细描述这张图片的内容，不要遗漏任何细节。",
                List.of(dataUrl));
    }

    /**
     * 分析文件原始内容（对无法通过解析器提取文本的文件，用 AI 提取关键信息）
     */
    public String describeFile(byte[] fileBytes, String fileName, String mimeType) {
        // 尝试将文件内容转为文本片段
        String textContent;
        try {
            textContent = new String(fileBytes, 0, Math.min(fileBytes.length, 4000), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            textContent = "[二进制文件，无法直接读取文本]";
        }
        String prompt = String.format(
                "你是一个文件内容分析助手。用户上传了一个文件 '%s' (类型: %s)。"
                        + "请分析以下文件内容片段，提取关键信息和摘要。如果内容是乱码或二进制，请说明你无法解析。"
                        + "请用中文输出。",
                fileName, mimeType != null ? mimeType : "unknown");
        return chat(prompt, "文件内容片段:\n" + textContent);
    }

    // ======================== Embedding ========================

    /**
     * 文本向量化（单条）。
     * <p>委托给 {@link EmbeddingService}，由它处理「配置模型优先 + 默认模型降级」二级策略。</p>
     */
    public List<Double> embed(String text) {
        return embeddingService.embed(text);
    }

    /**
     * 批量向量化。
     * <p>委托给 {@link EmbeddingService}，由它处理「配置模型优先 + 默认模型降级」二级策略。</p>
     */
    public List<List<Double>> embedBatch(List<String> texts) {
        return embeddingService.embedBatch(texts);
    }

    /**
     * 健康检查 — 快速测试 LLM 连通性
     */
    public boolean healthCheck() {
        try {
            String result = chat("你是一个助手", "回复OK");
            return result != null && !result.startsWith("[LLM");
        } catch (Exception e) {
            return false;
        }
    }
}
