package com.liang.xz.aiagent.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>知识库文档实体 —— 映射 ai_knowledge_doc 表</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDoc {

    @Schema(description = "主键(自增)")
    private Long id;

    @Schema(description = "所属知识库名称")
    private String kbName;

    @Schema(description = "文档标题")
    private String title;

    @Schema(description = "文档内容")
    private String content;

    @Schema(description = "内容类型: TEXT/MARKDOWN/HTML")
    private String contentType;

    @Schema(description = "原始文件名")
    private String fileName;

    @Schema(description = "文件大小(字节)")
    private Long fileSize;

    @Schema(description = "分块数量")
    private Integer chunkCount;

    @Schema(description = "分块策略: PARAGRAPH/SENTENCE/FIXED")
    private String splitterType;

    @Schema(description = "分块大小")
    private Integer chunkSize;

    @Schema(description = "分块重叠大小")
    private Integer overlap;

    @Schema(description = "状态: PENDING/PROCESSING/INDEXED/FAILED")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
