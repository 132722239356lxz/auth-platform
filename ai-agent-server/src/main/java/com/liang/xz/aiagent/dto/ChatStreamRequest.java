package com.liang.xz.aiagent.dto;

import com.liang.xz.aiagent.agent.ChatAgentService;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>流式对话 JSON 请求体（无文件附件时使用）</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
public class ChatStreamRequest {

    private String sessionId;
    private String question;
    private String userId;
    private boolean useMemory = true;
    private boolean thinking = true;
    private List<String> base64Images;

    /**
     * 将 JSON 请求体转换为 Service 层 {@link ChatAgentService.ChatRequest}
     */
    public ChatAgentService.ChatRequest toServiceRequest() {
        List<ChatAgentService.ChatAttachment> attachments = new ArrayList<>();
        if (base64Images != null) {
            for (String b64 : base64Images) {
                if (b64 == null || b64.isBlank()) {
                    continue;
                }
                attachments.add(AttachmentConverter.imageFromBase64(b64));
            }
        }
        return ChatAgentService.ChatRequest.builder()
                .sessionId(sessionId)
                .userId(userId)
                .question(question)
                .useMemory(useMemory)
                .thinking(thinking)
                .attachments(attachments)
                .build();
    }

}
