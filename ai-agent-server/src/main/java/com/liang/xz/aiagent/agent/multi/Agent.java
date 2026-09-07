package com.liang.xz.aiagent.agent.multi;

/**
 * <p>Agent 统一契约 —— 可被编排器调度的最小执行单元</p>
 *
 * <p><b>设计动机：</b>原有代码中"工具"与"Agent"职责混淆：{@code AgentTools} 与
 * {@code DataQueryTool} 是以 {@code @Tool} 暴露给 LLM 的函数，由 LLM 决定何时调用；
 * 而 {@code BusinessAnalysisAgent} 是一个定时自执行的完整智能体。两者无法统一调度。
 * 本接口把它们抽象为同一契约，使编排器可以用一致的方式分发任务。</p>
 *
 * <p><b>与 LangChain4j 的 {@code @Tool} 的关系：</b>二者不冲突，是互补关系。</p>
 * <ul>
 *   <li>{@code @Tool}：LLM 驱动的细粒度函数调用，在单次 LLM 交互内完成；</li>
 *   <li>{@code Agent}：编排器调度的粗粒度任务单元，内部可能包含多轮 LLM 调用。</li>
 * </ul>
 * <p>编排器负责宏观任务分解与依赖调度，LLM 在每个任务内部仍可用 {@code @Tool}。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public interface Agent {

    /**
     * Agent 唯一标识（如 "analysis"、"query"、"search"）。
     */
    String agentId();

    /**
     * 能力描述 —— 供 Supervisor 的 LLM 判断该把子任务分派给谁。
     * 描述是否清晰直接决定路由准确率，请用一句话说明"能处理什么问题"。
     */
    String description();

    /**
     * 快速判断是否适合处理该任务（可选的前置过滤，用于降低 LLM 路由开销）。
     *
     * @param task 任务描述
     * @param ctx  共享上下文
     * @return 可用返回 true；返回 false 只代表"本 Agent 不适合"，不代表任务无法完成
     */
    default boolean canHandle(String task, AgentContext ctx) {
        return true;
    }

    /**
     * 执行任务。
     *
     * <p><b>实现约定：</b></p>
     * <ul>
     *   <li>必须从 {@code ctx} 中读取上游依赖的结果，而非通过参数传递；</li>
     *   <li>必须把产出写入 {@code ctx}（通过 {@code ctx.putTaskResult}），供下游消费；</li>
     *   <li>不得吞掉异常：失败应抛出，由编排器统一降级处理；</li>
     *   <li>应保证幂等：编排器可能对同一任务重试。</li>
     * </ul>
     *
     * @param task 任务描述
     * @param ctx  共享上下文（黑板）
     * @return 执行结果
     */
    String execute(String task, AgentContext ctx);
}
