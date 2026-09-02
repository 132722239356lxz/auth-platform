package com.liang.xz.aiagent.rag;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.aiagent.config.AiProperties;
import com.liang.xz.aiagent.service.AiInvokeLogService;
import com.liang.xz.aiagent.vector.VectorDocument;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>重排序（Rerank）服务</p>
 *
 * <p>调用本地部署的 {@code bge-reranker-base} 重排序服务（{@code POST /rerank}），
 * 对向量召回的候选文档做语义精排，提升最终返回结果的准确性。</p>
 *
 * <p>非致命降级：当重排序服务不可用（未开启 / 网络异常 / 解析失败）时，
 * 返回原始候选列表（保持向量相似度顺序），不阻断 RAG 主流程。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class RerankService {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private final AiInvokeLogService aiInvokeLogService;

    public RerankService(AiProperties aiProperties, ObjectMapper objectMapper,
                          AiInvokeLogService aiInvokeLogService) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.aiInvokeLogService = aiInvokeLogService;
        AiProperties.RerankConfig cfg = aiProperties.getRerank();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(cfg.getConnectTimeout(), TimeUnit.SECONDS)
                .readTimeout(cfg.getReadTimeout(), TimeUnit.SECONDS)
                .writeTimeout(cfg.getReadTimeout(), TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(8, 1, TimeUnit.MINUTES))
                .build();
    }

    /**
     * 对候选文档按与 query 的相关性重排序。
     *
     * @param query 用户查询
     * @param candidates 待精排的候选文档（向量召回结果），顺序将被原地重排并写回 score
     * @return 重排序后的文档列表（size 与输入一致），服务不可用时返回原顺序
     */
    public List<VectorDocument> rerank(String query, List<VectorDocument> candidates) {
        AiProperties.RerankConfig cfg = aiProperties.getRerank();
        if (!cfg.isEnabled() || candidates == null || candidates.isEmpty()) {
            return candidates == null ? List.of() : candidates;
        }
        if (query == null || query.isBlank()) {
            return candidates;
        }

        long start = System.currentTimeMillis();
        // 控制单次请求候选数，超过截断（优先保留高分候选）
        List<VectorDocument> docs = candidates;
        if (docs.size() > cfg.getMaxCandidates()) {
            docs = new ArrayList<>(docs.subList(0, cfg.getMaxCandidates()));
        }

        // 保留原始向量相似度分数，用于重排序失败/分数异常时恢复
        Map<Integer, Double> originalScores = new HashMap<>(docs.size());
        for (int i = 0; i < docs.size(); i++) {
            originalScores.put(i, docs.get(i).getScore());
        }

        boolean used = false;
        String errorMsg = null;
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", cfg.getModel());
            body.put("query", query);
            List<String> texts = new ArrayList<>(docs.size());
            for (VectorDocument d : docs) {
                texts.add(d.getText() == null ? "" : d.getText());
            }
            body.put("documents", texts);
            String json = objectMapper.writeValueAsString(body);

            Request request = new Request.Builder()
                    .url(cfg.getUrl())
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, JSON))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    errorMsg = "重排序服务返回 HTTP " + response.code();
                    log.warn("[RerankService] {}，降级为原顺序", errorMsg);
                    restoreOriginalScores(docs, originalScores);
                    return candidates;
                }
                RerankResponse resp = objectMapper.readValue(respBody, RerankResponse.class);
                if (resp.results == null || resp.results.isEmpty()) {
                    errorMsg = "重排序返回结果为空";
                    log.warn("[RerankService] {}，降级为原顺序", errorMsg);
                    restoreOriginalScores(docs, originalScores);
                    return candidates;
                }
                // 按服务返回的相关性分数重排候选，并写回 score
                List<VectorDocument> reordered = new ArrayList<>(docs.size());
                boolean allZero = true;
                for (RerankResponse.RerankResult r : resp.results) {
                    if (r.index >= 0 && r.index < docs.size()) {
                        VectorDocument doc = docs.get(r.index);
                        double score = r.relevanceScore;
                        if (score > 0) {
                            allZero = false;
                        }
                        doc.setScore(score);
                        reordered.add(doc);
                    }
                }
                if (reordered.isEmpty()) {
                    errorMsg = "重排序结果映射为空";
                    restoreOriginalScores(docs, originalScores);
                    return candidates;
                }
                // 防御：若所有重排序分数都是 0，大概率是字段绑定失败或服务异常，恢复原始分数
                if (allZero && originalScores.values().stream().anyMatch(s -> s > 0)) {
                    errorMsg = "重排序返回分数全为 0，疑似绑定失败";
                    log.warn("[RerankService] {}，降级为原顺序。响应样例：{}",
                            errorMsg, truncate(respBody, 512));
                    restoreOriginalScores(docs, originalScores);
                    return candidates;
                }
                reordered.sort(Comparator.comparingDouble(VectorDocument::getScore).reversed());
                used = true;
                log.debug("[RerankService] 完成重排序，候选 {} 条，模型 {}", docs.size(), cfg.getModel());
                return reordered;
            }
        } catch (IOException e) {
            errorMsg = "重排序调用失败：" + e.getMessage();
            log.warn("[RerankService] {}，降级为原顺序", errorMsg);
            restoreOriginalScores(docs, originalScores);
            return candidates;
        } catch (Exception e) {
            errorMsg = "重排序处理异常：" + e.getMessage();
            log.warn("[RerankService] {}，降级为原顺序", errorMsg);
            restoreOriginalScores(docs, originalScores);
            return candidates;
        } finally {
            // 记录重排序调用日志（used=false 表示降级，前端视为「命中/免调用」）
            aiInvokeLogService.logRerank(null, cfg.getModel(), used, query,
                    System.currentTimeMillis() - start, errorMsg == null, errorMsg);
        }
    }

    /**
     * 将候选文档的 score 恢复为原始的向量相似度分数。
     */
    private void restoreOriginalScores(List<VectorDocument> docs, Map<Integer, Double> originalScores) {
        if (docs == null || originalScores == null) {
            return;
        }
        for (int i = 0; i < docs.size(); i++) {
            VectorDocument doc = docs.get(i);
            Double original = originalScores.get(i);
            if (doc != null && original != null) {
                doc.setScore(original);
            }
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /** 重排序服务响应 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class RerankResponse {
        public List<RerankResult> results;

        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class RerankResult {
            public int index;

            /** 兼容 bge-reranker-base 标准返回字段 relevance_score，同时兼容 score/relevanceScore */
            @JsonProperty("relevance_score")
            @JsonAlias({"score", "relevanceScore"})
            public double relevanceScore;
        }
    }
}
