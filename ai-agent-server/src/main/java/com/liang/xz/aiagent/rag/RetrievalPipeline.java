package com.liang.xz.aiagent.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.aiagent.config.AiProperties;
import com.liang.xz.aiagent.entity.KnowledgeChunk;
import com.liang.xz.aiagent.llm.LlmClient;
import com.liang.xz.aiagent.repository.KnowledgeDocRepository;
import com.liang.xz.aiagent.service.AiInvokeLogService;
import com.liang.xz.aiagent.vector.VectorDocument;
import com.liang.xz.aiagent.vector.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * <p>RAG 管道 — 文档加载 → 分割 → 向量化 → 存储 → 检索增强生成</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
public class RetrievalPipeline {

    private final TextSplitter textSplitter;
    private final LlmClient llmClient;
    private final VectorStore vectorStore;
    private final KnowledgeDocRepository docRepository;
    private final ObjectMapper objectMapper;
    private final AiProperties.VectorStoreConfig vectorConfig;
    private final RerankService rerankService;
    private final AiProperties aiProperties;
    private final AiInvokeLogService aiInvokeLogService;

    public RetrievalPipeline(TextSplitter textSplitter, LlmClient llmClient,
                             VectorStore vectorStore, KnowledgeDocRepository docRepository,
                             ObjectMapper objectMapper,
                             AiProperties aiProperties,
                             RerankService rerankService,
                             AiInvokeLogService aiInvokeLogService) {
        this.textSplitter = textSplitter;
        this.llmClient = llmClient;
        this.vectorStore = vectorStore;
        this.docRepository = docRepository;
        this.objectMapper = objectMapper;
        this.vectorConfig = aiProperties.getVector();
        this.aiProperties = aiProperties;
        this.rerankService = rerankService;
        this.aiInvokeLogService = aiInvokeLogService;
    }

    /**
     * 文档入库流程: 按策略分割 → 向量化 → 存储(内存+MySQL持久化)
     *
     * @return chunk 数量
     */
    public int indexDocument(String kbName, String docId, String title, String content,
                             TextSplitter.SplitStrategy strategy, int chunkSize, int overlap) {
        List<String> chunks = textSplitter.split(content, strategy, chunkSize, overlap);
        if (chunks.isEmpty()) return 0;

        // 批量向量化
        List<List<Double>> embeddings = llmClient.embedBatch(chunks);
        if (embeddings == null || embeddings.isEmpty()) {
            throw new RuntimeException("向量化失败: 嵌入服务返回空结果，请检查 LLM / Embedding 服务配置");
        }
        if (embeddings.size() != chunks.size()) {
            throw new RuntimeException("向量化失败: chunk 数量与 embedding 数量不匹配 (chunks=" + chunks.size()
                    + ", embeddings=" + embeddings.size() + ")");
        }

        // 构建Chunk对象列表(用于DB持久化) 和 向量文档(用于内存存储)
        List<KnowledgeChunk> chunkEntities = new ArrayList<>();
        long docIdLong = Long.parseLong(docId);

        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("kbName", kbName);
            metadata.put("docId", docId);
            metadata.put("title", title);
            metadata.put("chunkIndex", String.valueOf(i));
            metadata.put("totalChunks", String.valueOf(chunks.size()));

            // 短期记忆: 加入内存向量存储
            VectorDocument vecDoc = VectorDocument.builder()
                    .id(UUID.randomUUID().toString().replace("-", ""))
                    .text(chunks.get(i))
                    .embedding(embeddings.get(i))
                    .metadata(metadata)
                    .build();
            vectorStore.add(vecDoc);

            // 长期记忆: 构建DB实体
            String vectorJson;
            try {
                vectorJson = objectMapper.writeValueAsString(embeddings.get(i));
            } catch (Exception e) {
                log.error("向量序列化失败", e);
                vectorJson = null;
            }

            KnowledgeChunk chunk = KnowledgeChunk.builder()
                    .docId(docIdLong)
                    .chunkIndex(i)
                    .chunkText(chunks.get(i))
                    .vectorJson(vectorJson)
                    .tokenCount(chunks.get(i).length() / 2) // 粗略估算token数
                    .build();
            chunkEntities.add(chunk);
        }

        // 批量写入MySQL(长期记忆持久化)。失败需抛出异常，交由上层统一回滚/清理，
        // 避免静默吞掉导致分块缺失、文档状态不一致。
        docRepository.saveChunks(chunkEntities);
        log.info("长期记忆持久化: docId={} 写入{}个chunks到MySQL", docId, chunkEntities.size());

