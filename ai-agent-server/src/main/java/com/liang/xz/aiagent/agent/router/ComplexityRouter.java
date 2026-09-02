package com.liang.xz.aiagent.agent.router;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.aiagent.config.AiProperties;
import com.liang.xz.aiagent.llm.LlmClient;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * <p>基于LLM的问题复杂度路由器</p>
 *
 * <p>使用轻量LLM分析用户问题的复杂度，输出：
 * <ul>
 *   <li><b>complexity</b>: simple(简单问候/定义/查询) / medium(需要工具调用或分析) / complex(多步骤推理/多文件/对比分析)</li>
 *   <li><b>domain</b>: general / data_analysis / coding / knowledge_retrieval / creative / reasoning</li>
 *   <li><b>suggestedModel</b>: 建议使用的模型名称</li>
 *   <li><b>needsPlanning</b>: 是否需要任务拆分</li>
 *   <li><b>needsFileAnalysis</b>: 是否需要深度文件分析</li>
 * </ul>
 * </p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
public class ComplexityRouter {

    private final LlmClient llmClient;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public ComplexityRouter(LlmClient llmClient, AiProperties aiProperties,
                            ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    @Data
    @Builder
    public static class ComplexityResult {
        /** 复杂度: simple / medium / complex */
        private String complexity;
        /** 问题领域: general / data_analysis / coding / knowledge_retrieval / creative / reasoning */
        private String domain;
        /** 建议使用的供应商 (对应 providers 的 key) */
        private String suggestedProvider;
        /** 建议使用的模型名称 */
        private String suggestedModel;
        /** 是否需要任务拆分 */
        private boolean needsPlanning;
        /** 是否需要深度文件分析 */
        private boolean needsFileAnalysis;
        /** 建议的maxTokens */
        private int suggestedMaxTokens;
        /** 建议的temperature */
        private double suggestedTemperature;
    }

    /**
     * 评估问题复杂度并返回路由建议
     *
     * @param question     用户问题
     * @param fileInfo     附件信息(可为null)
     * @param contextDepth 当前上下文深度(消息数)
     */
    public ComplexityResult assess(String question, String fileInfo, int contextDepth) {
        var config = aiProperties.getComplexityRouting();
        if (!config.isEnabled()) {
            return buildDefault();
        }

        // 快速路径：极短问题直接判断为 simple
        if (question != null && question.length() < 15 && fileInfo == null && contextDepth == 0) {
            String lower = question.toLowerCase().trim();
            if (lower.startsWith("hello") || lower.startsWith("你好")
                    || lower.startsWith("hi") || lower.startsWith("帮助")
                    || lower.startsWith("help")) {
                return ComplexityResult.builder()
                        .complexity("simple")
                        .domain("general")
                        .suggestedProvider(config.getProvider())
                        .suggestedModel(config.getSimpleModel())
                        .needsPlanning(false)
                        .needsFileAnalysis(false)
                        .suggestedMaxTokens(config.getSimpleMaxTokens())
                        .suggestedTemperature(0.7)
                        .build();
            }
        }

        try {
            String systemPrompt = buildClassifierPrompt();
            String userPrompt = buildClassifierUserPrompt(question, fileInfo, contextDepth);
            String raw = llmClient.chat(systemPrompt, userPrompt);
            String json = extractJson(raw);
            Map<String, Object> result = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            String complexity = getString(result, "complexity", "medium");
            String domain = getString(result, "domain", "general");
            boolean needsPlanning = getBoolean(result, "needsPlanning", false);
            boolean needsFileAnalysis = getBoolean(result, "needsFileAnalysis", false);

            String model = selectModel(complexity, config);
            int maxTokens = selectMaxTokens(complexity, config);
            double temperature = "creative".equals(domain) ? 0.9 : 0.3;

            log.info("[ComplexityRouter] 评估结果: complexity={}, domain={}, needsPlanning={}, provider={}, model={}",
                    complexity, domain, needsPlanning, config.getProvider(), model);

            return ComplexityResult.builder()
                    .complexity(complexity)
                    .domain(domain)
                    .suggestedProvider(config.getProvider())
                    .suggestedModel(model)
                    .needsPlanning(needsPlanning)
                    .needsFileAnalysis(needsFileAnalysis)
                    .suggestedMaxTokens(maxTokens)
                    .suggestedTemperature(temperature)
                    .build();
        } catch (Exception e) {
            log.warn("[ComplexityRouter] LLM路由评估失败，使用默认配置: {}", e.getMessage());
            return buildDefault();
        }
    }

    private String buildClassifierPrompt() {
        return """
                你是一个问题复杂度分析专家。请分析用户问题并返回JSON格式的评估结果。
                
                评估维度：
                1. complexity (复杂度):
                   - simple: 简单问候/帮助/定义/单一查询，无需工具调用
                   - medium: 需要工具调用、简单分析、单文件处理
                   - complex: 多步骤推理、多文件对比、数据深度分析、需要任务拆分
                
                2. domain (领域):
                   - general: 一般问答
                   - data_analysis: 数据分析/统计/报表
                   - coding: 编程/代码相关
                   - knowledge_retrieval: 知识库检索/文档查询
                   - creative: 内容生成/创作
                   - reasoning: 复杂推理/诊断
                
                3. needsPlanning: 是否需要拆分为子任务 (true/false)
                4. needsFileAnalysis: 是否需要深度文件分析 (true/false)
                
                只返回JSON对象，不要任何其他文字。
                """;
    }

    private String buildClassifierUserPrompt(String question, String fileInfo, int contextDepth) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户问题: ").append(question).append("\n");
        if (fileInfo != null && !fileInfo.isBlank()) {
            sb.append("附件信息: ").append(fileInfo).append("\n");
        }
        if (contextDepth > 0) {
            sb.append("当前上下文消息数: ").append(contextDepth).append("\n");
        }
        sb.append("\n请返回JSON评估结果。");
        return sb.toString();
    }

    private String selectModel(String complexity, AiProperties.ComplexityRoutingConfig config) {
        String model = switch (complexity) {
            case "simple" -> config.getSimpleModel();
            case "complex" -> config.getComplexModel();
            default -> config.getMediumModel();
        };
        // 模型名未配置时返回 null，由上层回退到数据库供应商的默认模型
        return (model == null || model.isBlank()) ? null : model;
    }

    private int selectMaxTokens(String complexity, AiProperties.ComplexityRoutingConfig config) {
        return switch (complexity) {
            case "simple" -> config.getSimpleMaxTokens();
            case "complex" -> config.getComplexMaxTokens();
            default -> config.getMediumMaxTokens();
        };
    }

    private ComplexityResult buildDefault() {
        var config = aiProperties.getComplexityRouting();
        String model = config.getMediumModel();
        return ComplexityResult.builder()
                .complexity("medium")
                .domain("general")
                .suggestedProvider(config.getProvider())
                .suggestedModel((model == null || model.isBlank()) ? null : model)
                .needsPlanning(false)
                .needsFileAnalysis(false)
                .suggestedMaxTokens(config.getMediumMaxTokens())
                .suggestedTemperature(0.7)
                .build();
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object val = map.get(key);
        if (val instanceof Boolean b) return b;
        return defaultValue;
    }

    private String extractJson(String raw) {
        if (raw == null) return "{}";
        String text = raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return "{}";
    }
}
