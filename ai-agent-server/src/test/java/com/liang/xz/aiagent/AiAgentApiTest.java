package com.liang.xz.aiagent;

import com.liang.xz.aiagent.agent.ChatAgentService;
import com.liang.xz.aiagent.agent.ChatAgentService.ChatRequest;
import com.liang.xz.aiagent.agent.ChatAgentService.ChatResponse;
import com.liang.xz.aiagent.config.AiProperties;
import com.liang.xz.aiagent.entity.AnalysisAlert;
import com.liang.xz.aiagent.entity.KnowledgeDoc;
import com.liang.xz.aiagent.service.AnalysisAlertService;
import com.liang.xz.aiagent.service.KnowledgeBaseService;
import com.liang.xz.aiagent.service.SearchAggregationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>AI智能体服务 — Service 层集成测试套件</p>
 * <p>全部通过直接调用 Service Bean 验证，不经过 HTTP / MockMvc</p>
 *
 * <pre>
 * 使用方式:
 *   mvn test -Dtest=AiAgentApiTest
 *
 * 前置条件:
 *   1. 本地 MySQL 运行，auth_platform 库已建表
 *   2. LLM API 网络可达（对话相关测试会真实调用 LLM）
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("AI智能体服务 - Service层集成测试")
class AiAgentApiTest {

    @Autowired
    private AiProperties aiProperties;

    @Autowired
    private KnowledgeBaseService kbService;

    @Autowired
    private SearchAggregationService searchService;

    @Autowired
    private AnalysisAlertService analysisService;

    @Autowired
    private ChatAgentService chatAgentService;

    // ==================== 1. 健康检查（Bean 注入 + 配置验证） ====================

    @Test
    @Order(1)
    @DisplayName("1.1 核心 Bean 注入验证")
    void healthStatus() {
        assertNotNull(kbService, "KnowledgeBaseService 应已注入");
        assertNotNull(searchService, "SearchAggregationService 应已注入");
        assertNotNull(analysisService, "AnalysisAlertService 应已注入");
        assertNotNull(chatAgentService, "ChatAgentService 应已注入");
        System.out.println("✅ 服务健康状态检查通过: 4 个核心 Service 均已注入");
    }

    @Test
    @Order(2)
    @DisplayName("1.2 AI 配置检查")
    void aiConfigHealth() {
        // 说明：LLM 供应商与模型已迁移到数据库（sys_ai_provider 表，由页面配置），
        // AiProperties 不再提供 llm 节点，故此处校验仍由 AiProperties 管理的嵌入模型配置。
        assertNotNull(aiProperties.getEmbedding(), "embedding 配置应已加载");
        assertNotNull(aiProperties.getEmbedding().getModel(), "embedding model 应已配置");
        System.out.println("✅ AI 配置检查通过 (embeddingModel="
                + aiProperties.getEmbedding().getModel() + ")");
    }

    // ==================== 2. 知识库 CRUD ====================

    private static Long savedDocId;

    @Test
    @Order(10)
    @DisplayName("2.1 添加知识库文档")
    void addKnowledgeDoc() {
        KnowledgeDoc doc = kbService.addDocument(
                "test-kb", "AI Agent使用手册",
                "这是AI智能体平台的使用说明文档。支持自然语言查询数据库、分析业务指标、生成预警报告等功能。",
                "TEXT", null, null, null, null);
        assertNotNull(doc, "添加文档应返回非空结果");
        assertNotNull(doc.getId(), "文档ID不应为空");
        savedDocId = doc.getId();
        System.out.println("✅ 添加知识库文档成功 (id=" + savedDocId + ")");
    }

    @Test
    @Order(11)
    @DisplayName("2.2 获取知识库列表")
    void listKnowledgeBases() {
        var result = kbService.listKnowledgeBases();
        assertNotNull(result, "知识库列表不应为空");
        System.out.println("✅ 知识库列表获取成功 (共" + result.size() + "个)");
    }

