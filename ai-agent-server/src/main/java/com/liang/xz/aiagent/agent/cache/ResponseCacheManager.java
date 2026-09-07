package com.liang.xz.aiagent.agent.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.liang.xz.aiagent.config.AiProperties;
import com.liang.xz.aiagent.llm.LlmClient;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * <p>响应缓存管理器 — 基于Caffeine的高性能本地缓存</p>
 *
 * <p>缓存策略：
 * <ol>
 *   <li>Key: SHA-256(question + fileContentHashes)</li>
 *   <li>相似问题检测：通过embedding余弦相似度匹配，相似度 > 阈值视为命中</li>
 *   <li>缓存值包含：原回答 + 引用的上下文文件Hash(用于失效判断)</li>
 *   <li>单条缓存默认TTL 30分钟</li>
 * </ol>
 * </p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
public class ResponseCacheManager {

    private final Cache<String, CachedResponse> cache;
    private final AiProperties aiProperties;
    private final LlmClient llmClient;

    public ResponseCacheManager(AiProperties aiProperties, LlmClient llmClient) {
        this.aiProperties = aiProperties;
        this.llmClient = llmClient;
        var cacheConfig = aiProperties.getCache();
        this.cache = Caffeine.newBuilder()
                .maximumSize(cacheConfig.getMaxSize())
                .expireAfterWrite(Duration.ofMinutes(cacheConfig.getTtlMinutes()))
                .recordStats()
                .build();
    }

    @Data
    @Builder
    public static class CachedResponse {
        private String question;
        private String answer;
        /** 问题embedding(用于相似度匹配) */
        private List<Double> questionEmbedding;
        /** 引用文件的内容哈希(用于失效判断) */
        private String filesDigest;
        private long cachedAt;
        private int hitCount;
    }

    @Data
    @Builder
    public static class CacheLookupResult {
        private boolean hit;
        private CachedResponse cachedResponse;
        private double similarity;
        private String cacheKey;
    }

    /**
     * 查询缓存 — 优先精确匹配，其次语义相似度匹配
     */
    public CacheLookupResult lookup(String question, List<String> fileDigests) {
        var cacheConfig = aiProperties.getCache();
        if (!cacheConfig.isEnabled()) {
            return CacheLookupResult.builder().hit(false).build();
        }

        // 1. 精确匹配
        String exactKey = buildCacheKey(question, fileDigests);
        CachedResponse exact = cache.getIfPresent(exactKey);
        if (exact != null) {
            exact.setHitCount(exact.getHitCount() + 1);
            log.info("[Cache] 精确命中: key={}, hitCount={}", exactKey, exact.getHitCount());
            return CacheLookupResult.builder()
                    .hit(true)
                    .cachedResponse(exact)
                    .similarity(1.0)
                    .cacheKey(exactKey)
                    .build();
        }

        // 2. 语义相似度匹配 — 对已缓存的问题计算余弦相似度
        try {
            List<Double> questionEmb = llmClient.embed(question);
            if (questionEmb == null || questionEmb.isEmpty()) {
                return CacheLookupResult.builder().hit(false).build();
            }

            CachedResponse bestMatch = null;
            double bestSim = 0.0;
            String bestKey = null;

            for (var entry : cache.asMap().entrySet()) {
                CachedResponse cached = entry.getValue();
                if (cached.getQuestionEmbedding() == null) continue;
                double sim = com.liang.xz.aiagent.agent.relevance.FileRelevanceService
                        .cosineSimilarity(questionEmb, cached.getQuestionEmbedding());
                if (sim > bestSim && sim >= cacheConfig.getSimilarityThreshold()) {
                    // 仅当用户本次携带文件时才强制校验文件指纹，否则忽略以保证无附件二次提问可语义命中
                    boolean hasFiles = fileDigests != null && !fileDigests.isEmpty();
                    String currentDigest = joinDigests(fileDigests);
                    if (!hasFiles || currentDigest.equals(cached.getFilesDigest())) {
                        bestSim = sim;
                        bestMatch = cached;
                        bestKey = entry.getKey();
                    }
                }
            }

            if (bestMatch != null) {
                bestMatch.setHitCount(bestMatch.getHitCount() + 1);
                log.info("[Cache] 语义命中: key={}, similarity={:.3f}, hitCount={}",
                        bestKey, bestSim, bestMatch.getHitCount());
                return CacheLookupResult.builder()
                        .hit(true)
                        .cachedResponse(bestMatch)
                        .similarity(bestSim)
                        .cacheKey(bestKey)
                        .build();
            }
        } catch (Exception e) {
            log.debug("[Cache] 语义匹配失败: {}", e.getMessage());
        }

        return CacheLookupResult.builder().hit(false).build();
    }

    /**
     * 存入缓存
     */
    public void put(String question, String answer, List<String> fileDigests) {
        var cacheConfig = aiProperties.getCache();
        if (!cacheConfig.isEnabled()) return;

        try {
            String key = buildCacheKey(question, fileDigests);
            List<Double> embedding = null;
            try {
                embedding = llmClient.embed(question);
            } catch (Exception embedEx) {
                // embedding 失败不影响精确命中（精确匹配不依赖向量），仅降级语义匹配能力
                log.debug("[Cache] 向量化失败，降级为仅精确匹配: {}", embedEx.getMessage());
            }
            CachedResponse cached = CachedResponse.builder()
                    .question(question)
                    .answer(answer)
                    .questionEmbedding(embedding)
                    .filesDigest(joinDigests(fileDigests))
                    .cachedAt(System.currentTimeMillis())
                    .hitCount(1)
                    .build();
            cache.put(key, cached);
            log.debug("[Cache] 存入: key={}", key);
        } catch (Exception e) {
            log.debug("[Cache] 存入失败: {}", e.getMessage());
        }
    }

    /**
     * 使缓存失效（按前缀）
     */
    public void invalidate(String prefix) {
        cache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }

    /**
     * 清空全部缓存
     */
    public void clearAll() {
        cache.invalidateAll();
        log.info("[Cache] 已清空全部缓存");
    }

    /**
     * 获取缓存统计
     */
    public String stats() {
        return String.format("size=%d, hits=%d, misses=%d, hitRate=%.2f%%",
                cache.estimatedSize(),
                cache.stats().hitCount(),
                cache.stats().missCount(),
                cache.stats().hitRate() * 100);
    }

    public Map<String, Object> statsMap() {
        long hits = cache.stats().hitCount();
        long misses = cache.stats().missCount();
        long total = hits + misses;
        double hitRate = total == 0 ? 0.0 : (hits * 1.0 / total);
        return Map.of(
                "totalEntries", (int) cache.estimatedSize(),
                "hitCount", hits,
                "missCount", misses,
                "hitRate", hitRate,
                "totalRequests", total,
                "estimatedSize", String.valueOf(cache.estimatedSize()),
                "maxSize", aiProperties.getCache().getMaxSize(),
                "evictionCount", cache.stats().evictionCount()
        );
    }

    private String buildCacheKey(String question, List<String> fileDigests) {
        String raw = normalizeQuestion(question) + "|" + joinDigests(fileDigests);
        return sha256(raw);
    }

    /**
     * 问题文本归一化：去空白、转小写，保证语义相同但格式略有差异的问题能精确命中缓存。
     */
    private String normalizeQuestion(String question) {
        if (question == null) {
            return "";
        }
        return question.replaceAll("\\s+", "").toLowerCase();
    }

    private String joinDigests(List<String> digests) {
        if (digests == null || digests.isEmpty()) return "";
        return String.join(",", digests);
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