        log.info("Indexed document [{}] {} chunks into kb={} strategy={}", title, chunks.size(), kbName, strategy);
        return chunks.size();
    }

    /**
     * 增量添加文档
     */
    public int appendChunks(String kbName, String docId, String title, List<String> chunks,
                            List<List<Double>> embeddings) {
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("kbName", kbName);
            metadata.put("docId", docId);
            metadata.put("title", title);
            metadata.put("chunkIndex", String.valueOf(i));

            VectorDocument doc = VectorDocument.builder()
                    .id(UUID.randomUUID().toString().replace("-", ""))
                    .text(chunks.get(i))
                    .embedding(embeddings.get(i))
                    .metadata(metadata)
                    .build();
            vectorStore.add(doc);
        }
        return chunks.size();
    }

    // ==================== 检索 ====================

    /**
     * 检索相似文档块（向量宽召回 → 重排序精排）
     */
    public List<VectorDocument> retrieve(String query, int topK) {
        List<Double> queryEmbedding = llmClient.embed(query);
        if (queryEmbedding.isEmpty()) {
            log.warn("Failed to get query embedding");
            return List.of();
        }
        // 宽召回：向量检索先召回更多候选，再交给重排序模型精排取最终 topK
        int candidateCount = topK * Math.max(1, aiProperties.getSearch().getRetrieveTopK());
        List<VectorDocument> candidates = vectorStore.search(queryEmbedding, candidateCount);
        if (candidates.isEmpty()) {
            return candidates;
        }
        List<VectorDocument> reranked = rerankService.rerank(query, candidates);
        if (reranked.size() > topK) {
            reranked = new ArrayList<>(reranked.subList(0, topK));
        }
        return reranked;
    }

    /**
     * 在指定知识库中检索（向量宽召回 → 重排序精排）
     */
    public List<VectorDocument> retrieveInKb(String kbName, String query, int topK) {
        List<VectorDocument> reranked = retrieve(query, topK * 2); // 宽召回+重排后再按知识库过滤
        return reranked.stream()
                .filter(d -> d.getMetadata() != null && kbName.equals(d.getMetadata().get("kbName")))
                .limit(topK)
                .toList();
    }

    // ==================== 增强生成 ====================

    /**
     * RAG 核心: 检索 + LLM 生成答案
     */
    public String ragQuery(String query, int topK) {
        List<VectorDocument> retrieved = retrieve(query, topK);
        return generateAnswer(query, retrieved);
    }

    /**
     * 指定知识库的 RAG
     */
    public String ragQueryInKb(String kbName, String query, int topK) {
        List<VectorDocument> retrieved = retrieveInKb(kbName, query, topK);
        return generateAnswer(query, retrieved);
    }

    /**
     * 使用检索结果 + LLM 生成答案（并记录 AI 调用日志）
     */
    public String generateAnswer(String query, List<VectorDocument> contextDocs) {
        long start = System.currentTimeMillis();
        String answer;
        boolean success = true;
        String errorMsg = null;
        try {
            if (contextDocs.isEmpty()) {
                answer = llmClient.chat(
                        "你是一个知识库助手。请诚实回答：如果不知道就说不确定。",
                        query);
            } else {
                // 构建上下文
                StringBuilder context = new StringBuilder();
                for (int i = 0; i < contextDocs.size(); i++) {
                    VectorDocument doc = contextDocs.get(i);
                    context.append("【参考资料").append(i + 1).append("】");
                    if (doc.getMetadata() != null && doc.getMetadata().containsKey("title")) {
                        context.append(" 来源: ").append(doc.getMetadata().get("title"));
                    }
                    context.append("\n").append(doc.getText()).append("\n\n");
                }

                String systemPrompt = """
                        你是一个专业的知识库助手。请根据以下参考资料回答用户问题。
                        规则：
                        1. 优先使用参考资料中的信息
                        2. 如果参考资料不足以回答问题，请明确说明并给出你的最佳建议
                        3. 引用资料时标注来源
                        4. 回答要简洁、准确、有条理
                        """;

                String userMessage = String.format("""
                        参考资料：
                        %s
                        
                        用户问题：%s
                        
                        请根据以上参考资料回答问题：""", context, query);

                answer = llmClient.chat(systemPrompt, userMessage);
            }
        } catch (Exception e) {
            success = false;
            errorMsg = e.getMessage();
            answer = null;
            log.warn("[RetrievalPipeline] 生成答案失败：{}", e.getMessage());
        }

        // 记录 LLM 生成调用日志（RAG 智能回答的可观测入口）
        StringBuilder refs = new StringBuilder();
        for (int i = 0; i < Math.min(contextDocs.size(), 5); i++) {
            Map<String, Object> meta = contextDocs.get(i).getMetadata();
            if (meta != null && meta.containsKey("title")) {
                refs.append(meta.get("title")).append("; ");
            }
        }
        aiInvokeLogService.logLlm(null, null, contextDocs.isEmpty() ? "无上下文" : "RAG检索",
                false, null, null, null, 0, refs.toString(),
                query, answer, System.currentTimeMillis() - start, success, errorMsg);
        return answer == null ? "生成答案失败：" + (errorMsg == null ? "未知错误" : errorMsg) : answer;
    }

    // ==================== 知识库管理 ====================

    /**
     * 按关联文档ID删除向量
     */
    public void deleteByDocId(Long docId) {
        vectorStore.deleteByDocId(String.valueOf(docId));
        log.info("Deleted vector documents by docId: {}", docId);
    }

    /**
     * 删除知识库
     */
    public void deleteKnowledgeBase(String kbName) {
        vectorStore.deleteByKbName(kbName);
        log.info("Deleted knowledge base: {}", kbName);
    }

    /**
     * 获取向量存储统计
     */
    public Map<String, Object> getStats() {
        return Map.of("totalDocuments", vectorStore.size());
    }
}
