package com.liang.xz.aiagent.agent.session;

import com.liang.xz.aiagent.config.AiProperties;
import com.liang.xz.aiagent.entity.ChatMessage;
import com.liang.xz.aiagent.llm.LlmClient;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>上下文压缩器 — 对过长对话历史进行智能压缩</p>
 *
 * <p>压缩策略：
 * <ol>
 *   <li>保留最近 N 条消息不压缩（默认10条 = 5轮对话）</li>
 *   <li>对更早的消息生成摘要，替换为一条 system 消息</li>
 *   <li>使用 LLM 生成简洁的阶段性摘要，保留关键决策和结论</li>
 *   <li>摘要格式: "前置对话摘要: [用户讨论了X，AI回答了Y，关键结论是Z]"</li>
 * </ol>
 * </p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
public class ContextCompressor {

    private final LlmClient llmClient;
    private final AiProperties aiProperties;

    public ContextCompressor(LlmClient llmClient, AiProperties aiProperties) {
        this.llmClient = llmClient;
        this.aiProperties = aiProperties;
    }

    @Data
    @Builder
    public static class CompressResult {
        private List<ChatMessage> compressed;
        private int originalCount;
        private int compressedCount;
        private String summary;
    }

    /**
     * 压缩超长对话历史
     *
     * @param messages 完整消息列表(按时间正序)
     * @return 压缩后的消息列表
     */
    public CompressResult compress(List<ChatMessage> messages) {
        var sessionConfig = aiProperties.getSession();
        int windowSize = sessionConfig.getContextWindowSize();
        int threshold = sessionConfig.getCompressThreshold();

        if (messages == null || messages.size() <= threshold) {
            return CompressResult.builder()
                    .compressed(new ArrayList<>(messages))
                    .originalCount(messages != null ? messages.size() : 0)
                    .compressedCount(messages != null ? messages.size() : 0)
                    .summary(null)
                    .build();
        }

        int keepFrom = Math.max(0, messages.size() - windowSize);
        List<ChatMessage> recent = new ArrayList<>(messages.subList(keepFrom, messages.size()));
        List<ChatMessage> older = messages.subList(0, keepFrom);

        log.info("[ContextCompressor] 压缩上下文: {}条 -> 保留{}条 + 压缩{}条",
                messages.size(), recent.size(), older.size());

        try {
            String summary = generateSummary(older);
            ChatMessage summaryMsg = ChatMessage.builder()
                    .role("system")
                    .content("【前置对话摘要】" + summary)
                    .build();
            List<ChatMessage> result = new ArrayList<>();
            result.add(summaryMsg);
            result.addAll(recent);

            log.info("[ContextCompressor] 压缩完成: {}条 -> {}条 (摘要+{}条保留)",
                    messages.size(), result.size(), recent.size());

            return CompressResult.builder()
                    .compressed(result)
                    .originalCount(messages.size())
                    .compressedCount(result.size())
                    .summary(summary)
                    .build();
        } catch (Exception e) {
            log.warn("[ContextCompressor] 压缩失败，回退到截断: {}", e.getMessage());
            // 回退：直接丢弃最旧的消息
            return CompressResult.builder()
                    .compressed(recent)
                    .originalCount(messages.size())
                    .compressedCount(recent.size())
                    .summary(null)
                    .build();
        }
    }

    private String generateSummary(List<ChatMessage> messages) {
        if (messages.isEmpty()) return "";

        String conversation = messages.stream()
                .map(m -> String.format("[%s]: %s",
                        m.getRole(),
                        truncate(m.getContent() != null ? m.getContent() : "", 300)))
                .collect(Collectors.joining("\n"));

        String prompt = """
                请将以下对话历史压缩为一段简洁的摘要（不超过500字）。
                要求：
                1. 保留用户的核心问题和意图
                2. 保留AI给出的关键结论和决策
                3. 保留使用的工具和查询的关键数据
                4. 忽略简单的确认/打招呼等闲聊内容
                5. 使用中文

                对话历史：
                """ + conversation;
        return llmClient.chat("你是一个对话摘要专家，请将以下对话压缩为精炼摘要。", prompt);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
