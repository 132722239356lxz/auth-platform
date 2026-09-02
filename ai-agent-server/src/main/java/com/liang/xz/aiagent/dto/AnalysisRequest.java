package com.liang.xz.aiagent.dto;

import lombok.Data;

/**
 * <p>分析查询请求</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
public class AnalysisRequest {
    /** 即时分析的自然语言问题 */
    private String query;
}
