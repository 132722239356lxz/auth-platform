package com.liang.xz.aiagent.vector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.aiagent.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * <p>MySQL 向量存储 — 向量以 JSON 存储在 MySQL LONGTEXT 列中，检索时用内存余弦相似度计算</p>
 * <p>适用场景: 不想引入 pgvector/Milvus/Redis 等外部组件的中小规模部署</p>
 * <p>与 InMemoryVectorStore 的区别: 不在内存驻留全量向量，每次检索实时查询 MySQL</p>
 *
 * <p>切换方式: ai-agent.vector.type=MYSQL</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
public class MysqlVectorStore implements VectorStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AiProperties.VectorStoreConfig config;

    public MysqlVectorStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, AiProperties aiProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.config = aiProperties.getVector();
    }

    @Override
    public void add(VectorDocument doc) {
        // MySQL 模式: 向量由 RetrievalPipeline 通过 KnowledgeDocRepository.saveChunks() 写入
        // 这里不需要额外操作
    }

    @Override
    public void addAll(List<VectorDocument> docs) {
        // 同上，批量写入由 RetrievalPipeline 通过 saveChunks() 处理
    }

    @Override
    public List<VectorDocument> search(List<Double> queryEmbedding, int topK) {
        return search(queryEmbedding, topK, config.getSimilarityThreshold());
    }

    @Override
    public List<VectorDocument> search(List<Double> queryEmbedding, int topK, double minSimilarity) {
        if (queryEmbedding == null || queryEmbedding.isEmpty()) {
            return List.of();
        }

        // 从 MySQL 查询所有已索引的 chunks（含向量 JSON）
        String sql = "SELECT c.id, c.doc_id, c.chunk_index, c.chunk_text, c.vector_json, "
                + "d.kb_name, d.title "
                + "FROM ai_knowledge_chunk c "
                + "JOIN ai_knowledge_doc d ON c.doc_id = d.id "
                + "WHERE d.status = 'INDEXED' AND c.vector_json IS NOT NULL";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        if (rows.isEmpty()) {
            return List.of();
        }

        PriorityQueue<VectorDocument> topDocs = new PriorityQueue<>(
                Comparator.comparingDouble(VectorDocument::getScore));

        for (Map<String, Object> row : rows) {
            try {
                String vectorJson = (String) row.get("vector_json");
                List<Double> embedding = objectMapper.readValue(vectorJson,
                        new TypeReference<List<Double>>() {});

                double similarity = cosineSimilarity(queryEmbedding, embedding);
                if (similarity >= minSimilarity) {
                    VectorDocument doc = VectorDocument.builder()
                            .id(String.valueOf(row.get("id")))
                            .text((String) row.get("chunk_text"))
                            .embedding(embedding)
                            .score(similarity)
                            .metadata(Map.of(
                                    "kbName", String.valueOf(row.get("kb_name")),
                                    "docId", String.valueOf(row.get("doc_id")),
                                    "chunkIndex", String.valueOf(row.get("chunk_index")),
                                    "title", String.valueOf(row.get("title"))
                            ))
                            .build();
                    topDocs.offer(doc);
                    if (topDocs.size() > topK) {
                        topDocs.poll();
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse vector for chunk id={}", row.get("id"), e);
            }
        }

        List<VectorDocument> results = new java.util.ArrayList<>(topDocs);
        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return results;
    }

    @Override
    public void delete(String id) {
        jdbcTemplate.update("DELETE FROM ai_knowledge_chunk WHERE id = ?", Long.parseLong(id));
    }

    @Override
    public void deleteByKbName(String kbName) {
        jdbcTemplate.update(
                "DELETE c FROM ai_knowledge_chunk c "
                        + "JOIN ai_knowledge_doc d ON c.doc_id = d.id "
                        + "WHERE d.kb_name = ?", kbName);
    }

    @Override
    public void deleteByDocId(String docId) {
        jdbcTemplate.update("DELETE FROM ai_knowledge_chunk WHERE doc_id = ?", Long.parseLong(docId));
    }

    @Override
    public int size() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_knowledge_chunk WHERE vector_json IS NOT NULL", Integer.class);
        return count != null ? count : 0;
    }

    @Override
    public void clear() {
        jdbcTemplate.update("DELETE FROM ai_knowledge_chunk");
    }

    // =========== 余弦相似度 ===========

    private double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.size() != b.size()) {
            return 0.0;
        }
        double dotProduct = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            dotProduct += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
