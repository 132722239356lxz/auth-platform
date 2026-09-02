package com.liang.xz.aiagent.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * <p>多 Agent 编排日志仓储 —— 记录子任务调用明细与编排汇总</p>
 *
 * <p><b>设计要点：</b></p>
 * <ul>
 *   <li><b>异步写入：</b>日志落库发生在编排执行路径上，同步写会给每次对话叠加数据库往返延迟。
 *       采用 {@code @Async} 交由独立线程池执行，主流程不受影响。</li>
 *   <li><b>失败静默：</b>日志是观测设施而非业务功能，写库失败只记录 warn 日志，
 *       绝不能因日志问题导致编排链路失败——这是"观测不影响可用性"的基本原则。</li>
 *   <li><b>内容截断：</b>Agent 的输入输出可能很大（如检索到的文档全文），
 *       入库前统一截断，避免撑爆字段与浪费存储。</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Repository
public class AgentTaskLogRepository {

    /** 输入输出的最大入库长度，超出部分截断（避免大对象撑爆字段） */
    private static final int MAX_CONTENT_LENGTH = 8000;

    private final JdbcTemplate jdbcTemplate;

    public AgentTaskLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 异步记录一次子任务（Agent）调用。
     *
     * @param params 子任务日志参数
     */
    @Async
    public void logTask(AgentTaskLogParams params) {
        if (params == null || params.traceId == null || params.taskId == null) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "INSERT INTO ai_agent_task_log (trace_id, session_id, task_id, parent_trace_id, "
                            + "agent_id, task_name, layer_index, depends_on, task_input, task_output, "
                            + "elapsed_ms, success, error_msg, create_time) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?, NOW())",
                    params.traceId,
                    params.sessionId,
                    params.taskId,
                    params.parentTraceId,
                    truncate(params.agentId, 50),
                    truncate(params.taskName, 200),
                    params.layerIndex,
                    truncate(params.dependsOn, 200),
                    truncate(params.taskInput, MAX_CONTENT_LENGTH),
                    truncate(params.taskOutput, MAX_CONTENT_LENGTH),
                    params.elapsedMs,
                    params.success ? 1 : 0,
                    truncate(params.errorMsg, 1000));
        } catch (Exception e) {
            log.warn("[AgentTaskLog] 写入子任务日志失败(不影响主流程): traceId={}, taskId={}, reason={}",
                    params.traceId, params.taskId, e.getMessage());
        }
    }

    /**
     * 异步记录一次编排的汇总信息。
     *
     * @param params 编排汇总日志参数
     */
    @Async
    public void logWorkflow(AgentWorkflowLogParams params) {
        if (params == null || params.traceId == null) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "INSERT INTO ai_agent_workflow_log (trace_id, session_id, question, agent_ids, "
                            + "task_count, layer_count, success_count, fail_count, elapsed_ms, "
                            + "fallback, remark, create_time) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?, NOW()) "
                            + "ON DUPLICATE KEY UPDATE task_count=VALUES(task_count), "
                            + "success_count=VALUES(success_count), fail_count=VALUES(fail_count), "
                            + "elapsed_ms=VALUES(elapsed_ms), fallback=VALUES(fallback), remark=VALUES(remark)",
                    params.traceId,
                    params.sessionId,
                    truncate(params.question, 1000),
                    truncate(params.agentIds, 200),
                    params.taskCount,
                    params.layerCount,
                    params.successCount,
                    params.failCount,
                    params.elapsedMs,
                    params.fallback ? 1 : 0,
                    truncate(params.remark, 500));
        } catch (Exception e) {
            log.warn("[AgentTaskLog] 写入编排汇总日志失败(不影响主流程): traceId={}, reason={}",
                    params.traceId, e.getMessage());
        }
    }

    // ======================== 查询（排障用） ========================

    /**
     * 查询某次编排的全部子任务调用明细。
     *
     * @param traceId 编排链路追踪ID
     * @return 按层级与创建时间排序的子任务日志
     */
    public List<Map<String, Object>> findByTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return List.of();
        }
        try {
            return jdbcTemplate.queryForList(
                    "SELECT id, trace_id, session_id, task_id, agent_id, task_name, layer_index, "
                            + "depends_on, left(task_input, 500) AS task_input, "
                            + "left(task_output, 1000) AS task_output, "
                            + "elapsed_ms, success, error_msg, create_time "
                            + "FROM ai_agent_task_log WHERE trace_id = ? "
                            + "ORDER BY layer_index ASC, id ASC", traceId);
        } catch (Exception e) {
            log.warn("[AgentTaskLog] 查询子任务日志失败: traceId={}, reason={}", traceId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 查询最近的编排汇总记录。
     *
     * @param sessionId 会话ID，为空则不限
     * @param limit    返回条数
     */
    public List<Map<String, Object>> findWorkflows(String sessionId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        try {
            if (sessionId == null || sessionId.isBlank()) {
                return jdbcTemplate.queryForList(
                        "SELECT trace_id, session_id, left(question, 200) AS question, agent_ids, "
                                + "task_count, layer_count, success_count, fail_count, elapsed_ms, "
                                + "fallback, remark, create_time "
                                + "FROM ai_agent_workflow_log ORDER BY id DESC LIMIT ?", safeLimit);
            }
            return jdbcTemplate.queryForList(
                    "SELECT trace_id, session_id, left(question, 200) AS question, agent_ids, "
                            + "task_count, layer_count, success_count, fail_count, elapsed_ms, "
                            + "fallback, remark, create_time "
                            + "FROM ai_agent_workflow_log WHERE session_id = ? "
                            + "ORDER BY id DESC LIMIT ?", sessionId, safeLimit);
        } catch (Exception e) {
            log.warn("[AgentTaskLog] 查询编排汇总日志失败: reason={}", e.getMessage());
            return List.of();
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max) + "...[截断]";
    }

    /**
     * 子任务调用日志参数。
     */
    public static class AgentTaskLogParams {
        public String traceId;
        public String sessionId;
        public String taskId;
        public String parentTraceId;
        public String agentId;
        public String taskName;
        public int layerIndex;
        public String dependsOn;
        public String taskInput;
        public String taskOutput;
        public long elapsedMs;
        public boolean success;
        public String errorMsg;
    }

    /**
     * 编排汇总日志参数。
     */
    public static class AgentWorkflowLogParams {
        public String traceId;
        public String sessionId;
        public String question;
        public String agentIds;
        public int taskCount;
        public int layerCount;
        public int successCount;
        public int failCount;
        public long elapsedMs;
        public boolean fallback;
        public String remark;
    }
}
