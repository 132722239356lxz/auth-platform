package com.liang.xz.aiagent.dto;

import lombok.Data;

/**
 * <p>知识库文档请求</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
public class KnowledgeDocRequest {
    private String kbName;
    private String title;
    private String content;
    private String contentType;
    private String fileName;
    private String splitterType;
    private Integer chunkSize;
    private Integer overlap;
}
