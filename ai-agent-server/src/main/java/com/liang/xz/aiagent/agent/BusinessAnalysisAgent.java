package com.liang.xz.aiagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.aiagent.config.AiProperties;
import com.liang.xz.aiagent.entity.AnalysisAlert;
import com.liang.xz.aiagent.llm.LlmClient;
import com.liang.xz.aiagent.repository.AlertRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * <p>业务数据分析预警智能体</p>
 * <p>
 * 核心能力:
 * 1. 定时采集业务数据指标
 * 2. 规则引擎评估 → 分级预警
 * 3. LLM 深度分析 → 生成洞察报告和改进建议
 * 4. 支持自定义分析任务和即时查询
 * </p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
public class BusinessAnalysisAgent {

    private final DataQueryTool dataQueryTool;
    private final AlertRuleEngine ruleEngine;
    private final AlertRecordRepository alertRepository;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final AiProperties.AnalysisConfig analysisConfig;

    public BusinessAnalysisAgent(DataQueryTool dataQueryTool,
                                  AlertRuleEngine ruleEngine,
                                  AlertRecordRepository alertRepository,
                                  LlmClient llmClient,
                                  ObjectMapper objectMapper,
                                  AiProperties aiProperties) {
        this.dataQueryTool = dataQueryTool;
        this.ruleEngine = ruleEngine;
        this.alertRepository = alertRepository;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.analysisConfig = aiProperties.getAnalysis();
    }

    // ==================== 定时自动分析 ====================

    /**
     * 每30分钟自动执行业务数据分析和预警
     */
    @Scheduled(cron = "${ai-agent.analysis.cron:0 0/30 * * * ?}")
    public void scheduledAnalysis() {
        if (!analysisConfig.isEnabled()) {
            return;
        }
        log.info("=== Scheduled business analysis started ===");
        try {
            analyzeAndAlert("定时自动分析", AlertRuleEngine.defaultBusinessRules());
        } catch (Exception e) {
            log.error("Scheduled analysis failed", e);
        }
    }

    // ==================== 核心分析流程 ====================

    /**
     * 采集指标 → 规则评估 → LLM 深度分析 → 生成预警
     */
    public AnalysisResult analyzeAndAlert(String taskName, List<AlertRuleEngine.AlertRule> rules) {
        // Step 1: 采集数据指标
        Map<String, Double> metrics = collectMetrics();
        log.info("Collected {} metrics for analysis", metrics.size());

        // Step 2: 规则引擎评估
        List<AlertRuleEngine.AlertEvaluation> evaluations = ruleEngine.evaluateAll(rules, metrics);

        // Step 3: LLM 深度分析关键指标
        String llmInsight = generateInsight(taskName, metrics, evaluations);

        // Step 4: 生成预警记录
        List<AnalysisAlert> alerts = new ArrayList<>();
        for (AlertRuleEngine.AlertEvaluation eval : evaluations) {
            AnalysisAlert alert = buildAlert(eval, llmInsight, metrics);
            alertRepository.save(alert);
            alerts.add(alert);
        }

        // Step 5: 如果没有规则触发但有洞察,生成一条 INFO 级别记录
        if (alerts.isEmpty() && llmInsight != null && !llmInsight.isBlank()) {
            AnalysisAlert info = AnalysisAlert.builder()
                    .alertName(taskName + " - 综合洞察")
                    .analysisType("INSIGHT")
                    .alertLevel("INFO")
                    .alertContent("业务运行正常，综合洞察报告")
                    .analysisDetail(llmInsight)
                    .metricsJson(toJson(metrics))
                    .isRead(false)
                    .resolved(false)
                    .build();
            alertRepository.save(info);
            alerts.add(info);
        }

        log.info("Analysis complete: {} alerts generated", alerts.size());
        return new AnalysisResult(metrics, evaluations, alerts, llmInsight);
    }

