package com.liang.xz.aiagent.service;

import com.liang.xz.aiagent.agent.AlertRuleEngine;
import com.liang.xz.aiagent.agent.BusinessAnalysisAgent;
import com.liang.xz.aiagent.entity.AnalysisAlert;
import com.liang.xz.aiagent.repository.AlertRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * <p>分析预警服务 — 对外暴露的业务分析API</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class AnalysisAlertService {

    private final BusinessAnalysisAgent analysisAgent;
    private final AlertRecordRepository alertRepository;
    private final AlertRuleEngine ruleEngine;

    public AnalysisAlertService(BusinessAnalysisAgent analysisAgent,
                                 AlertRecordRepository alertRepository,
                                 AlertRuleEngine ruleEngine) {
        this.analysisAgent = analysisAgent;
        this.alertRepository = alertRepository;
        this.ruleEngine = ruleEngine;
    }

    /**
     * 手动触发分析预警
     */
    public BusinessAnalysisAgent.AnalysisResult triggerAnalysis() {
        return analysisAgent.analyzeAndAlert("手动触发分析", AlertRuleEngine.defaultBusinessRules());
    }

    /**
     * 使用自定义规则分析
     */
    public BusinessAnalysisAgent.AnalysisResult triggerAnalysis(List<AlertRuleEngine.AlertRule> rules) {
        return analysisAgent.analyzeAndAlert("自定义分析", rules);
    }

    /**
     * 即时分析问答
     */
    public BusinessAnalysisAgent.AnalysisResult instantAnalysis(String query) {
        return analysisAgent.instantAnalysis(query);
    }

    /**
     * 获取当前业务指标
     */
    public Map<String, Double> getCurrentMetrics() {
        return analysisAgent.collectMetrics();
    }

    // ======================== 预警查询 ========================

    public List<AnalysisAlert> getUnresolvedAlerts() {
        return alertRepository.findUnresolved();
    }

    public List<AnalysisAlert> getAlertsByLevel(String level) {
        return alertRepository.findByAlertLevel(level);
    }

    public List<AnalysisAlert> getRecentAlerts(int limit) {
        return alertRepository.findRecent(limit);
    }

    public List<AnalysisAlert> getAlertsByType(String type) {
        return alertRepository.findByType(type);
    }

    /**
     * 分页条件查询预警
     */
    public Map<String, Object> queryAlerts(String keyword, String level, Boolean resolved, String analysisType,
                                           LocalDateTime startTime, LocalDateTime endTime, int page, int size) {
        List<AnalysisAlert> list = alertRepository.queryAlerts(keyword, level, resolved, analysisType,
                startTime, endTime, page, size);
        int total = alertRepository.countAlerts(keyword, level, resolved, analysisType, startTime, endTime);
        return Map.of("total", total, "list", list);
    }

    public Map<String, Object> getAlertSummary() {
        return Map.of(
                "critical", alertRepository.countUnresolvedByLevel("CRITICAL"),
                "warn", alertRepository.countUnresolvedByLevel("WARN"),
                "info", alertRepository.countUnresolvedByLevel("INFO"),
                "pending", alertRepository.countUnresolved()
        );
    }

    public void resolveAlert(Long id, String resolvedBy) {
        alertRepository.resolve(id, resolvedBy);
    }

    public void markAlertRead(Long id) {
        alertRepository.markRead(id);
    }
}
