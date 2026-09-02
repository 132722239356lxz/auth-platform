package com.liang.xz.aiagent.agent.multi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>Agent 注册表 —— 集中管理所有可被编排的 Agent</p>
 *
 * <p>职责:</p>
 * <ul>
 *   <li><b>注册发现：</b>所有实现 {@link Agent} 的 Bean 由 Spring 自动收集，无需手工登记；</li>
 *   <li><b>按 ID 查找：</b>编排器根据 LLM 规划的 {@code agentId} 精确定位执行者；</li>
 *   <li><b>能力清单：</b>把各 Agent 的描述拼成提示词，供 Supervisor 的 LLM 做路由决策；</li>
 *   <li><b>降级选人：</b>LLM 给出未知 agentId 时，按 {@code canHandle} 回退挑选可用 Agent。</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
public class AgentRegistry {

    private final Map<String, Agent> agents = new LinkedHashMap<>();

    public AgentRegistry(List<Agent> agentList) {
        for (Agent agent : agentList) {
            if (agent == null) {
                continue;
            }
            Agent previous = agents.putIfAbsent(agent.agentId(), agent);
            if (previous != null) {
                log.warn("[AgentRegistry] Agent ID 冲突，忽略重复注册: {} ({})",
                        agent.agentId(), agent.getClass().getSimpleName());
            }
        }
        if (agents.isEmpty()) {
            log.warn("[AgentRegistry] 未注册任何 Agent，多Agent编排将不可用");
        } else {
            log.info("[AgentRegistry] 已注册 {} 个 Agent: {}", agents.size(), agents.keySet());
        }
    }

    /**
     * 按 ID 精确查找。
     */
    public Optional<Agent> findById(String agentId) {
        if (agentId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(agents.get(agentId.trim()));
    }

    /**
     * 选择能处理该任务的 Agent：优先按 ID，其次按 canHandle 匹配。
     *
     * @param agentId LLM 建议的 Agent ID（可能为 null 或无效值）
     * @param task    任务描述
     * @param ctx     共享上下文
     * @return 可用的 Agent；无可用时返回 empty（由调用方降级）
     */
    public Optional<Agent> select(String agentId, String task, AgentContext ctx) {
        Optional<Agent> byId = findById(agentId);
        if (byId.isPresent()) {
            return byId;
        }
        if (agentId != null && !agentId.isBlank()) {
            log.debug("[AgentRegistry] 未找到 agentId={}，回退按能力匹配", agentId);
        }
        return agents.values().stream()
                .filter(agent -> agent.canHandle(task, ctx))
                .findFirst();
    }

    /**
     * 生成能力清单提示词，供 Supervisor 的 LLM 做任务分派决策。
     */
    public String describeCapabilities() {
        if (agents.isEmpty()) {
            return "（当前无可用 Agent）";
        }
        return agents.values().stream()
                .map(agent -> String.format("- %s: %s", agent.agentId(), agent.description()))
                .collect(Collectors.joining("\n"));
    }

    /**
     * 返回全部已注册的 Agent ID。
     */
    public Set<String> agentIds() {
        return Collections.unmodifiableSet(agents.keySet());
    }

    /**
     * 返回全部 Agent 能力条目，供管理接口展示。
     */
    public List<Capability> capabilities() {
        List<Capability> list = new ArrayList<>(agents.size());
        agents.forEach((id, agent) -> list.add(new Capability(id, agent.description(),
                agent.getClass().getSimpleName())));
        return Collections.unmodifiableList(list);
    }

    /**
     * Agent 能力描述条目。
     */
    public record Capability(String agentId, String description, String implementation) {
    }
}
