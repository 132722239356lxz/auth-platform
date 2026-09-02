package com.liang.xz.aiagent.dto;

import com.liang.xz.aiagent.entity.KnowledgeDoc;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>知识库文档 VO</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocVO {
    private Long id;
    private String kbName;
    private String title;
    private String contentPreview;
    private String contentType;
    private String fileName;
    private Long fileSize;
    private Integer chunkCount;
    private String splitterType;
    private Integer chunkSize;
    private Integer overlap;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static KnowledgeDocVO from(KnowledgeDoc doc) {
        return KnowledgeDocVO.builder()
                .id(doc.getId())
                .kbName(doc.getKbName())
                .title(doc.getTitle())
                .contentPreview(doc.getContent() != null && doc.getContent().length() > 200
                        ? doc.getContent().substring(0, 200) + "..." : doc.getContent())
                .contentType(doc.getContentType())
                .fileName(doc.getFileName())
                .fileSize(doc.getFileSize())
                .chunkCount(doc.getChunkCount())
                .splitterType(doc.getSplitterType())
                .chunkSize(doc.getChunkSize())
                .overlap(doc.getOverlap())
                .status(doc.getStatus())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }
}
