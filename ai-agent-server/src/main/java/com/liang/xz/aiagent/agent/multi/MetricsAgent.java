package com.liang.xz.aiagent.agent.multi;

import com.liang.xz.aiagent.agent.AgentTools;
import com.liang.xz.aiagent.agent.BusinessAnalysisAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * <p>指标分析 Agent —— 负责业务指标的采集与解读</p>
 *
 * <p>委托 {@link AgentTools#getMetrics()} 采集关键指标（待审批数、驳回率、消息失败率等），
 * 并在任务需要解读时调用 {@link BusinessAnalysisAgent#instantAnalysis(String)} 生成分析结论。</p>
 *
 * <p>本类是适配层：不重复实现业务逻辑，只把既有能力包装成 {@link Agent} 契约，
 * 使其可被编排器统一调度。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsAgent implements Agent {

    private final AgentTools agentTools;
    private final BusinessAnalysisAgent businessAnalysisAgent;

    @Override
    public String agentId() {
        return "metrics";
    }

    @Override
    public String description() {
        return "查询并解读关键业务指标，包括工作流待审批数、驳回率、消息发送失败率、预警积压数等；"
                + "适合「当前有多少待审批」「效率怎么样」「运行状况如何」这类需要量化数据的问题";
    }

    @Override
    public boolean canHandle(String task, AgentContext ctx) {
        if (task == null) {
            return false;
        }
        return task.contains("指标") || task.contains("统计") || task.contains("数量")
                || task.contains("数据") || task.contains("趋势") || task.contains("率")
                || task.contains("效率") || task.contains("状况");
    }

    @Override
    public String execute(String task, AgentContext ctx) {
        String metrics = agentTools.getMetrics();
        // 任务含解读诉求时，额外调用分析能力给出结论，而非只抛原始数据
        if (task != null && (task.contains("分析") || task.contains("解读")
                || task.contains("原因") || task.contains("建议"))) {
            try {
                var result = businessAnalysisAgent.instantAnalysis(task);
                if (result != null && result.llmInsight() != null && !result.llmInsight().isBlank()) {
                    return metrics + "\n\n分析结论：" + result.llmInsight();
                }
            } catch (Exception e) {
                log.warn("[MetricsAgent] 生成分析结论失败，仅返回原始指标: {}", e.getMessage());
            }
        }
        return metrics;
    }
}
