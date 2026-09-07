package com.liang.xz.aiagent.dto;

import com.liang.xz.aiagent.entity.KnowledgeChunk;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>知识库 Chunk VO</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunkVO {
    private Long id;
    private Long docId;
    private Integer chunkIndex;
    private String chunkText;
    private Integer tokenCount;
    private LocalDateTime createdAt;

    public static KnowledgeChunkVO from(KnowledgeChunk chunk) {
        return KnowledgeChunkVO.builder()
                .id(chunk.getId())
                .docId(chunk.getDocId())
                .chunkIndex(chunk.getChunkIndex())
                .chunkText(chunk.getChunkText())
                .tokenCount(chunk.getTokenCount())
                .createdAt(chunk.getCreatedAt())
                .build();
    }
}
