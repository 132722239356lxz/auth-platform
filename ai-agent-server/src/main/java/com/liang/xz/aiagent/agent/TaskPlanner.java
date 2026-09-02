package com.liang.xz.aiagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.aiagent.agent.multi.Agent;
import com.liang.xz.aiagent.agent.multi.AgentContext;
import com.liang.xz.aiagent.agent.multi.AgentRegistry;
import com.liang.xz.aiagent.config.AiProperties;
import com.liang.xz.aiagent.llm.LlmClient;
import com.liang.xz.aiagent.repository.AgentTaskLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>任务规划器 —— 把复杂问题拆解为带依赖关系的子任务，并调度多 Agent 协作完成</p>
 *
 * <p><b>与旧版的关键差异：</b></p>
 * <ol>
 *   <li><b>依赖支持：</b>旧版子任务之间完全独立、只能并行，无法表达
 *       "先查数据 → 再基于数据做分析"这类串行依赖。新版引入 {@code dependsOn}
 *       构成 DAG，通过拓扑排序实现"同层并行、层间串行"。</li>
 *   <li><b>Agent 调度：</b>旧版按 {@code toolHint} 硬编码分支到具体工具；
 *       新版按 {@code agentId} 从 {@link AgentRegistry} 查找执行者，
 *       新增 Agent 无需改动本类（符合开闭原则）。</li>
 *   <li><b>结果传递：</b>旧版靠返回值汇总；新版通过 {@link AgentContext} 黑板传递，
 *       下游任务自动获得上游结果作为输入。</li>
 * </ol>
 *
 * <p><b>执行模型（拓扑分层并发）：</b></p>
 * <pre>
 *   t1(查指标)     t2(查预警)      ← 第 1 层：无依赖，并行执行
 *        \            /
 *         t3(对比分析)             ← 第 2 层：依赖 t1/t2，拿到上游结果后执行
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
public class TaskPlanner {

    private static final int MAX_SUBTASKS = 5;
    private static final int MIN_SUBTASKS = 2;
    private static final int PLANNER_TIMEOUT_SECONDS = 60;
    private static final int MAX_DEPTH = 10;

    /** 触发多步骤规划的信号词：出现这些词说明问题需要拆解 */
    private static final String[] PLAN_KEYWORDS = {
            "并且", "同时", "分别", "对比", "汇总", "分析", "统计",
            "然后", "接着", "先", "再", "以及", "综合"
    };

    /** 提取被 ```json ... ``` 包裹的内容 */
    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private final LlmClient llmClient;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final AgentRegistry agentRegistry;
    private final AgentTaskLogRepository agentTaskLogRepository;
    private final ExecutorService executor;

    public TaskPlanner(LlmClient llmClient, AiProperties aiProperties,
                       ObjectMapper objectMapper, AgentRegistry agentRegistry,
                       AgentTaskLogRepository agentTaskLogRepository) {
        this.llmClient = llmClient;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.agentRegistry = agentRegistry;
        this.agentTaskLogRepository = agentTaskLogRepository;
        this.executor = new ThreadPoolExecutor(
                2,
                Math.max(4, Runtime.getRuntime().availableProcessors()),
                120L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(128),
                r -> {
                    Thread t = new Thread(r, "agent-task-" + System.nanoTime());
                    t.setDaemon(true);
                    return t;
                });
    }

    // ---------------- 是否需要进行多步规划 ----------------

    /**
     * 判断问题是否复杂到需要多 Agent 协作。
     *
     * <p>采用规则启发式而非 LLM 判断：此处若再调一次 LLM，会给每次对话增加一次
     * 完整往返延迟，对简单问题得不偿失。规则判断零延迟且可预测。</p>
     */
    public boolean needPlan(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        int qMarkCount = 0;
        for (int i = 0; i < question.length(); i++) {
            char c = question.charAt(i);
            if (c == '?' || c == '？') {
                qMarkCount++;
            }
        }
        if (qMarkCount >= 2) {
            return true;
        }
        for (String keyword : PLAN_KEYWORDS) {
            if (question.contains(keyword)) {
                return true;
            }
        }
        return question.length() > 60;
    }

    // ---------------- 执行计划 ----------------

