package com.liang.xz.aiagent.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * <p>Embedding 响应</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmbeddingResponse {

    private String object;
    private List<EmbeddingData> data;
    private String model;
    private Usage usage;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmbeddingData {
        private String object;
        private int index;
        private List<Double> embedding;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {
        private int promptTokens;
        private int totalTokens;
    }

    /** 获取首个向量 */
    public List<Double> getFirstEmbedding() {
        if (data != null && !data.isEmpty()) {
            return data.get(0).getEmbedding();
        }
        return List.of();
    }
}