    /**
     * 即时分析(用户触发)
     */
    public AnalysisResult instantAnalysis(String query) {
        // 采集最新指标
        Map<String, Double> metrics = collectMetrics();

        // 使用 LLM 回答用户的分析查询
        String prompt = buildMetricsPrompt(metrics);
        String insight = llmClient.chat(
                "你是一个业务数据分析师。请基于以下业务指标数据回答用户的分析问题。" +
                "给出专业的数据分析洞察和建议。",
                "业务指标:\n" + prompt + "\n\n用户问题: " + query);

        List<AnalysisAlert> alerts = new ArrayList<>();
        if (insight != null && !insight.isBlank()) {
            AnalysisAlert alert = AnalysisAlert.builder()
                    .alertName("即时分析: " + truncate(query, 100))
                    .analysisType("INSIGHT")
                    .alertLevel("INFO")
                    .alertContent(query)
                    .analysisDetail(insight)
                    .metricsJson(toJson(metrics))
                    .isRead(false).resolved(false)
                    .build();
            alertRepository.save(alert);
            alerts.add(alert);
        }

        return new AnalysisResult(metrics, List.of(), alerts, insight);
    }

    // ==================== 指标采集 ====================

    /**
     * 从各业务表采集关键指标
     */
    public Map<String, Double> collectMetrics() {
        Map<String, Double> metrics = new LinkedHashMap<>();

        // 工作流指标
        collectWorkflowMetrics(metrics);
        // 消息指标
        collectMessageMetrics(metrics);
        // 预警指标
        collectAlertMetrics(metrics);

        return metrics;
    }

    /** 近 7 天时间边界表达式（受 DataQueryTool 白名单约束） */
    private static final String LAST_7_DAYS = "DATE_SUB(NOW(), INTERVAL 7 DAY)";

    /** 近 1 天时间边界表达式（受 DataQueryTool 白名单约束） */
    private static final String LAST_1_DAY = "DATE_SUB(NOW(), INTERVAL 1 DAY)";

    private void collectWorkflowMetrics(Map<String, Double> metrics) {
        try {
            // 待审批任务数
            var r1 = dataQueryTool.aggregate("wf_task", "COUNT", "*", null,
                    List.of(filter("status", "EQ", "PENDING")));
            metrics.put("pendingTaskCount", getCount(r1));

            // 今日审批量
            var r2 = dataQueryTool.aggregate("wf_instance", "COUNT", "*", null,
                    List.of(filterExpr("create_time", "GE", "CURDATE()")));
            metrics.put("todayCreatedCount", getCount(r2));

            // 驳回数
            var r3 = dataQueryTool.aggregate("wf_instance", "COUNT", "*", null,
                    List.of(filter("status", "EQ", "REJECTED"),
                            filterExpr("update_time", "GE", LAST_7_DAYS)));
            metrics.put("rejectCount", getCount(r3));

            // 总审批数
            var r4 = dataQueryTool.aggregate("wf_instance", "COUNT", "*", null,
                    List.of(filterExpr("update_time", "GE", LAST_7_DAYS)));
            metrics.put("totalApprovalCount", getCount(r4));

            // 近7天日均
            var last7Total = dataQueryTool.aggregate("wf_instance", "COUNT", "*", null,
                    List.of(filterExpr("create_time", "GE", LAST_7_DAYS)));
            metrics.put("avgLastWeekCount", getCount(last7Total) / 7.0);

            // 今日
            var r6 = dataQueryTool.aggregate("wf_instance", "COUNT", "*", null,
                    List.of(filterExpr("create_time", "GE", "CURDATE()")));
            metrics.put("todayCount", getCount(r6));

        } catch (Exception e) {
            log.warn("Workflow metrics collection failed: {}", e.getMessage());
        }
    }

    private void collectMessageMetrics(Map<String, Double> metrics) {
        try {
            var r1 = dataQueryTool.aggregate("msg_record", "COUNT", "*", null,
                    List.of(filter("status", "EQ", "FAILED"),
                            filterExpr("create_time", "GE", LAST_1_DAY)));
            metrics.put("failCount", getCount(r1));

            var r2 = dataQueryTool.aggregate("msg_record", "COUNT", "*", null,
                    List.of(filterExpr("create_time", "GE", LAST_1_DAY)));
            metrics.put("totalSendCount", getCount(r2));
        } catch (Exception e) {
            log.warn("Message metrics collection failed: {}", e.getMessage());
        }
    }

