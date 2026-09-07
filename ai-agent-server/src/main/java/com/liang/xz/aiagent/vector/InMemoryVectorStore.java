package com.liang.xz.aiagent.vector;

import com.liang.xz.aiagent.config.AiProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * <p>内存向量存储 — 基于余弦相似度的轻量级实现</p>
 * <p>生产环境可替换为 pgvector / Redis / Milvus / ChromaDB 等</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
public class InMemoryVectorStore implements VectorStore {

    private final List<VectorDocument> documents = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> idIndex = new ConcurrentHashMap<>();
    private final AiProperties.VectorStoreConfig config;

    public InMemoryVectorStore(AiProperties aiProperties) {
        this.config = aiProperties.getVector();
    }

    @Override
    public void add(VectorDocument doc) {
        if (doc.getEmbedding() == null || doc.getEmbedding().isEmpty()) {
            log.warn("Skip document with empty embedding: {}", doc.getId());
            return;
        }
        documents.add(doc);
        idIndex.put(doc.getId(), documents.size() - 1);
    }

    @Override
    public void addAll(List<VectorDocument> docs) {
        docs.forEach(this::add);
    }

    @Override
    public List<VectorDocument> search(List<Double> queryEmbedding, int topK) {
        return search(queryEmbedding, topK, config.getSimilarityThreshold());
    }

    @Override
    public List<VectorDocument> search(List<Double> queryEmbedding, int topK, double minSimilarity) {
        if (documents.isEmpty() || queryEmbedding == null || queryEmbedding.isEmpty()) {
            return List.of();
        }

        // 计算所有文档与查询向量的余弦相似度
        PriorityQueue<VectorDocument> topDocs = new PriorityQueue<>(
                Comparator.comparingDouble(VectorDocument::getScore));

        for (VectorDocument doc : documents) {
            double similarity = cosineSimilarity(queryEmbedding, doc.getEmbedding());
            if (similarity >= minSimilarity) {
                doc.setScore(similarity);
                topDocs.offer(doc);
                if (topDocs.size() > topK) {
                    topDocs.poll(); // 移除最低分的
                }
            }
        }

        // 按相似度降序排列
        List<VectorDocument> results = new ArrayList<>(topDocs);
        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return results;
    }

    @Override
    public void delete(String id) {
        Integer idx = idIndex.remove(id);
        if (idx != null && idx < documents.size()) {
            documents.set(idx, null);
        }
    }

    @Override
    public void deleteByKbName(String kbName) {
        documents.removeIf(doc -> {
            if (doc.getMetadata() != null && kbName.equals(doc.getMetadata().get("kbName"))) {
                idIndex.remove(doc.getId());
                return true;
            }
            return false;
        });
    }

    @Override
    public int size() {
        return (int) documents.stream().filter(Objects::nonNull).count();
    }

    @Override
    public void deleteByDocId(String docId) {
        documents.removeIf(doc -> {
            if (doc != null && doc.getMetadata() != null && docId.equals(doc.getMetadata().get("docId"))) {
                idIndex.remove(doc.getId());
                return true;
            }
            return false;
        });
    }

    @Override
    public void clear() {
        documents.clear();
        idIndex.clear();
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
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
