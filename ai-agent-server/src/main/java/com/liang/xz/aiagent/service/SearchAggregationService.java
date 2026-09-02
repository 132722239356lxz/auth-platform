package com.liang.xz.aiagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.aiagent.entity.SearchRecord;
import com.liang.xz.aiagent.llm.LlmClient;
import com.liang.xz.aiagent.rag.RetrievalPipeline;
import com.liang.xz.aiagent.repository.KnowledgeDocRepository;
import com.liang.xz.aiagent.search.LocalSearchEngine;
import com.liang.xz.aiagent.search.WebSearchClient;
import com.liang.xz.aiagent.vector.VectorDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>搜索聚合服务 — 统一编排本地/联网/RAG/混合搜索</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class SearchAggregationService {

    private final LocalSearchEngine localSearchEngine;
    private final WebSearchClient webSearchClient;
    private final RetrievalPipeline retrievalPipeline;
    private final LlmClient llmClient;
    private final KnowledgeDocRepository docRepository;
    private final ObjectMapper objectMapper;

    public SearchAggregationService(LocalSearchEngine localSearchEngine,
                                     WebSearchClient webSearchClient,
                                     RetrievalPipeline retrievalPipeline,
                                     LlmClient llmClient,
                                     KnowledgeDocRepository docRepository,
                                     ObjectMapper objectMapper) {
        this.localSearchEngine = localSearchEngine;
        this.webSearchClient = webSearchClient;
        this.retrievalPipeline = retrievalPipeline;
        this.llmClient = llmClient;
        this.docRepository = docRepository;
        this.objectMapper = objectMapper;
    }

    // ======================== 本地离线搜索 ========================

    public Map<String, Object> localSearch(String query, int maxResults) {
        long start = System.currentTimeMillis();
        List<LocalSearchEngine.SearchResult> results = localSearchEngine.search(query, maxResults);
        long elapsed = System.currentTimeMillis() - start;

        recordSearch(query, "LOCAL", results.size(), null, elapsed);
        return Map.of(
                "query", query,
                "totalResults", results.size(),
                "elapsedMs", elapsed,
                "results", results
        );
    }

    public Map<String, Object> localSuggest(String prefix, int limit) {
        return Map.of("suggestions", localSearchEngine.suggest(prefix, limit));
    }

    // ======================== 联网搜索 ========================

    public Map<String, Object> webSearch(String query, int maxResults) {
        long start = System.currentTimeMillis();
        List<WebSearchClient.WebSearchResult> results = webSearchClient.search(query, maxResults);
        long elapsed = System.currentTimeMillis() - start;

        recordSearch(query, "INTERNET", results.size(), null, elapsed);
        return Map.of(
                "query", query,
                "provider", webSearchClient.getClass().getSimpleName(),
                "totalResults", results.size(),
                "elapsedMs", elapsed,
                "results", results
        );
    }

    // ======================== 相似度检索 ========================

    /**
     * 纯相似度检索：仅返回向量检索上下文，不调用 LLM
     */
    public Map<String, Object> similaritySearch(String query, String kbName, int topK) {
        long start = System.currentTimeMillis();
        List<VectorDocument> context;
        if (kbName != null && !kbName.isEmpty()) {
            context = retrievalPipeline.retrieveInKb(kbName, query, topK);
        } else {
            context = retrievalPipeline.retrieve(query, topK);
        }
        long elapsed = System.currentTimeMillis() - start;

        List<Map<String, Object>> contextSummary = context.stream().map(doc -> Map.<String, Object>of(
                "text", doc.getText().length() > 300 ? doc.getText().substring(0, 300) + "..." : doc.getText(),
                "score", doc.getScore(),
                "metadata", doc.getMetadata()
        )).toList();

        return Map.of(
                "query", query,
                "kbName", kbName != null ? kbName : "",
                "searchType", "SIMILARITY",
                "contextCount", context.size(),
                "context", contextSummary,
                "elapsedMs", elapsed
        );
    }

    /**
     * 相似度 + 本地检索：向量检索 + Lucene 本地搜索，返回融合结果
     */
    public Map<String, Object> similarityLocalSearch(String query, String kbName, int topK) {
        long start = System.currentTimeMillis();

        List<VectorDocument> vectorResults = (kbName != null && !kbName.isEmpty())
                ? retrievalPipeline.retrieveInKb(kbName, query, topK)
                : retrievalPipeline.retrieve(query, topK);
        List<LocalSearchEngine.SearchResult> localResults = localSearchEngine.search(query, topK);

        // 过滤本地结果：只保留同一知识库的
        List<LocalSearchEngine.SearchResult> filteredLocal = localResults;
        if (kbName != null && !kbName.isEmpty()) {
            filteredLocal = localResults.stream()
                    .filter(r -> r.getMetadata() != null && kbName.equals(r.getMetadata().get("kbName")))
                    .toList();
        }

        long elapsed = System.currentTimeMillis() - start;

        List<Map<String, Object>> vectorSummary = vectorResults.stream().map(doc -> Map.<String, Object>of(
                "text", doc.getText().length() > 300 ? doc.getText().substring(0, 300) + "..." : doc.getText(),
                "score", doc.getScore(),
                "source", "VECTOR",
                "metadata", doc.getMetadata()
        )).toList();

        List<Map<String, Object>> localSummary = filteredLocal.stream().map(r -> Map.<String, Object>of(
                "title", r.getTitle(),
                "content", r.getContent() != null && r.getContent().length() > 300
                        ? r.getContent().substring(0, 300) + "..." : r.getContent(),
                "score", r.getScore(),
                "source", "LOCAL",
                "highlight", r.getHighlight()
        )).toList();

        recordSearch(query, "SIMILARITY_LOCAL", vectorResults.size() + filteredLocal.size(), null, elapsed);
        return Map.of(
                "query", query,
                "kbName", kbName != null ? kbName : "",
                "searchType", "SIMILARITY_LOCAL",
                "vectorResults", vectorSummary,
                "localResults", localSummary,
                "vectorCount", vectorResults.size(),
                "localCount", filteredLocal.size(),
                "elapsedMs", elapsed
        );
    }

    // ======================== RAG 搜索 ========================

    public Map<String, Object> ragSearch(String query, int topK) {
        long start = System.currentTimeMillis();

        // 检索上下文
        List<VectorDocument> context = retrievalPipeline.retrieve(query, topK);

        // 生成答案
        String answer = retrievalPipeline.generateAnswer(query, context);

        long elapsed = System.currentTimeMillis() - start;

        // 构建上下文摘要
        List<Map<String, Object>> contextSummary = context.stream().map(doc -> Map.<String, Object>of(
                "text", doc.getText().length() > 300 ? doc.getText().substring(0, 300) + "..." : doc.getText(),
                "score", doc.getScore(),
                "metadata", doc.getMetadata()
        )).toList();

        recordSearch(query, "RAG", context.size(), answer, elapsed);
        return Map.of(
                "query", query,
                "answer", answer,
                "contextCount", context.size(),
                "context", contextSummary,
                "elapsedMs", elapsed
        );
    }

    /**
     * 指定知识库 RAG
     */
    public Map<String, Object> ragSearchInKb(String kbName, String query, int topK) {
        long start = System.currentTimeMillis();
        List<VectorDocument> context = retrievalPipeline.retrieveInKb(kbName, query, topK);
        String answer = retrievalPipeline.generateAnswer(query, context);
        long elapsed = System.currentTimeMillis() - start;

        return Map.of(
                "kbName", kbName,
                "query", query,
                "answer", answer,
                "contextCount", context.size(),
                "elapsedMs", elapsed
        );
    }

    // ======================== 混合搜索 ========================

    /**
     * 混合搜索: 本地 + 联网 + 向量检索, 再由 LLM 融合答案
     */
    public Map<String, Object> hybridSearch(String query, int maxResults) {
        long start = System.currentTimeMillis();

        // 并行执行三种搜索
        List<LocalSearchEngine.SearchResult> localResults = localSearchEngine.search(query, 3);
        List<WebSearchClient.WebSearchResult> webResults = webSearchClient.search(query, 3);
        List<VectorDocument> ragContext = retrievalPipeline.retrieve(query, 3);

        // LLM 融合多源结果
        String fusedAnswer = fuseResults(query, localResults, webResults, ragContext);

        long elapsed = System.currentTimeMillis() - start;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("answer", fusedAnswer);
        result.put("elapsedMs", elapsed);
        result.put("localResults", localResults);
        result.put("webResults", webResults);
        result.put("ragContextCount", ragContext.size());

        recordSearch(query, "HYBRID",
                localResults.size() + webResults.size() + ragContext.size(),
                fusedAnswer, elapsed);

        return result;
    }

    private String fuseResults(String query,
                                List<LocalSearchEngine.SearchResult> local,
                                List<WebSearchClient.WebSearchResult> web,
                                List<VectorDocument> rag) {
        StringBuilder context = new StringBuilder();
        context.append("=== 本地知识库 ===\n");
        local.forEach(r -> context.append("- ").append(r.getTitle()).append(": ")
                .append(truncate(r.getContent(), 200)).append("\n"));
        context.append("\n=== 联网搜索 ===\n");
        web.forEach(r -> context.append("- ").append(r.getTitle()).append(": ")
                .append(truncate(r.getSnippet(), 200)).append("\n"));
        context.append("\n=== 向量检索 ===\n");
        rag.forEach(d -> context.append("- ").append(truncate(d.getText(), 200)).append("\n"));

        return llmClient.chat("""
                你是智能搜索助手。请基于以下多源搜索结果回答用户问题。
                要求:
                1. 综合所有信息源给出最准确的答案
                2. 标注信息来源(本地库/网络/知识库)
                3. 如果有冲突,说明不同来源的差异
                4. 回答简洁专业
                """, context + "\n\n用户问题: " + query);
    }

    // ======================== 辅助 ========================

    public long getLocalIndexCount() {
        return localSearchEngine.count();
    }

    public void indexLocalDocument(String id, String title, String content, Map<String, String> metadata) {
        localSearchEngine.index(id, title, content, metadata);
    }

    public void indexLocalDocuments(List<LocalSearchEngine.DocumentEntry> entries) {
        localSearchEngine.indexBatch(entries);
    }

    private void recordSearch(String query, String type, int count, String resultJson, long elapsed) {
        try {
            SearchRecord record = SearchRecord.builder()
                    .queryText(query)
                    .searchType(type)
                    .resultCount(count)
                    .resultsJson(resultJson)
                    .latencyMs(elapsed)
                    .build();
            docRepository.saveSearchRecord(record);
        } catch (Exception e) {
            log.warn("Failed to record search", e);
        }
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