    @Test
    @Order(12)
    @DisplayName("2.3 获取指定知识库文档")
    void listDocsByKbName() {
        List<KnowledgeDoc> docs = kbService.listDocuments("test-kb");
        assertNotNull(docs, "文档列表不应为空");
        assertTrue(docs.size() > 0, "test-kb 知识库应至少有一篇文档");
        System.out.println("✅ 指定知识库文档获取成功 (共" + docs.size() + "篇)");
    }

    @Test
    @Order(13)
    @DisplayName("2.4 获取全部文档")
    void listAllDocs() {
        List<KnowledgeDoc> docs = kbService.listAllDocuments();
        assertNotNull(docs, "文档列表不应为空");
        assertTrue(docs.size() > 0, "应至少有一篇文档");
        System.out.println("✅ 全部文档获取成功 (共" + docs.size() + "篇)");
    }

    @Test
    @Order(14)
    @DisplayName("2.5 知识库统计")
    void getKnowledgeStats() {
        Map<String, Object> stats = kbService.getStats();
        assertNotNull(stats, "统计结果不应为空");
        System.out.println("✅ 知识库统计获取成功: " + stats);
    }

    // ==================== 3. 智能搜索 ====================

    @Test
    @Order(20)
    @DisplayName("3.1 本地搜索")
    void localSearch() {
        Map<String, Object> result = searchService.localSearch("AI Agent", 5);
        assertNotNull(result, "搜索结果不应为空");
        System.out.println("✅ 本地搜索完成");
    }

    @Test
    @Order(21)
    @DisplayName("3.2 搜索建议")
    void searchSuggest() {
        Map<String, Object> result = searchService.localSuggest("AI", 5);
        assertNotNull(result, "搜索建议不应为空");
        System.out.println("✅ 搜索建议获取成功");
    }

    @Test
    @Order(22)
    @DisplayName("3.3 RAG知识库检索")
    void ragSearch() {
        Map<String, Object> result = searchService.ragSearch("如何使用AI智能体", 3);
        assertNotNull(result, "RAG检索结果不应为空");
        System.out.println("✅ RAG检索完成");
    }

    @Test
    @Order(23)
    @DisplayName("3.4 混合搜索")
    void hybridSearch() {
        Map<String, Object> result = searchService.hybridSearch("业务数据", 5);
        assertNotNull(result, "混合搜索结果不应为空");
        System.out.println("✅ 混合搜索完成");
    }

    @Test
    @Order(24)
    @DisplayName("3.5 统一搜索(本地)")
    void unifiedSearch() {
        Map<String, Object> result = searchService.localSearch("工作流", 5);
        assertNotNull(result, "统一搜索结果不应为空");
        System.out.println("✅ 统一搜索完成");
    }

    @Test
    @Order(25)
    @DisplayName("3.6 索引统计")
    void indexStats() {
        long count = searchService.getLocalIndexCount();
        System.out.println("✅ 索引统计获取成功 (索引数: " + count + ")");
    }

    // ==================== 4. 数据分析预警 ====================

    @Test
    @Order(30)
    @DisplayName("4.1 获取当前业务指标")
    void getMetrics() {
        Map<String, Double> metrics = analysisService.getCurrentMetrics();
        assertNotNull(metrics, "业务指标不应为空");
        System.out.println("✅ 业务指标获取成功");
    }

    @Test
    @Order(31)
    @DisplayName("4.2 手动触发分析预警")
    void triggerAnalysis() {
        var result = analysisService.triggerAnalysis();
        assertNotNull(result, "分析结果不应为空");
        System.out.println("✅ 分析预警触发成功 (告警数: " + result.alerts().size() + ")");
    }

    @Test
    @Order(32)
    @DisplayName("4.3 即时分析问答")
    void instantAnalysis() {
        var result = analysisService.instantAnalysis("当前系统有哪些异常？请分析原因");
        assertNotNull(result, "分析结果不应为空");
        System.out.println("✅ 即时分析完成");
    }

    @Test
    @Order(33)
    @DisplayName("4.4 获取预警列表")
    void getAlerts() {
        List<AnalysisAlert> alerts = analysisService.getUnresolvedAlerts();
        assertNotNull(alerts, "预警列表不应为空");
        System.out.println("✅ 预警列表获取成功 (共" + alerts.size() + "条)");
    }

