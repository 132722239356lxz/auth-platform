package com.liang.xz.aiagent.agent.workflow;

import com.liang.xz.aiagent.agent.TaskPlanner;
import com.liang.xz.aiagent.agent.multi.AgentContext;
import com.liang.xz.aiagent.agent.multi.AgentRegistry;
import com.liang.xz.aiagent.llm.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>多 Agent 编排器 —— 用 LLM 规划任务图，调度多个 Agent 协作，并汇总产出</p>
 *
 * <p>本类承担"协调者(Supervisor)"角色，是 {@link TaskPlanner} 的上层：
 * TaskPlanner 负责"拆任务 + 按 DAG 调度执行"，本类负责"决定要不要多 Agent、
 * 以及把各 Agent 的产出汇总成最终答案"。</p>
 *
 * <p><b>执行流程:</b></p>
 * <ol>
 *   <li><b>门控：</b>用 {@link TaskPlanner#needPlan(String)} 判断是否值得走多 Agent；
 *       简单问题直接返回，交给单链路处理（避免为一句问候付出多次 LLM 调用的代价）。</li>
 *   <li><b>规划与执行：</b>委托 {@link TaskPlanner#executePlan} 完成 DAG 分解与分层并发。</li>
 *   <li><b>汇总：</b>把各子任务产出交给 LLM 综合成自然语言答案。</li>
 * </ol>
 *
 * <p><b>相比旧版的修正：</b>旧版 {@code plan()} 调用 LLM 生成计划后<b>直接丢弃了结果</b>
 * （planJson 变量从未被使用），后续 {@code executeSubtasks()} 硬编码了 3 个固定子任务，
 * 属于伪实现。现在整个规划-执行链路已由 TaskPlanner 的 DAG 调度承担，本类只做门控与汇总。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
public class WorkflowOrchestrator {

    private final LlmClient llmClient;
    private final TaskPlanner taskPlanner;
    private final AgentRegistry agentRegistry;
    private final AtomicInteger workflowCount = new AtomicInteger(0);

    public WorkflowOrchestrator(LlmClient llmClient,
                                TaskPlanner taskPlanner,
                                AgentRegistry agentRegistry) {
        this.llmClient = llmClient;
        this.taskPlanner = taskPlanner;
        this.agentRegistry = agentRegistry;
    }

    /**
     * 判断是否应该走多 Agent 编排。
     *
     * <p>前置条件：存在已注册 Agent，且问题满足复杂度门控。
     * 任一不满足都应走单链路，保证简单场景的响应速度。</p>
     */
    public boolean shouldOrchestrate(String question) {
        if (agentRegistry.agentIds().isEmpty()) {
            return false;
        }
        return taskPlanner.needPlan(question);
    }

    /**
     * 执行多 Agent 编排，返回最终答案。
     *
     * <p><b>降级策略：</b>任何环节失败都返回 {@code null}，由调用方回退到单链路，
     * 确保编排能力是"增强"而非"依赖"——编排不可用时系统依然可用。</p>
     *
     * @param question  用户问题
     * @param sessionId 会话ID，写入日志以便关联到具体对话
     * @return 编排结果（含答案与执行元数据）；不适合编排或执行失败返回 null
     */
    public OrchestrationResult orchestrate(String question, String sessionId) {
        if (question == null || question.isBlank()) {
            return null;
        }
        if (!shouldOrchestrate(question)) {
            return null;
        }

        String traceId = "wf-" + System.currentTimeMillis() + "-" + workflowCount.incrementAndGet();
        AgentContext ctx = new AgentContext(traceId, sessionId, question);
        long start = System.currentTimeMillis();
        log.info("[WorkflowOrchestrator] 启动多Agent编排: traceId={}, question={}",
                traceId, truncate(question, 60));

        try {
            TaskPlanner.PlanResult planResult = taskPlanner.executePlan(question, ctx);
            if (!planResult.isNeedPlan() || planResult.getSummary().isBlank()) {
                log.info("[WorkflowOrchestrator] 未执行多Agent计划，回退单链路: traceId={}", traceId);
                return null;
            }
            String answer = summarize(question, planResult, ctx);
            long elapsed = System.currentTimeMillis() - start;
            log.info("[WorkflowOrchestrator] 编排完成: traceId={}, 子任务数={}, 耗时={}ms",
                    traceId, planResult.getSubTasks().size(), elapsed);
            return new OrchestrationResult(answer, traceId, planResult.getAgentIds(),
                    planResult.getSubTasks().size(), planResult.getLayerCount(),
                    planResult.getSuccessCount(), planResult.getFailCount(), elapsed);
        } catch (Exception e) {
            log.warn("[WorkflowOrchestrator] 编排失败，回退单链路: traceId={}, reason={}",
                    traceId, e.getMessage());
            return null;
        }
    }

    /**
     * 编排执行结果 —— 除最终答案外，还携带供前端展示与链路追踪的元数据。
     *
     * @param answer       汇总后的答案
     * @param traceId      编排链路追踪ID，可用于查询子任务明细日志
     * @param agentIds     参与本次协作的 Agent 标识
     * @param taskCount    子任务总数
     * @param layerCount   执行层级数（反映任务图深度）
     * @param successCount 成功子任务数
     * @param failCount    失败子任务数
     * @param elapsedMs    整体耗时毫秒
     */
    public record OrchestrationResult(String answer, String traceId, List<String> agentIds,
                                      int taskCount, int layerCount, int successCount,
                                      int failCount, long elapsedMs) {
    }

    /**
     * 汇总各 Agent 的产出，生成面向用户的最终答案。
     *
     * <p>汇总环节本身也交给 LLM 处理：各子任务的产出是碎片化的原始数据，
     * 直接拼给用户可读性差，需要语言模型做整合与提炼。</p>
     */
    private String summarize(String question, TaskPlanner.PlanResult planResult, AgentContext ctx) {
        String systemPrompt = """
                你是一个结果汇总专家。多个专业 Agent 已分别完成了各自的子任务，
                请你把它们的产出整合成一份连贯、准确、可直接回答用户问题的答案。

                要求：
                1. 直接回应用户问题，不要复述"任务已完成"之类的过程描述
                2. 保留关键数据，但不要罗列所有原始细节
                3. 若某个子任务失败或数据缺失，如实说明，不要编造
                4. 用自然语言组织，必要时分点说明
                """;

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("用户问题：").append(question).append("\n\n");
        if (planResult.getSubTasks() != null && !planResult.getSubTasks().isEmpty()) {
            userPrompt.append("任务分解：\n");
            for (TaskPlanner.SubTask task : planResult.getSubTasks()) {
                userPrompt.append("- ").append(task.getTaskName())
                        .append("（执行者: ").append(task.getAgentId()).append("）\n");
            }
            userPrompt.append("\n");
        }
        userPrompt.append("各 Agent 产出：\n").append(ctx.summarizeTraces());

        try {
            String answer = llmClient.chat(systemPrompt, userPrompt.toString());
            if (answer != null && !answer.isBlank()) {
                return answer;
            }
        } catch (Exception e) {
            log.warn("[WorkflowOrchestrator] LLM 汇总失败，直接返回原始产出: {}", e.getMessage());
        }
        // 汇总失败时降级为直接返回各 Agent 产出，保证有内容可答
        return ctx.summarizeTraces();
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
