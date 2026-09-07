package com.liang.xz.aiagent.vector;

import java.util.List;

/**
 * <p>向量存储抽象接口</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public interface VectorStore {

    /** 添加单个文档 */
    void add(VectorDocument doc);

    /** 批量添加文档 */
    void addAll(List<VectorDocument> docs);

    /** 相似度搜索(返回Top-K) */
    List<VectorDocument> search(List<Double> queryEmbedding, int topK);

    /** 带阈值过滤的相似度搜索 */
    List<VectorDocument> search(List<Double> queryEmbedding, int topK, double minSimilarity);

    /** 删除文档 */
    void delete(String id);

    /** 按知识库删除 */
    void deleteByKbName(String kbName);

    /** 按关联文档ID删除 */
    void deleteByDocId(String docId);

    /** 获取文档总数 */
    int size();

    /** 清空所有 */
    void clear();
}