    @Test
    @Order(34)
    @DisplayName("4.5 获取预警汇总")
    void getAlertSummary() {
        var summary = analysisService.getAlertSummary();
        assertNotNull(summary, "预警汇总不应为空");
        System.out.println("✅ 预警汇总获取成功");
    }

    @Test
    @Order(35)
    @DisplayName("4.6 按级别查询预警")
    void getAlertsByLevel() {
        List<AnalysisAlert> alerts = analysisService.getAlertsByLevel("CRITICAL");
        assertNotNull(alerts, "预警列表不应为空");
        System.out.println("✅ 按级别查询预警完成 (共" + alerts.size() + "条)");
    }

    @Test
    @Order(36)
    @DisplayName("4.7 获取最近N条预警")
    void getRecentAlerts() {
        List<AnalysisAlert> alerts = analysisService.getRecentAlerts(5);
        assertNotNull(alerts, "最近预警列表不应为空");
        System.out.println("✅ 最近预警获取成功 (共" + alerts.size() + "条)");
    }

    // ==================== 5. AI 智能对话Agent ====================

    @Test
    @Order(40)
    @DisplayName("5.1 快速问答(无记忆)")
    void quickAsk() {
        ChatResponse response = chatAgentService.quickAsk("查看待审批任务数");
        assertNotNull(response, "问答结果不应为空");
        assertNotNull(response.getAnswer(), "问答答案不应为空");
        System.out.println("✅ 快速问答完成");
    }

    @Test
    @Order(41)
    @DisplayName("5.2 智能对话(有记忆)")
    void chatAsk() {
        ChatRequest request = ChatRequest.builder()
                .sessionId("test-session-001")
                .question("获取当前业务指标")
                .useMemory(true)
                .build();
        var response = chatAgentService.chat(request);
        assertNotNull(response, "对话响应不应为空");
        System.out.println("✅ 智能对话完成");
    }

    @Test
    @Order(42)
    @DisplayName("5.3 多轮对话测试")
    void multiTurnChat() {
        ChatRequest req1 = ChatRequest.builder()
                .sessionId("test-session-multi")
                .question("列出所有知识库")
                .useMemory(true)
                .build();
        var r1 = chatAgentService.chat(req1);
        assertNotNull(r1, "第1轮对话不应为空");

        ChatRequest req2 = ChatRequest.builder()
                .sessionId("test-session-multi")
                .question("第一个知识库有多少文档？")
                .useMemory(true)
                .build();
        var r2 = chatAgentService.chat(req2);
        assertNotNull(r2, "第2轮对话不应为空");

        System.out.println("✅ 多轮对话完成");
    }

    @Test
    @Order(43)
    @DisplayName("5.4 服务状态查询")
    void chatStatus() {
        int sessionCount = chatAgentService.getActiveSessionCount();
        System.out.println("✅ 对话服务状态查询成功 (活跃会话: " + sessionCount + ")");
    }

    @Test
    @Order(44)
    @DisplayName("5.5 清除会话")
    void clearSession() {
        chatAgentService.clearSession("test-session-001");
        System.out.println("✅ 会话清除成功");
    }

    // ==================== 6. 清理测试数据 ====================

    @Test
    @Order(90)
    @DisplayName("6.1 清理测试文档")
    void cleanTestKnowledge() {
        if (savedDocId != null) {
            kbService.deleteDocument(savedDocId);
        }
        System.out.println("✅ 测试数据清理完成");
    }

    // ==================== 综合报告 ====================

    @AfterAll
    static void report() {
        System.out.println("\n========================================");
        System.out.println("  AI智能体服务测试报告");
        System.out.println("========================================");
        System.out.println("测试方式: 直接调用 Service Bean（不经过 HTTP/MockMvc）");
        System.out.println("知识库CRUD / 搜索 / 分析预警 / AI对话Agent 均已覆盖");
        System.out.println("========================================");
    }
}
