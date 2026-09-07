package com.liang.xz.aiagent.agent.multi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>Agent 共享上下文（黑板模式 Blackboard）—— 多 Agent 之间唯一的通信媒介</p>
 *
 * <p><b>为什么需要黑板：</b>多 Agent 协作的核心难题是"结果如何传递给下游"。
 * 若让 Agent 之间直接互相调用，会形成强耦合的网状依赖；黑板模式让所有 Agent
 * 只与上下文交互，彼此解耦，且天然支持 DAG 依赖（下游读取上游写入的键）。</p>
 *
 * <p><b>键约定：</b>每个子任务的执行结果以 {@code result:<taskId>} 为键写入黑板，
 * 下游任务通过 {@code dependsOn} 声明依赖后，编排器会自动把上游结果注入其提示词。</p>
 *
 * <p><b>并发安全：</b>同层子任务由编排器并发执行，故内部使用并发容器。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public final class AgentContext {

    /** 一次完整对话的追踪标识，用于串联所有 Agent 的执行日志 */
    private final String traceId;

    /** 对话会话标识，用于把编排日志关联回具体对话 */
    private final String sessionId;

    /** 原始用户问题 */
    private final String question;

    /** 黑板数据：taskId/变量名 → 值 */
    private final Map<String, Object> blackboard = new ConcurrentHashMap<>();

    /** 执行记录：按发生顺序记录每个 Agent 的执行结果，供最终汇总与审计 */
    private final List<AgentTrace> traces = Collections.synchronizedList(new ArrayList<>());

    /** 已执行 Agent 计数，用于生成序号 */
    private final AtomicInteger executedCount = new AtomicInteger(0);

    public AgentContext(String traceId, String question) {
        this(traceId, null, question);
    }

    public AgentContext(String traceId, String sessionId, String question) {
        this.traceId = traceId;
        this.sessionId = sessionId;
        this.question = question;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getQuestion() {
        return question;
    }

    /**
     * 写入黑板。
     */
    public <T> void put(String key, T value) {
        if (key != null && value != null) {
            blackboard.put(key, value);
        }
    }

    /**
     * 从黑板读取并做类型校验，类型不符或不存在返回 empty。
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        Object value = blackboard.get(key);
        if (value == null || !type.isInstance(value)) {
            return Optional.empty();
        }
        return Optional.of((T) value);
    }

    /**
     * 读取黑板内容，不存在时返回默认值。
     */
    public <T> T getOrDefault(String key, Class<T> type, T defaultValue) {
        return get(key, type).orElse(defaultValue);
    }

    /**
     * 记录一次 Agent 执行（供汇总与审计）。
     *
     * @param taskId  子任务标识
     * @param agentId 执行的 Agent
     * @param success 是否成功
     * @param output  输出内容
     */
    public void record(String taskId, String agentId, boolean success, String output) {
        traces.add(new AgentTrace(taskId, agentId, success, output, executedCount.incrementAndGet()));
    }

    /**
     * 读取某个子任务的执行结果（约定键为 {@code result:<taskId>}）。
     */
    public Optional<String> getTaskResult(String taskId) {
        return get(RESULT_KEY_PREFIX + taskId, String.class);
    }

    /**
     * 写入子任务结果（约定键为 {@code result:<taskId>}）。
     */
    public void putTaskResult(String taskId, String result) {
        put(RESULT_KEY_PREFIX + taskId, result);
    }

    /**
     * 返回本次对话中所有 Agent 的执行记录（不可修改视图）。
     */
    public List<AgentTrace> getTraces() {
        return Collections.unmodifiableList(traces);
    }

    /**
     * 将所有执行结果拼装为文本，供汇总 Agent 生成最终回答。
     */
    public String summarizeTraces() {
        if (traces.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (AgentTrace trace : traces) {
            sb.append(String.format("[%s] %s: %s%n",
                    trace.success ? "成功" : "失败", trace.agentId, trace.output));
        }
        return sb.toString();
    }

    /** 子任务结果键前缀 */
    public static final String RESULT_KEY_PREFIX = "result:";

    /**
     * 单次 Agent 执行记录。
     */
    public record AgentTrace(String taskId, String agentId, boolean success, String output, int order) {
    }
}
