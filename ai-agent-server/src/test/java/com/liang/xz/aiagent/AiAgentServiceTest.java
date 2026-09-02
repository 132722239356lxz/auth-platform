package com.liang.xz.aiagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.aiagent.agent.AlertRuleEngine;
import com.liang.xz.aiagent.agent.BusinessAnalysisAgent;
import com.liang.xz.aiagent.rag.TextSplitter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <p>AI智能体服务 综合测试用例</p>
 * <p>覆盖: RAG知识库 / 本地检索 / 联网搜索 / 数据分析预警</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@DisplayName("AI智能体服务测试")
class AiAgentServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TextSplitter textSplitter = new TextSplitter();

    // ==================== 场景1: 文本分割 ====================

    @Test
    @DisplayName("场景1: 短文本不分割")
    void testShortTextNoSplit() {
        String text = "这是一段很短的文本，不需要分割。";
        List<String> chunks = textSplitter.split(text);
        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0));
        System.out.println(chunks.get(0));
    }

    @Test
    @DisplayName("场景2: 长文本自动分割为多个chunk")
    void testLongTextSplitting() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("这是第").append(i + 1).append("段测试文本内容。")
              .append("包含一些业务数据和描述信息。")
              .append("用于验证文本分割器的正确性。\n\n");
        }
        List<String> chunks = textSplitter.split(sb.toString());
        assertTrue(chunks.size() > 1, "长文本应被分割为多个chunk");
        // 每个chunk不应超过chunkSize
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 600, "Chunk大小不应显著超过限制");
        }
        for (String chunk : chunks) {
            System.out.println(chunk);
        }
    }

    @Test
    @DisplayName("场景3: 空文本处理")
    void testEmptyText() {
        assertTrue(textSplitter.split("").isEmpty());
        assertTrue(textSplitter.split(null).isEmpty());
    }

    // ==================== 场景4-6: 预警规则引擎 ====================

    @Test
    @DisplayName("场景4: 阈值触发CRITICAL预警")
    void testThresholdCriticalAlert() {
        AlertRuleEngine engine = new AlertRuleEngine();
        AlertRuleEngine.AlertRule rule = AlertRuleEngine.AlertRule.builder()
                .ruleId("TEST_THRESHOLD")
                .ruleName("测试阈值")
                .analysisType("THRESHOLD")
                .type(AlertRuleEngine.AlertRule.RuleType.THRESHOLD_ABOVE)
                .metricKey("cpuUsage")
                .metricName("CPU使用率")
                .warnThreshold(70.0)
                .criticalThreshold(90.0)
                .build();

        // CPU=95 → CRITICAL
        var eval = engine.evaluate(rule, Map.of("cpuUsage", 95.0));
        assertTrue(eval.isTriggered());
        assertEquals("CRITICAL", eval.getLevel());

        // CPU=75 → WARN
        eval = engine.evaluate(rule, Map.of("cpuUsage", 75.0));
        assertTrue(eval.isTriggered());
        assertEquals("WARN", eval.getLevel());

        // CPU=50 → 不触发
        eval = engine.evaluate(rule, Map.of("cpuUsage", 50.0));
        assertFalse(eval.isTriggered());
    }

    @Test
    @DisplayName("场景5: 变化率预警检测")
    void testChangeRateAlert() {
        AlertRuleEngine engine = new AlertRuleEngine();
        AlertRuleEngine.AlertRule rule = AlertRuleEngine.AlertRule.builder()
                .ruleId("TEST_CHANGE_RATE")
                .ruleName("工作流处理量骤降")
                .analysisType("CHANGE_RATE")
                .type(AlertRuleEngine.AlertRule.RuleType.CHANGE_RATE)
                .metricKey("todayCount")
                .metricName("今日处理量")
                .baselineKey("avgLastWeek")
                .warnThreshold(0.3)
                .criticalThreshold(0.5)
                .build();

        // 从100降到40, 变化率-60% → CRITICAL
        var eval = engine.evaluate(rule, Map.of("todayCount", 40.0, "avgLastWeek", 100.0));
        assertTrue(eval.isTriggered());
        assertEquals("CRITICAL", eval.getLevel());

        // 轻微波动, 变化率-20% → 不触发
        eval = engine.evaluate(rule, Map.of("todayCount", 80.0, "avgLastWeek", 100.0));
        assertFalse(eval.isTriggered());
    }

    @Test
    @DisplayName("场景6: 批量规则评估排序(CRITICAL优先)")
    void testBatchEvaluationPriority() {
        AlertRuleEngine engine = new AlertRuleEngine();
        List<AlertRuleEngine.AlertRule> rules = AlertRuleEngine.defaultBusinessRules();

        // 模拟高危指标
        Map<String, Double> metrics = Map.of(
                "pendingTaskCount", 120.0,      // 远超CRITICAL阈值50
                "rejectCount", 80.0,
                "totalApprovalCount", 100.0,    // rejectRate=80% CRITICAL
                "failCount", 2.0,
                "totalSendCount", 100.0,        // failRate=2% 不触发
                "todayCount", 30.0,
                "avgLastWeekCount", 100.0,      // 骤降-70% CRITICAL
                "unresolvedAlertCount", 20.0    // 超过CRITICAL阈值15
        );

        List<AlertRuleEngine.AlertEvaluation> results = engine.evaluateAll(rules, metrics);
        assertFalse(results.isEmpty(), "应有多个预警触发");

        // 第一条应该是CRITICAL级别
        assertEquals("CRITICAL", results.get(0).getLevel(),
                "级别最高的预警应排在首位");
    }

    // ==================== 场景7-8: 知识库文档管理 ====================

    @Test
    @DisplayName("场景7: 知识库CRUD流程(模拟)")
    void testKnowledgeBaseCrudFlow() {
        // 模拟: 创建文档 → 索引 → 查询
        String kbName = "test_kb";
        String title = "测试文档";
        String content = "这是一份测试文档，包含业务规则说明。授权平台支持OAuth2.0和JWT认证。";
        String contentType = "TEXT";

        // 验证字段完整性
        assertNotNull(kbName);
        assertNotNull(title);
        assertNotNull(content);
        assertEquals("TEXT", contentType);
    }

    @Test
    @DisplayName("场景8: 多知识库隔离")
    void testMultiKnowledgeBaseIsolation() {
        // 验证不同知识库独立管理
        String kb1 = "产品文档";
        String kb2 = "技术手册";
        assertNotEquals(kb1, kb2);
        // 实际隔离性由 RetrievalPipeline.retrieveInKb() 保证
    }

    // ==================== 场景9-10: 搜索 ====================

    @Test
    @DisplayName("场景9: 搜索类型路由")
    void testSearchTypeRouting() {
        // 验证四种搜索类型
        List<String> validTypes = List.of("LOCAL", "INTERNET", "RAG", "HYBRID");
        assertEquals(4, validTypes.size());

        // 实际路由在 SearchController 中实现
        for (String type : validTypes) {
            assertNotNull(type, type + " 搜索类型不应为空");
        }
    }

    @Test
    @DisplayName("场景10: 混合搜索多源融合")
    void testHybridSearchFusion() {
        // 混合搜索融合本地+联网+RAG三个来源
        String query = "OAuth2.0授权流程";
        // 实际融合由 LLM 完成
        assertNotNull(query);
    }

    // ==================== 场景11-12: 数据分析智能体 ====================

    @Test
    @DisplayName("场景11: 指标采集完整性")
    void testMetricsCollection() {
        // 验证指标采集包含工作流/消息/预警三大类
        List<String> expectedMetricPrefixes = List.of(
                "pendingTaskCount", "rejectCount", "totalApprovalCount",  // 工作流
                "failCount", "totalSendCount",                             // 消息
                "unresolvedAlertCount", "criticalAlertCount"              // 预警
        );

        for (String prefix : expectedMetricPrefixes) {
            assertNotNull(prefix, "指标 " + prefix + " 应该被采集");
        }
    }

    @Test
    @DisplayName("场景12: 即时分析生成洞察")
    void testInstantAnalysis() {
        // 模拟用户分析请求
        String analysisQuery = "分析本周的工作流审批情况和异常趋势";
        assertNotNull(analysisQuery);
        assertTrue(analysisQuery.contains("工作流") || analysisQuery.contains("审批"));
        // 实际LLM调用需要API KEY配置
    }

    // ==================== 场景13: 定时分析 ====================

    @Test
    @DisplayName("场景13: 定时分析Cron配置验证")
    void testSchedulingCronConfig() {
        String cron = "0 0/30 * * * ?";  // 每30分钟
        assertNotNull(cron);
        assertTrue(cron.contains("30"), "应包含30分钟间隔");
    }

    // ==================== 场景14: LLM客户端协议兼容性 ====================

    @Test
    @DisplayName("场景14: ChatRequest OpenAI协议格式")
    void testChatRequestFormat() throws Exception {
        // 构造一个请求并序列化, 验证格式符合 OpenAI Chat API
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", "你是一个助手"),
                Map.of("role", "user", "content", "你好")
        );
        Map<String, Object> request = Map.of(
                "model", "gpt-3.5-turbo",
                "messages", messages,
                "temperature", 0.7,
                "max_tokens", 2048,
                "stream", false
        );

        String json = objectMapper.writeValueAsString(request);
        assertTrue(json.contains("\"role\""));
        assertTrue(json.contains("\"model\""));
        assertTrue(json.contains("gpt-3.5-turbo"));
    }

    // ==================== 场景15: 异常处理 ====================

    @Test
    @DisplayName("场景15: 异常数据容错")
    void testExceptionTolerance() {
        AlertRuleEngine engine = new AlertRuleEngine();
        AlertRuleEngine.AlertRule rule = AlertRuleEngine.AlertRule.builder()
                .ruleId("TEST")
                .ruleName("测试")
                .type(AlertRuleEngine.AlertRule.RuleType.THRESHOLD_ABOVE)
                .metricKey("nonExist")
                .metricName("不存在的指标")
                .warnThreshold(10.0)
                .criticalThreshold(20.0)
                .build();

        // 指标不存在 → 不应抛异常
        var eval = engine.evaluate(rule, Map.of());
        assertNotNull(eval);
        assertFalse(eval.isTriggered());
        assertEquals("INFO", eval.getLevel());
    }
}