    /**
     * 规划并执行一个多 Agent 协作计划。
     *
     * @param question 用户问题
     * @param ctx      共享上下文（黑板），执行结果会写入其中
     * @return 规划执行结果；若无需规划或规划失败，{@code needPlan} 为 false，由调用方回退单链路
     */
    public PlanResult executePlan(String question, AgentContext ctx) {
        if (!needPlan(question)) {
            return PlanResult.notNeeded();
        }
        if (agentRegistry.agentIds().isEmpty()) {
            log.debug("[TaskPlanner] 无可用 Agent，跳过多步规划");
            return PlanResult.notNeeded();
        }

        List<SubTask> subTasks = planTasks(question);
        if (subTasks.isEmpty()) {
            return PlanResult.notNeeded();
        }

        try {
            List<List<SubTask>> layers = topoSort(subTasks);
            log.info("[TaskPlanner] 规划完成: {} 个子任务, {} 个执行层, traceId={}",
                    subTasks.size(), layers.size(), ctx.getTraceId());
            long start = System.currentTimeMillis();
            for (int i = 0; i < layers.size(); i++) {
                executeLayer(layers.get(i), ctx, i);
            }
            long elapsed = System.currentTimeMillis() - start;
            recordWorkflowLog(ctx, subTasks, layers.size(), elapsed, false, null);
            List<String> usedAgents = new ArrayList<>(ctx.getTraces().stream()
                    .map(AgentContext.AgentTrace::agentId).distinct().toList());
            int successCount = (int) ctx.getTraces().stream()
                    .filter(AgentContext.AgentTrace::success).count();
            return PlanResult.success(subTasks, ctx.summarizeTraces(), layers.size(),
                    successCount, ctx.getTraces().size() - successCount, usedAgents);
        } catch (IllegalStateException e) {
            // 规划产物不合法（如存在环），回退单链路，不把错误暴露给用户
            log.warn("[TaskPlanner] 任务图不合法，回退单任务: {}", e.getMessage());
            recordWorkflowLog(ctx, subTasks, 0, 0, true, "任务图不合法: " + e.getMessage());
            return PlanResult.notNeeded();
        } catch (Exception e) {
            log.warn("[TaskPlanner] 多Agent执行失败，回退单任务: {}", e.getMessage());
            recordWorkflowLog(ctx, subTasks, 0, 0, true, "执行失败: " + e.getMessage());
            return PlanResult.notNeeded();
        }
    }

    // ---------------- LLM 规划 ----------------

