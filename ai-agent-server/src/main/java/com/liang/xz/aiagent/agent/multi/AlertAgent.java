package com.liang.xz.aiagent.agent.multi;

import com.liang.xz.aiagent.agent.AgentTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * <p>预警 Agent —— 负责风险预警的查询与触发</p>
 *
 * <p>委托 {@link AgentTools} 的预警相关能力：查询现有预警列表、预警汇总统计，
 * 以及在需要时触发一次实时分析以刷新预警。</p>
 *
 * <p><b>Agent 间协作的典型场景：</b>本 Agent 常作为上游被 MetricsAgent 依赖——
 * 编排器会先并行采集指标与预警，再交给汇总环节做综合判断。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertAgent implements Agent {

    private final AgentTools agentTools;

    @Override
    public String agentId() {
        return "alert";
    }

    @Override
    public String description() {
        return "查询系统分析预警（预警名称、级别、状态、汇总统计），或在需要时触发一次实时业务分析；"
                + "适合「有哪些异常」「有没有风险」「当前告警情况」这类问题";
    }

    @Override
    public boolean canHandle(String task, AgentContext ctx) {
        if (task == null) {
            return false;
        }
        return task.contains("预警") || task.contains("告警") || task.contains("异常")
                || task.contains("风险") || task.contains("问题") || task.contains("错误");
    }

    @Override
    public String execute(String task, AgentContext ctx) {
        String summary = agentTools.getAlertSummary();
        String alerts = agentTools.getAlerts();

        // 仅当任务明确要求"重新检测/刷新"时才触发实时分析，
        // 因为该操作开销较大（会跑规则引擎并调用 LLM），不应在每次查询时执行
        if (task != null && (task.contains("触发") || task.contains("重新检测")
                || task.contains("刷新") || task.contains("立即检测"))) {
            try {
                String triggered = agentTools.triggerAnalysis();
                return summary + "\n\n实时检测结果：" + triggered;
            } catch (Exception e) {
                log.warn("[AlertAgent] 触发实时分析失败，返回已有预警: {}", e.getMessage());
            }
        }
        return summary + "\n\n预警明细：" + alerts;
    }
}
