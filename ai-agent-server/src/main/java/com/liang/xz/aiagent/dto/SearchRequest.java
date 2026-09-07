package com.liang.xz.aiagent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <p>搜索请求</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
public class SearchRequest {
    /** 搜索查询 */
    private String query;
    /** 搜索类型: LOCAL / INTERNET / RAG / HYBRID */
    private String searchType;
    /** 知识库名称(RAG模式时使用) */
    private String kbName;
    /** 最大返回数 */
    private Integer maxResults;
}
