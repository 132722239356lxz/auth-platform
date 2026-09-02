package com.liang.xz.aiagent.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * <p>Chat Completion 请求</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    private String model;
    private List<Message> messages;
    private double temperature;
    private int maxTokens;
    private boolean stream;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;       // system / user / assistant
        private String content;
        /** OpenAI 多模态消息内容数组(文本+图片URL/base64)，设置后 content 字段失效 */
        private List<ContentPart> contentArray;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentPart {
        private String type;       // text / image_url
        private String text;       // type=text 时使用
        private ImageUrl imageUrl; // type=image_url 时使用
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageUrl {
        private String url;        // 图片 URL 或 base64 data URL
        private String detail;     // auto / low / high
    }

    public Map<String, Object> toRequestBody() {
        List<Map<String, Object>> msgList = messages.stream()
                .map(m -> {
                    Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("role", m.role);
                    if (m.contentArray != null && !m.contentArray.isEmpty()) {
                        List<Map<String, Object>> parts = new java.util.ArrayList<>();
                        for (ContentPart part : m.contentArray) {
                            Map<String, Object> p = new java.util.LinkedHashMap<>();
                            p.put("type", part.type);
                            if ("text".equals(part.type)) {
                                p.put("text", part.text);
                            } else if ("image_url".equals(part.type)) {
                                Map<String, Object> img = new java.util.LinkedHashMap<>();
                                img.put("url", part.imageUrl != null ? part.imageUrl.url : "");
                                if (part.imageUrl != null && part.imageUrl.detail != null) {
                                    img.put("detail", part.imageUrl.detail);
                                }
                                p.put("image_url", img);
                            }
                            parts.add(p);
                        }
                        map.put("content", parts);
                    } else {
                        map.put("content", m.content != null ? m.content : "");
                    }
                    return map;
                })
                .toList();
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", msgList);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        body.put("stream", stream);
        return body;
    }
}
