package com.liang.xz.aiagent.dto;

import com.liang.xz.aiagent.entity.KnowledgeChunk;
import com.liang.xz.aiagent.entity.KnowledgeDoc;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>知识库文档详情 VO — 含完整内容 + chunks</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocDetailVO {
    private Long id;
    private String kbName;
    private String title;
    private String content;
    private String contentPreview;
    private String contentType;
    private String fileName;
    private Long fileSize;
    private Integer chunkCount;
    private String splitterType;
    private String splitterLabel;
    private Integer chunkSize;
    private Integer overlap;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<KnowledgeChunkVO> chunks;

    public static KnowledgeDocDetailVO from(KnowledgeDoc doc, List<KnowledgeChunk> chunks) {
        return KnowledgeDocDetailVO.builder()
                .id(doc.getId())
                .kbName(doc.getKbName())
                .title(doc.getTitle())
                .content(doc.getContent())
                .contentPreview(doc.getContent() != null && doc.getContent().length() > 200
                        ? doc.getContent().substring(0, 200) + "..." : doc.getContent())
                .contentType(doc.getContentType())
                .fileName(doc.getFileName())
                .fileSize(doc.getFileSize())
                .chunkCount(doc.getChunkCount())
                .splitterType(doc.getSplitterType())
                .splitterLabel(SplitterLabel.of(doc.getSplitterType()))
                .chunkSize(doc.getChunkSize())
                .overlap(doc.getOverlap())
                .status(doc.getStatus())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .chunks(chunks != null ? chunks.stream().map(KnowledgeChunkVO::from).toList() : null)
                .build();
    }

    public static String splitterLabel(String splitterType) {
        return SplitterLabel.of(splitterType);
    }

    private static class SplitterLabel {
        static String of(String type) {
            return switch (type != null ? type.toUpperCase() : "PARAGRAPH") {
                case "MARKDOWN" -> "Markdown 结构切片";
                case "HTML" -> "HTML 标签切片";
                case "CODE" -> "代码函数切片";
                case "JSON" -> "JSON 节点切片";
                case "CSV" -> "CSV 行切片";
                case "CHARACTER" -> "固定字符切片";
                default -> "段落语义切片";
            };
        }
    }
}
