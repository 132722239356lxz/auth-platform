package com.liang.xz.aiagent.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>AI对话会话实体 —— 映射 ai_chat_session 表</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {

    @Schema(description = "主键(自增)")
    private Long id;

    @Schema(description = "会话唯一标识")
    private String sessionId;

    @Schema(description = "用户ID")
    private String userId;

    @Schema(description = "会话标题")
    private String title;

    @Schema(description = "状态: ACTIVE/CLOSED")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
