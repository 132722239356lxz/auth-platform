package com.liang.xz.aiagent.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Predicate;

/**
 * <p>预警规则引擎 — 基于数据指标的规则匹配与分级预警</p>
 * <p>支持: 阈值判断 / 趋势检测 / 异常比例 / 排名变化</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
public class AlertRuleEngine {

    /**
     * 单一规则评估
     */
    public AlertEvaluation evaluate(AlertRule rule, Map<String, Double> metrics) {
        Double value = metrics.get(rule.getMetricKey());
        if (value == null) {
            return AlertEvaluation.noData(rule);
        }

        String level = "INFO";
        String description = "";
        boolean triggered = false;

        switch (rule.getType()) {
            case THRESHOLD_ABOVE -> {
                if (value > rule.getCriticalThreshold()) {
                    level = "CRITICAL"; triggered = true;
                    description = String.format("%s=%.2f 超过临界值 %.2f", rule.getMetricName(), value, rule.getCriticalThreshold());
                } else if (value > rule.getWarnThreshold()) {
                    level = "WARN"; triggered = true;
                    description = String.format("%s=%.2f 超过预警值 %.2f", rule.getMetricName(), value, rule.getWarnThreshold());
                }
            }
            case THRESHOLD_BELOW -> {
                if (value < rule.getCriticalThreshold()) {
                    level = "CRITICAL"; triggered = true;
                    description = String.format("%s=%.2f 低于临界值 %.2f", rule.getMetricName(), value, rule.getCriticalThreshold());
                } else if (value < rule.getWarnThreshold()) {
                    level = "WARN"; triggered = true;
                    description = String.format("%s=%.2f 低于预警值 %.2f", rule.getMetricName(), value, rule.getWarnThreshold());
                }
            }
            case CHANGE_RATE -> {
                Double baseline = metrics.getOrDefault(rule.getBaselineKey(), 0.0);
                if (baseline > 0) {
                    double rate = (value - baseline) / baseline;
                    if (Math.abs(rate) > rule.getCriticalThreshold()) {
                        level = "CRITICAL"; triggered = true;
                    } else if (Math.abs(rate) > rule.getWarnThreshold()) {
                        level = "WARN"; triggered = true;
                    }
                    if (triggered) {
                        description = String.format("%s 变化率 %.1f%% (%.2f -> %.2f)",
                                rule.getMetricName(), rate * 100, baseline, value);
                    }
                }
            }
            case RATIO -> {
                Double denominator = metrics.getOrDefault(rule.getBaselineKey(), 0.0);
                if (denominator > 0) {
                    double ratio = value / denominator;
                    if (ratio > rule.getCriticalThreshold()) {
                        level = "CRITICAL"; triggered = true;
                    } else if (ratio > rule.getWarnThreshold()) {
                        level = "WARN"; triggered = true;
                    }
                    if (triggered) {
                        description = String.format("%s/%s 比率=%.2f 超过阈值 %.2f",
                                rule.getMetricName(), rule.getBaselineKey(), ratio, rule.getWarnThreshold());
                    }
                }
            }
        }

        return AlertEvaluation.builder()
                .rule(rule)
                .triggered(triggered)
                .level(level)
                .description(description)
                .currentValue(value)
                .build();
    }

    /**
     * 批量规则评估
     */
    public List<AlertEvaluation> evaluateAll(List<AlertRule> rules, Map<String, Double> metrics) {
        return rules.stream()
                .map(r -> evaluate(r, metrics))
                .filter(AlertEvaluation::isTriggered)
                .sorted(Comparator.comparing(a -> {
                    return switch (a.getLevel()) {
                        case "CRITICAL" -> 0;
                        case "WARN" -> 1;
                        default -> 2;
                    };
                }))
                .toList();
    }

    // ==================== 内置规则构建器 ====================

    /**
     * 构建一组默认业务分析规则
     */
    public static List<AlertRule> defaultBusinessRules() {
        return List.of(
                // 工作流审批积压检测
                AlertRule.builder().ruleId("WF_PENDING_BACKLOG")
                        .ruleName("待审批任务积压").analysisType("THRESHOLD")
                        .type(AlertRule.RuleType.THRESHOLD_ABOVE)
                        .metricKey("pendingTaskCount").metricName("待审批任务数")
                        .warnThreshold(20.0).criticalThreshold(50.0)
                        .dataSource("wf_task").build(),

                // 工作流驳回率检测
                AlertRule.builder().ruleId("WF_REJECT_RATE")
                        .ruleName("工作流驳回率异常").analysisType("RATIO")
                        .type(AlertRule.RuleType.RATIO)
                        .metricKey("rejectCount").metricName("驳回数")
                        .baselineKey("totalApprovalCount")
                        .warnThreshold(0.3).criticalThreshold(0.5)
                        .dataSource("wf_instance").build(),

                // 消息发送失败率
                AlertRule.builder().ruleId("MSG_FAIL_RATE")
                        .ruleName("消息发送失败率").analysisType("RATIO")
                        .type(AlertRule.RuleType.RATIO)
                        .metricKey("failCount").metricName("失败数")
                        .baselineKey("totalSendCount")
                        .warnThreshold(0.05).criticalThreshold(0.1)
                        .dataSource("msg_record").build(),

                // 工作流日处理量骤降
                AlertRule.builder().ruleId("WF_VOLUME_DROP")
                        .ruleName("工作流日处理量骤降").analysisType("CHANGE_RATE")
                        .type(AlertRule.RuleType.CHANGE_RATE)
                        .metricKey("todayCount").metricName("今日处理量")
                        .baselineKey("avgLastWeekCount")
                        .warnThreshold(0.3).criticalThreshold(0.5)
                        .dataSource("wf_instance").build(),

                // 未处理预警积压
                AlertRule.builder().ruleId("ALERT_BACKLOG")
                        .ruleName("未处理预警积压").analysisType("THRESHOLD")
                        .type(AlertRule.RuleType.THRESHOLD_ABOVE)
                        .metricKey("unresolvedAlertCount").metricName("未处理预警数")
                        .warnThreshold(5.0).criticalThreshold(15.0)
                        .dataSource("ai_analysis_alert").build()
        );
    }

    // ==================== 数据类 ====================

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AlertRule {
        private String ruleId;
        private String ruleName;
        private String analysisType;   // TREND / ANOMALY / THRESHOLD / RATIO
        private RuleType type;
        private String metricKey;
        private String metricName;
        private String baselineKey;    // 基线指标key
        private Double warnThreshold;
        private Double criticalThreshold;
        private String dataSource;

        public enum RuleType {
            THRESHOLD_ABOVE, THRESHOLD_BELOW, CHANGE_RATE, RATIO
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AlertEvaluation {
        private AlertRule rule;
        private boolean triggered;
        private String level;
        private String description;
        private Double currentValue;

        public static AlertEvaluation noData(AlertRule rule) {
            return AlertEvaluation.builder()
                    .rule(rule).triggered(false).level("INFO")
                    .description("无数据").build();
        }
    }
}
