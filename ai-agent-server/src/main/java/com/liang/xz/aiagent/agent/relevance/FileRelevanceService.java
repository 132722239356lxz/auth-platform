package com.liang.xz.aiagent.agent.relevance;

import com.liang.xz.aiagent.agent.ChatAgentService.ChatAttachment;
import com.liang.xz.aiagent.config.AiProperties;
import com.liang.xz.aiagent.llm.LlmClient;
import com.liang.xz.aiagent.service.FileParserService;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * <p>多文件相关性评分服务</p>
 *
 * <p>当用户上传多个文件并提问时，通过语义嵌入计算每个文件与问题的相关性：
 * <ol>
 *   <li>对每个文件提取文本内容</li>
 *   <li>计算问题与各文件的 embedding 余弦相似度</li>
 *   <li>按相似度降序排列，标记最相关的文件</li>
 *   <li>高相关性文件作为主要上下文注入 prompt</li>
 * </ol>
 * </p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class FileRelevanceService {

    private final LlmClient llmClient;
    private final FileParserService fileParserService;
    private final AiProperties aiProperties;

    public FileRelevanceService(LlmClient llmClient, FileParserService fileParserService,
                                AiProperties aiProperties) {
        this.llmClient = llmClient;
        this.fileParserService = fileParserService;
        this.aiProperties = aiProperties;
    }

    @Data
    @Builder
    public static class FileRelevance {
        /** 文件名 */
        private String fileName;
        /** 文件类型 */
        private String fileType;
        /** 与问题的相似度(0-1) */
        private double similarity;
        /** 文件内容摘要(前500字符) */
        private String contentSummary;
        /** 是否为最相关文件 */
        private boolean isPrimary;
        /** 相关性等级: high/medium/low */
        private String relevanceLevel;
    }

    @Data
    @Builder
    public static class RelevanceResult {
        /** 各文件的相关性评分列表(按相似度降序) */
        private List<FileRelevance> files;
        /** 最相关文件的上下文文本 */
        private String primaryContext;
        /** 所有文件的汇总上下文 */
        private String aggregatedContext;
    }

    /**
     * 计算多文件与问题的相关性
     *
     * @param question    用户问题
     * @param attachments 附件列表
     * @return 相关性分析结果
     */
    public RelevanceResult analyze(String question, List<ChatAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return RelevanceResult.builder()
                    .files(List.of())
                    .primaryContext("")
                    .aggregatedContext("")
                    .build();
        }

        // 单文件直接返回，无需计算相关性
        if (attachments.size() == 1) {
            ChatAttachment att = attachments.get(0);
            String content = extractContent(att);
            return RelevanceResult.builder()
                    .files(List.of(FileRelevance.builder()
                            .fileName(att.getName())
                            .fileType(att.getType())
                            .similarity(1.0)
                            .contentSummary(truncate(content, 500))
                            .isPrimary(true)
                            .relevanceLevel("high")
                            .build()))
                    .primaryContext(content)
                    .aggregatedContext(content)
                    .build();
        }

        // 多文件：计算 embedding 相似度
        try {
            List<Double> questionEmbedding = llmClient.embed(question);
            if (questionEmbedding == null || questionEmbedding.isEmpty()) {
                return fallbackResult(attachments);
            }

            List<FileRelevance> relevances = new ArrayList<>();
            for (ChatAttachment att : attachments) {
                String content = extractContent(att);
                if (content == null || content.isBlank()) {
                    relevances.add(FileRelevance.builder()
                            .fileName(att.getName())
                            .fileType(att.getType())
                            .similarity(0.0)
                            .contentSummary("(无法解析内容)")
                            .isPrimary(false)
                            .relevanceLevel("low")
                            .build());
                    continue;
                }
                List<Double> fileEmbedding = llmClient.embed(content.substring(
                        0, Math.min(content.length(), 2000)));
                double sim = cosineSimilarity(questionEmbedding, fileEmbedding);
                String level = sim > 0.7 ? "high" : sim > 0.4 ? "medium" : "low";
                relevances.add(FileRelevance.builder()
                        .fileName(att.getName())
                        .fileType(att.getType())
                        .similarity(sim)
                        .contentSummary(truncate(content, 500))
                        .isPrimary(false)
                        .relevanceLevel(level)
                        .build());
            }

            // 按相似度降序排列
            relevances.sort(Comparator.comparingDouble(FileRelevance::getSimilarity).reversed());

            // 标记最相关文件
            if (!relevances.isEmpty()) {
                relevances.get(0).setPrimary(true);
            }

            // 构建上下文
            String primaryCtx = buildPrimaryContext(relevances);
            String aggregatedCtx = buildAggregatedContext(relevances, question);

            log.info("[FileRelevance] 分析完成, 共{}个文件, 最相关: {} (similarity={:.3f})",
                    relevances.size(),
                    relevances.isEmpty() ? "N/A" : relevances.get(0).getFileName(),
                    relevances.isEmpty() ? 0 : relevances.get(0).getSimilarity());

            return RelevanceResult.builder()
                    .files(relevances)
                    .primaryContext(primaryCtx)
                    .aggregatedContext(aggregatedCtx)
                    .build();

        } catch (Exception e) {
            log.warn("[FileRelevance] 相似度计算失败，回退到基础模式: {}", e.getMessage());
            return fallbackResult(attachments);
        }
    }

    private String extractContent(ChatAttachment att) {
        if (att.getBytes() == null || att.getBytes().length == 0) {
            return att.getName();
        }
        try {
            // 图片：调用 AI 视觉模型描述图片内容
            if ("image".equals(att.getType())) {
                log.info("[FileRelevance] 调用AI分析图片: {}", att.getName());
                String description = llmClient.describeImage(att.getBytes(), att.getMimeType());
                if (description != null && !description.isBlank() && !description.startsWith("[LLM")) {
                    log.info("[FileRelevance] 图片分析完成: {} -> {} chars", att.getName(), description.length());
                    return "[图片描述] " + description;
                }
                log.warn("[FileRelevance] AI图片分析返回异常或为空: {}", att.getName());
                return "[图片附件: " + att.getName() + " (AI分析失败)]";
            }
            // 文件：先尝试 Tika 解析，失败则 AI 兜底
            if ("file".equals(att.getType())) {
                FileParserService.ParseResult pr = fileParserService.parse(att.getBytes(), att.getName());
                if (pr.getText() != null && !pr.getText().isBlank()) {
                    return pr.getText();
                }
                // Tika 解析失败，尝试 AI 分析
                log.info("[FileRelevance] Tika解析失败，调用AI分析文件: {}", att.getName());
                String aiContent = llmClient.describeFile(att.getBytes(), att.getName(), att.getMimeType());
                if (aiContent != null && !aiContent.isBlank() && !aiContent.startsWith("[LLM")) {
                    return "[AI文件分析] " + aiContent;
                }
                return "[文件: " + att.getName() + " (无法解析)]";
            }
        } catch (Exception e) {
            log.warn("[FileRelevance] 文件解析失败: {} - {}", att.getName(), e.getMessage());
        }
        return att.getName();
    }

    private String buildPrimaryContext(List<FileRelevance> relevances) {
        if (relevances.isEmpty()) return "";
        FileRelevance primary = relevances.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append("【最相关文件】").append(primary.getFileName())
                .append(" (相似度: ").append(String.format("%.2f", primary.getSimilarity())).append(")\n");
        sb.append(primary.getContentSummary());
        return sb.toString();
    }

    private String buildAggregatedContext(List<FileRelevance> relevances, String question) {
        if (relevances.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("用户上传了").append(relevances.size()).append("个文件，提问: ").append(question).append("\n\n");
        sb.append("各文件与问题的相关度排序:\n");
        for (int i = 0; i < relevances.size(); i++) {
            FileRelevance fr = relevances.get(i);
            sb.append(i + 1).append(". ").append(fr.getFileName())
                    .append(" [" + fr.getRelevanceLevel() + ", sim=")
                    .append(String.format("%.3f", fr.getSimilarity())).append("]\n");
        }
        sb.append("\n请优先基于最相关的文件回答，必要时参考其他文件。");
        return sb.toString();
    }

    private RelevanceResult fallbackResult(List<ChatAttachment> attachments) {
        List<FileRelevance> list = new ArrayList<>();
        StringBuilder ctx = new StringBuilder();
        for (ChatAttachment att : attachments) {
            String content = extractContent(att);
            list.add(FileRelevance.builder()
                    .fileName(att.getName())
                    .fileType(att.getType())
                    .similarity(0.5)
                    .contentSummary(truncate(content, 500))
                    .isPrimary(false)
                    .relevanceLevel("medium")
                    .build());
            ctx.append("[").append(att.getType()).append("] ")
                    .append(att.getName()).append(": ")
                    .append(truncate(content, 300)).append("\n");
        }
        if (!list.isEmpty()) {
            list.get(0).setPrimary(true);
        }
        return RelevanceResult.builder()
                .files(list)
                .primaryContext(list.isEmpty() ? "" : list.get(0).getContentSummary())
                .aggregatedContext(ctx.toString())
                .build();
    }

    /**
     * 余弦相似度计算
     */
    public static double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0.0;
        int minLen = Math.min(a.size(), b.size());
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < minLen; i++) {
            double va = a.get(i);
            double vb = b.get(i);
            dotProduct += va * vb;
            normA += va * va;
            normB += vb * vb;
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