    /**
     * 构造普通值过滤条件（值走参数化绑定）。
     */
    private DataQueryTool.QueryFilter filter(String field, String op, Object value) {
        return DataQueryTool.QueryFilter.builder().field(field).op(op).value(value).build();
    }

    /**
     * 构造 SQL 函数过滤条件（表达式受 DataQueryTool 白名单校验）。
     */
    private DataQueryTool.QueryFilter filterExpr(String field, String op, String valueExpr) {
        return DataQueryTool.QueryFilter.builder().field(field).op(op).valueExpr(valueExpr).build();
    }

    private void collectAlertMetrics(Map<String, Double> metrics) {
        metrics.put("unresolvedAlertCount", (double) alertRepository.countUnresolved());
        metrics.put("criticalAlertCount", (double) alertRepository.countUnresolvedByLevel("CRITICAL"));
    }

    private double getCount(DataQueryTool.DataQueryResult result) {
        if (result.isSuccess() && !result.getData().isEmpty()) {
            Object val = result.getData().get(0).values().iterator().next();
            return val instanceof Number n ? n.doubleValue() : 0.0;
        }
        return 0.0;
    }

    // ==================== LLM 洞察生成 ====================

    private String generateInsight(String taskName, Map<String, Double> metrics,
                                   List<AlertRuleEngine.AlertEvaluation> triggereds) {
        if (triggereds.isEmpty()) {
            return "所有指标正常，业务运行稳定。";
        }

        StringBuilder context = new StringBuilder();
        context.append("=== 触发的预警 ===\n");
        for (var e : triggereds) {
            context.append(String.format("- [%s] %s: %s (当前值: %.2f)\n",
                    e.getLevel(), e.getRule().getRuleName(), e.getDescription(), e.getCurrentValue()));
        }
        context.append("\n=== 当前关键指标 ===\n");
        metrics.forEach((k, v) -> context.append(String.format("%s = %.2f\n", k, v)));

        return llmClient.chat("""
                你是业务数据分析与预警专家。请分析以下预警和指标数据，给出:
                1. 问题根因分析
                2. 业务影响评估
                3. 具体可执行的改进建议(按优先级排序)
                回答简洁专业，使用中文。
                """, context.toString());
    }

    // ==================== 辅助方法 ====================

    private AnalysisAlert buildAlert(AlertRuleEngine.AlertEvaluation eval,
                                     String insight, Map<String, Double> metrics) {
        return AnalysisAlert.builder()
                .alertName(eval.getRule().getRuleName())
                .analysisType(eval.getRule().getAnalysisType())
                .dataSource(eval.getRule().getDataSource())
                .alertLevel(eval.getLevel())
                .alertContent(eval.getDescription())
                .analysisDetail(insight)
                .suggestion(extractSuggestion(insight))
                .metricsJson(toJson(metrics))
                .isRead(false)
                .resolved(false)
                .build();
    }

    private String extractSuggestion(String insight) {
        if (insight == null) return "";
        int idx = insight.indexOf("改进建议");
        if (idx >= 0) {
            return insight.substring(idx);
        }
        return insight.length() > 500 ? insight.substring(0, 500) + "..." : insight;
    }

    private String buildMetricsPrompt(Map<String, Double> metrics) {
        StringBuilder sb = new StringBuilder();
        metrics.forEach((k, v) -> sb.append(String.format("  %s = %.2f\n", k, v)));
        return sb.toString();
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    // ==================== 结果类 ====================

    public record AnalysisResult(
            Map<String, Double> metrics,
            List<AlertRuleEngine.AlertEvaluation> evaluations,
            List<AnalysisAlert> alerts,
            String llmInsight
    ) {}
}
