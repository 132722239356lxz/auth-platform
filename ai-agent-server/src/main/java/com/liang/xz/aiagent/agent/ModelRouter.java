package com.liang.xz.aiagent.agent;

import com.liang.xz.aiagent.agent.router.ComplexityRouter;
import com.liang.xz.aiagent.config.AiProperties;
import com.liang.xz.aiagent.llm.ProviderFailoverService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * <p>模型路由器 — 关键词快速匹配 + LLM复杂度路由双模式</p>
 *
 * <p>路由策略（优先级从高到低）:
 * <ol>
 *   <li><b>LLM复杂度路由</b>: 通过 ComplexityRouter 让AI评估问题复杂度，选择最佳模型</li>
 *   <li><b>关键词匹配</b>: 快速路径，根据预设关键词进行分类</li>
 *   <li><b>默认回退</b>: 无匹配时使用默认模型</li>
 * </ol>
 * </p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
public class ModelRouter {

    private final AiProperties aiProperties;
    private final ComplexityRouter complexityRouter;
    private final ProviderFailoverService providerFailoverService;

    public ModelRouter(AiProperties aiProperties, ComplexityRouter complexityRouter,
                       ProviderFailoverService providerFailoverService) {
        this.aiProperties = aiProperties;
        this.complexityRouter = complexityRouter;
        this.providerFailoverService = providerFailoverService;
    }

    @lombok.Builder
    @lombok.Data
    public static class RouteResult {
        private String category;
        private String provider;
        private String model;
        private double temperature;
        private int maxTokens;
        private boolean matched;
        private String complexity;
        private boolean needsPlanning;
    }

    /**
     * 根据用户问题路由到合适的模型配置（关键词快速路径）
     */
    public RouteResult route(String question) {
        var routingConfig = aiProperties.getModelRouting();

        if (!routingConfig.isEnabled() || routingConfig.getRoutes().isEmpty()) {
            return buildDefault();
        }

        String lowerQuestion = question != null ? question.toLowerCase() : "";

        for (Map.Entry<String, AiProperties.RouteConfig> entry : routingConfig.getRoutes().entrySet()) {
            AiProperties.RouteConfig route = entry.getValue();
            if (route.getKeywords() != null && !route.getKeywords().isEmpty()) {
                for (String keyword : route.getKeywords()) {
                    if (lowerQuestion.contains(keyword.toLowerCase())) {
                        log.info("[ModelRouter] 关键词匹配: {} -> provider={}, model={}",
                                entry.getKey(), route.getProvider(), route.getModel());
                        return RouteResult.builder()
                                .category(entry.getKey())
                                .provider(route.getProvider())
                                .model(route.getModel())
                                .temperature(route.getTemperature())
                                .maxTokens(route.getMaxTokens())
                                .matched(true)
                                .complexity("medium")
                                .needsPlanning(false)
                                .build();
                    }
                }
            }
        }

        log.debug("[ModelRouter] 未匹配关键词路由，使用默认模型");
        return buildDefault();
    }

    /**
     * 基于LLM复杂度评估路由到合适模型
     *
     * @param question     用户问题
     * @param fileInfo     附件信息
     * @param contextDepth 当前上下文深度
     */
    public RouteResult routeByComplexity(String question, String fileInfo, int contextDepth) {
        var complexityConfig = aiProperties.getComplexityRouting();
        if (!complexityConfig.isEnabled()) {
            return route(question);
        }

        ComplexityRouter.ComplexityResult assessment = complexityRouter.assess(question, fileInfo, contextDepth);

        String category = resolveCategory(assessment.getDomain());
        return RouteResult.builder()
                .category(category)
                .provider(assessment.getSuggestedProvider())
                .model(assessment.getSuggestedModel())
                .temperature(assessment.getSuggestedTemperature())
                .maxTokens(assessment.getSuggestedMaxTokens())
                .matched(true)
                .complexity(assessment.getComplexity())
                .needsPlanning(assessment.isNeedsPlanning())
                .build();
    }

    private String resolveCategory(String domain) {
        return switch (domain) {
            case "data_analysis" -> "data_analysis";
            case "coding" -> "coding";
            case "knowledge_retrieval" -> "knowledge";
            case "creative" -> "creative";
            case "reasoning" -> "reasoning";
            default -> "general";
        };
    }

    private RouteResult buildDefault() {
        com.liang.xz.aiagent.config.DbProviderConfig primary = providerFailoverService.getPrimaryProvider();
        String model = primary != null && primary.getDefaultModel() != null
                ? primary.getDefaultModel() : "gpt-4o";
        double temperature = primary != null && primary.getTemperature() != null
                ? primary.getTemperature() : 0.7;
        int maxTokens = primary != null && primary.getMaxTokens() != null
                ? primary.getMaxTokens() : 4096;
        return RouteResult.builder()
                .category("default")
                .model(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .matched(false)
                .complexity("medium")
                .needsPlanning(false)
                .build();
    }
}
