package com.liang.xz.aiagent.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>AI对话消息实体 —— 映射 ai_chat_message 表, 用于持久化 ChatMemory</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Schema(description = "主键(自增)")
    private Long id;

    @Schema(description = "会话ID")
    private String sessionId;

    @Schema(description = "用户ID(关联系统用户, 历史记录用于展示发送者)")
    private String userId;

    @Schema(description = "用户名称(发送者昵称/用户名, 冗余存储便于历史展示)")
    private String userName;

    @Schema(description = "角色: USER/ASSISTANT/SYSTEM")
    private String role;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "附件信息(JSON)")
    private String attachmentsJson;

    @Schema(description = "使用的工具列表")
    private String toolsUsed;

    @Schema(description = "响应耗时(毫秒)")
    private Long latencyMs;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