    /**
     * 调用 LLM 生成子任务列表（含 agentId 与 dependsOn）。
     */
    private List<SubTask> planTasks(String question) {
        String systemPrompt = """
                你是一个任务规划专家。请将用户的复杂问题拆分为多个可独立或协作完成的子任务。

                可用 Agent 及其能力：
                %s

                输出要求（严格遵守）：
                1. 只输出 JSON 数组，不要任何解释文字，不要 markdown 代码块标记
                2. 子任务数量控制在 %d 到 %d 个之间
                3. 每个子任务包含字段：
                   - id: 任务唯一标识（字符串，如 "t1"、"t2"）
                   - taskName: 任务名称（简短）
                   - description: 任务描述（给执行 Agent 的具体指令）
                   - agentId: 从上述可用 Agent 中选择最合适的一个
                   - dependsOn: 依赖的任务 id 数组，无依赖则为空数组 []
                4. 若某子任务需要其它子任务的结果作为输入，必须在 dependsOn 中声明
                5. 不要创建循环依赖

                输出示例：
                [{"id":"t1","taskName":"查询指标","description":"查询当前待审批数量","agentId":"analysis","dependsOn":[]}]
                """.formatted(agentRegistry.describeCapabilities(), MIN_SUBTASKS, MAX_SUBTASKS);

        try {
            String response = llmClient.chat(systemPrompt, "用户问题：" + question);
            return parseTasks(response);
        } catch (Exception e) {
            log.warn("[TaskPlanner] 子任务规划失败，回退到单任务: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析 LLM 返回的子任务 JSON。
     *
     * <p>做了充分的容错：模型常在 JSON 外层包裹 markdown 代码块，或夹杂前后缀说明，
     * 因此先尝试提取代码块，再退化为截取首个 '[' 到末个 ']' 之间的内容。</p>
     */
    private List<SubTask> parseTasks(String response) {
        if (response == null || response.isBlank()) {
            return Collections.emptyList();
        }
        String json = response.trim();
        Matcher matcher = JSON_BLOCK.matcher(json);
        if (matcher.find()) {
            json = matcher.group(1).trim();
        } else {
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
        }

        try {
            JsonNode array = objectMapper.readTree(json);
            if (array == null || !array.isArray()) {
                return Collections.emptyList();
            }
            List<SubTask> tasks = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();
            for (JsonNode node : array) {
                String id = text(node, "id");
                if (id == null || !seenIds.add(id)) {
                    continue;
                }
                SubTask task = new SubTask();
                task.setId(id);
                task.setTaskName(defaultIfBlank(text(node, "taskName"), id));
                task.setDescription(defaultIfBlank(text(node, "description"), task.getTaskName()));
                task.setAgentId(defaultIfBlank(text(node, "agentId"), null));
                task.setDependsOn(parseDepends(node.get("dependsOn")));
                tasks.add(task);
                if (tasks.size() >= MAX_SUBTASKS) {
                    break;
                }
            }
            return tasks.size() >= MIN_SUBTASKS ? tasks : Collections.emptyList();
        } catch (Exception e) {
            log.warn("[TaskPlanner] 解析子任务 JSON 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> parseDepends(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> deps = new ArrayList<>();
        for (JsonNode item : node) {
            if (item != null && item.isTextual()) {
                String value = item.asText();
                if (value != null && !value.isBlank()) {
                    deps.add(value.trim());
                }
            }
        }
        return deps;
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        return child.asText();
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    // ---------------- 拓扑排序与分层 ----------------

    /**
     * 将子任务按依赖关系分层（Kahn 算法）。
     *
     * <p>同层任务之间无依赖可并行；层与层之间必须串行，保证下游能读到上游结果。
     * 若存在环（LLM 规划错误），抛出 {@link IllegalStateException} 由上层回退。</p>
     */
    private List<List<SubTask>> topoSort(List<SubTask> tasks) {
        Map<String, SubTask> taskMap = new LinkedHashMap<>();
        for (SubTask task : tasks) {
            taskMap.put(task.getId(), task);
        }
        // 清理指向不存在任务的依赖，避免 LLM 幻觉 id 导致整体失败
        for (SubTask task : tasks) {
            List<String> valid = new ArrayList<>();
            for (String dep : task.getDependsOn()) {
                if (taskMap.containsKey(dep) && !dep.equals(task.getId())) {
                    valid.add(dep);
                } else {
                    log.debug("[TaskPlanner] 忽略无效依赖: {} -> {}", task.getId(), dep);
                }
            }
            task.setDependsOn(valid);
        }

        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        for (SubTask task : tasks) {
            inDegree.put(task.getId(), task.getDependsOn().size());
            for (String dep : task.getDependsOn()) {
                dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(task.getId());
            }
        }

        List<List<SubTask>> layers = new ArrayList<>();
        Set<String> remaining = new HashSet<>(taskMap.keySet());
        Deque<String> queue = new ArrayDeque<>();
        inDegree.forEach((id, degree) -> {
            if (degree == 0) {
                queue.add(id);
            }
        });

        int depth = 0;
        while (!remaining.isEmpty()) {
            if (queue.isEmpty()) {
                throw new IllegalStateException("子任务存在循环依赖，无法排序");
            }
            if (++depth > MAX_DEPTH) {
                throw new IllegalStateException("子任务依赖层级过深，可能存在环");
            }
            List<SubTask> currentLayer = new ArrayList<>();
            List<String> nextSeeds = new ArrayList<>();
            while (!queue.isEmpty()) {
                String id = queue.poll();
                if (!remaining.remove(id)) {
                    continue;
                }
                currentLayer.add(taskMap.get(id));
                for (String dependent : dependents.getOrDefault(id, List.of())) {
                    Integer degree = inDegree.get(dependent);
                    if (degree == null) {
                        continue;
                    }
                    if (degree - 1 == 0) {
                        nextSeeds.add(dependent);
                    }
                    inDegree.put(dependent, degree - 1);
                }
            }
            if (!currentLayer.isEmpty()) {
                layers.add(currentLayer);
            }
            queue.addAll(nextSeeds);
        }
        return layers;
    }

    // ---------------- 执行 ----------------

    /**
     * 并发执行同一层内的所有子任务。
     */
    private void executeLayer(List<SubTask> layer, AgentContext ctx, int layerIndex) {
        if (layer.size() == 1) {
            executeSubTask(layer.get(0), ctx, layerIndex);
            return;
        }
        // 并发度由专用线程池的线程数上限控制；子任务数已限制在 MAX_SUBTASKS 以内，
        // 无需再用信号量重复限流（信号量在此只会增加无谓的复杂度）
        List<CompletableFuture<Void>> futures = new ArrayList<>(layer.size());
        for (SubTask task : layer) {
            futures.add(CompletableFuture.runAsync(
                    () -> executeSubTask(task, ctx, layerIndex), executor));
        }
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(PLANNER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[TaskPlanner] 执行层超时或异常，未完成的任务将标记失败: {}", e.getMessage());
        }
    }

    /**
     * 执行单个子任务：选 Agent → 拼装带上游结果的提示词 → 执行 → 写入黑板 → 落调用日志。
     *
     * <p>无论成功或失败都会记录一条调用日志，便于事后还原完整的编排链路。</p>
     *
     * @param task       子任务
     * @param ctx        共享上下文
     * @param layerIndex 所在执行层级（0 为起始层）
     */
    private void executeSubTask(SubTask task, AgentContext ctx, int layerIndex) {
        String taskId = task.getId();
        String agentId = task.getAgentId();
        String prompt = null;
        long start = System.currentTimeMillis();
        try {
            Agent agent = agentRegistry.select(task.getAgentId(), task.getDescription(), ctx)
                    .orElseThrow(() -> new IllegalStateException(
                            "未找到可处理该任务的 Agent: " + task.getAgentId()));
            agentId = agent.agentId();
            log.debug("[TaskPlanner] 子任务开始: traceId={} task={} agent={} layer={} dependsOn={}",
                    ctx.getTraceId(), taskId, agentId, layerIndex, task.getDependsOn());

            prompt = buildTaskPrompt(task, ctx);
            String output = agent.execute(prompt, ctx);

            if (output == null || output.isBlank()) {
                output = "（Agent 未返回有效结果）";
            }
            ctx.putTaskResult(taskId, output);
            ctx.record(taskId, agentId, true, output);
            log.info("[TaskPlanner] 子任务完成: traceId={} task={} agent={} layer={} elapsed={}ms",
                    ctx.getTraceId(), taskId, agentId, layerIndex, System.currentTimeMillis() - start);
            recordTaskLog(ctx, task, agentId, layerIndex, prompt, output,
                    System.currentTimeMillis() - start, true, null);
        } catch (Exception e) {
            // 单个任务失败不阻断整体流程：记录失败后继续，由汇总阶段向用户说明
            String errorMsg = "任务执行失败: " + e.getMessage();
            ctx.putTaskResult(taskId, errorMsg);
            ctx.record(taskId, agentId == null ? "unknown" : agentId, false, errorMsg);
            log.warn("[TaskPlanner] 子任务 {} 执行失败: {}", task.getTaskName(), e.getMessage());
            recordTaskLog(ctx, task, agentId, layerIndex, prompt, null,
                    System.currentTimeMillis() - start, false, e.getMessage());
        }
    }

    /**
     * 记录一次子任务（Agent）调用日志。
     */
    private void recordTaskLog(AgentContext ctx, SubTask task, String agentId, int layerIndex,
                               String input, String output, long elapsed,
                               boolean success, String errorMsg) {
        if (agentTaskLogRepository == null) {
            return;
        }
        try {
            AgentTaskLogRepository.AgentTaskLogParams params =
                    new AgentTaskLogRepository.AgentTaskLogParams();
            params.traceId = ctx.getTraceId();
            params.sessionId = ctx.getSessionId();
            params.taskId = task.getId();
            params.agentId = agentId;
            params.taskName = task.getTaskName();
            params.layerIndex = layerIndex;
            params.dependsOn = task.getDependsOn() == null || task.getDependsOn().isEmpty()
                    ? null : String.join(",", task.getDependsOn());
            params.taskInput = input;
            params.taskOutput = output;
            params.elapsedMs = elapsed;
            params.success = success;
            params.errorMsg = errorMsg;
            agentTaskLogRepository.logTask(params);
        } catch (Exception e) {
            // 日志写入失败绝不能影响编排主流程
            log.warn("[TaskPlanner] 记录子任务日志异常(不影响主流程): {}", e.getMessage());
        }
    }

    /**
     * 记录一次编排的汇总日志。
     */
    private void recordWorkflowLog(AgentContext ctx, List<SubTask> tasks, int layerCount,
                                   long elapsed, boolean fallback, String remark) {
        if (agentTaskLogRepository == null) {
            return;
        }
        try {
            int successCount = 0;
            int failCount = 0;
            for (AgentContext.AgentTrace trace : ctx.getTraces()) {
                if (trace.success()) {
                    successCount++;
                } else {
                    failCount++;
                }
            }
            AgentTaskLogRepository.AgentWorkflowLogParams params =
                    new AgentTaskLogRepository.AgentWorkflowLogParams();
            params.traceId = ctx.getTraceId();
            params.sessionId = ctx.getSessionId();
            params.question = ctx.getQuestion();
            params.agentIds = String.join(",", agentRegistry.agentIds());
            params.taskCount = tasks == null ? 0 : tasks.size();
            params.layerCount = layerCount;
            params.successCount = successCount;
            params.failCount = failCount;
            params.elapsedMs = elapsed;
            params.fallback = fallback;
            params.remark = remark;
            agentTaskLogRepository.logWorkflow(params);
        } catch (Exception e) {
            log.warn("[TaskPlanner] 记录编排汇总日志异常(不影响主流程): {}", e.getMessage());
        }
    }

    /**
     * 拼装子任务提示词：把所依赖的上游结果注入，实现 Agent 间的数据传递。
     */
    private String buildTaskPrompt(SubTask task, AgentContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("【总体问题】").append(ctx.getQuestion()).append("\n\n");
        sb.append("【本次任务】").append(task.getDescription()).append("\n");

        List<String> deps = task.getDependsOn();
        if (deps != null && !deps.isEmpty()) {
            sb.append("\n【前置任务的产出（请结合这些结果完成本次任务）】\n");
            for (String dep : deps) {
                String upstream = ctx.getTaskResult(dep).orElse("");
                if (!upstream.isBlank()) {
                    sb.append("- ").append(dep).append(": ").append(upstream).append("\n");
                }
            }
        }
        return sb.toString();
    }

    // ---------------- 模型 ----------------

    /**
     * 子任务定义（支持依赖关系）。
     */
    @lombok.Data
    public static class SubTask {
        /** 任务唯一标识 */
        private String id;
        /** 任务名称 */
        private String taskName;
        /** 任务描述（给执行 Agent 的指令） */
        private String description;
        /** 建议的 Agent ID，可为空（由注册表按能力匹配） */
        private String agentId;
        /** 依赖的上游任务 ID 列表，构成 DAG */
        private List<String> dependsOn = new ArrayList<>();
    }

    /**
     * 规划执行结果。
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PlanResult {
        private boolean needPlan;
        private List<SubTask> subTasks;
        /** 各子任务的产出汇总文本，供最终回答生成使用 */
        private String summary;
        /** 执行层级数，反映任务图的深度 */
        private int layerCount;
        /** 成功子任务数 */
        private int successCount;
        /** 失败子任务数 */
        private int failCount;
        /** 参与执行的 Agent 标识 */
        private List<String> agentIds;

        public static PlanResult notNeeded() {
            return PlanResult.builder()
                    .needPlan(false)
                    .subTasks(Collections.emptyList())
                    .summary("")
                    .agentIds(Collections.emptyList())
                    .build();
        }

        public static PlanResult success(List<SubTask> tasks, String summary,
                                        int layerCount, int successCount, int failCount,
                                        List<String> agentIds) {
            return PlanResult.builder()
                    .needPlan(true)
                    .subTasks(tasks)
                    .summary(summary == null ? "" : summary)
                    .layerCount(layerCount)
                    .successCount(successCount)
                    .failCount(failCount)
                    .agentIds(agentIds == null ? Collections.emptyList() : agentIds)
                    .build();
        }
    }
}
