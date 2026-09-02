package com.liang.xz.aiagent.vector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * <p>向量文档</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorDocument {
    /** 文档唯一ID */
    private String id;
    /** 原始文本 */
    private String text;
    /** 向量 */
    private List<Double> embedding;
    /** 元数据(来源/知识库名/文档标题等) */
    private Map<String, Object> metadata;
    /** 相似度得分(检索时填充) */
    private double score;
}
