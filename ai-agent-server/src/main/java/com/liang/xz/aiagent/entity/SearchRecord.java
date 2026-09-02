package com.liang.xz.aiagent.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>搜索记录实体 —— 映射 ai_search_record 表</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRecord {

    @Schema(description = "主键(自增)")
    private Long id;

    @Schema(description = "搜索查询文本")
    private String queryText;

    @Schema(description = "搜索类型: LOCAL/INTERNET/RAG/HYBRID")
    private String searchType;

    @Schema(description = "结果数量")
    private Integer resultCount;

    @Schema(description = "搜索结果(JSON)")
    private String resultsJson;

    @Schema(description = "用户ID")
    private String userId;

    @Schema(description = "客户端IP")
    private String ipAddress;

    @Schema(description = "搜索耗时(毫秒)")
    private Long latencyMs;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
