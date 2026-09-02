package com.liang.xz.aiagent.dto;

import com.liang.xz.aiagent.agent.ChatAgentService;

import java.util.Base64;

/**
 * <p>附件转换工具</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public final class AttachmentConverter {

    private AttachmentConverter() {
    }

    /**
     * 将 base64 Data URL 转换为图片附件
     *
     * @param dataUrl base64 图片数据，如 data:image/png;base64,xxx
     * @return 图片附件
     */
    public static ChatAgentService.ChatAttachment imageFromBase64(String dataUrl) {
        try {
            int commaIdx = dataUrl.indexOf(',');
            String base64 = commaIdx > 0 ? dataUrl.substring(commaIdx + 1) : dataUrl;
            String mimeType = "image/png";
            if (dataUrl.startsWith("data:")) {
                int semiIdx = dataUrl.indexOf(';');
                if (semiIdx > 5) {
                    mimeType = dataUrl.substring(5, semiIdx);
                }
            }
            return ChatAgentService.ChatAttachment.builder()
                    .type("image")
                    .name("粘贴图片")
                    .mimeType(mimeType)
                    .url(dataUrl)
                    .bytes(Base64.getDecoder().decode(base64))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("解析 base64 图片失败", e);
        }
    }
}
