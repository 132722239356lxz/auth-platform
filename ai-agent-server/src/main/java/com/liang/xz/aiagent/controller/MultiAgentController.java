package com.liang.xz.aiagent.controller;

import com.liang.xz.aiagent.agent.multi.AgentRegistry;
import com.liang.xz.aiagent.config.AiProperties;
import com.liang.xz.aiagent.repository.AgentTaskLogRepository;
import com.liang.xz.common.core.model.R;
import com.liang.xz.resource.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>多 Agent 编排管理 API</p>
 *
 * <p>提供编排能力的运行期可观测性：查询已注册的 Agent、各自能力描述及编排开关状态。
 * 排障时首要确认"问题是否被判定为复杂问题"以及"有哪些 Agent 可供调度"。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Tag(name = "多Agent编排", description = "查看已注册的 Agent、能力清单与编排开关状态")
@RestController
@RequestMapping("/api/ai/agent")
@RequiredArgsConstructor
public class MultiAgentController {

    private final AgentRegistry agentRegistry;
    private final AiProperties aiProperties;
    private final AgentTaskLogRepository agentTaskLogRepository;

    @Operation(summary = "获取已注册的 Agent 列表",
            description = "返回每个 Agent 的 ID、能力描述与实现类，用于确认编排可调度的执行单元")
    @RequirePermission("ai:agent:list")
    @GetMapping("/list")
    public R<List<AgentRegistry.Capability>> listAgents() {
        return R.ok(agentRegistry.capabilities());
    }

    @Operation(summary = "获取 Agent 能力清单",
            description = "返回供 LLM 做任务分派使用的能力描述文本，可直接核对路由提示词内容")
    @RequirePermission("ai:agent:list")
    @GetMapping("/capabilities")
    public R<String> capabilities() {
        return R.ok(agentRegistry.describeCapabilities());
    }

    @Operation(summary = "查询某次编排的子任务调用明细",
            description = "按 traceId 查看该次多Agent协作中每个子Agent的输入、输出、耗时与成败，用于链路排障")
    @RequirePermission("ai:agent:list")
    @GetMapping("/logs/task")
    public R<List<Map<String, Object>>> taskLogs(@RequestParam String traceId) {
        return R.ok(agentTaskLogRepository.findByTraceId(traceId));
    }

    @Operation(summary = "查询编排汇总记录",
            description = "查看最近的多Agent编排概况（任务数、层级数、成功失败数、是否回退），用于评估编排质量")
    @RequirePermission("ai:agent:list")
    @GetMapping("/logs/workflow")
    public R<List<Map<String, Object>>> workflowLogs(
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "20") int limit) {
        return R.ok(agentTaskLogRepository.findWorkflows(sessionId, limit));
    }

    @Operation(summary = "获取编排开关状态",
            description = "查看多 Agent 编排是否启用。关闭后所有问题走单 Agent 工具链路")
    @RequirePermission("ai:agent:list")
    @GetMapping("/status")
    public R<Map<String, Object>> status() {
        AiProperties.OrchestrationConfig config = aiProperties.getOrchestration();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("orchestrationEnabled", config == null || config.isEnabled());
        status.put("agentCount", agentRegistry.agentIds().size());
        status.put("agentIds", agentRegistry.agentIds());
        return R.ok(status);
    }
}
