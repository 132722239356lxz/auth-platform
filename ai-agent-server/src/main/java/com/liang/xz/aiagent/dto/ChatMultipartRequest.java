package com.liang.xz.aiagent.dto;

import com.liang.xz.aiagent.agent.ChatAgentService;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>多模态对话 multipart/form-data 请求体</p>
 * <p>Spring MVC 自动将 multipart 各部分绑定到对应字段（文件名与字段名一致即可）</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
public class ChatMultipartRequest {

    private String sessionId;
    private String question;
    private String userId;
    private boolean useMemory = true;
    private boolean thinking = true;
    private List<MultipartFile> files;
    private List<String> base64Images;

    /**
     * 将 multipart 请求体转换为 Service 层 {@link ChatAgentService.ChatRequest}
     */
    public ChatAgentService.ChatRequest toServiceRequest() {
        List<ChatAgentService.ChatAttachment> attachments = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    continue;
                }
                attachments.add(buildFileAttachment(file));
            }
        }
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

    private ChatAgentService.ChatAttachment buildFileAttachment(MultipartFile file) {
        try {
            String contentType = file.getContentType();
            boolean isImage = contentType != null && contentType.startsWith("image/");
            return ChatAgentService.ChatAttachment.builder()
                    .type(isImage ? "image" : "file")
                    .name(file.getOriginalFilename())
                    .mimeType(contentType)
                    .bytes(file.getBytes())
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("读取附件失败: " + file.getOriginalFilename(), e);
        }
    }

}
