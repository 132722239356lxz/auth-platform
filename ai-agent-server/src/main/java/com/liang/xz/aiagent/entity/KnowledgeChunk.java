package com.liang.xz.aiagent.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>知识库文档块(Chunk)实体 —— 映射 ai_knowledge_chunk 表</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunk {

    @Schema(description = "主键(自增)")
    private Long id;

    @Schema(description = "关联的文档ID")
    private Long docId;

    @Schema(description = "分块索引(从0开始)")
    private Integer chunkIndex;

    @Schema(description = "分块文本内容")
    private String chunkText;

    @Schema(description = "向量数据(JSON)")
    private String vectorJson;

    @Schema(description = "Token数量")
    private Integer tokenCount;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
