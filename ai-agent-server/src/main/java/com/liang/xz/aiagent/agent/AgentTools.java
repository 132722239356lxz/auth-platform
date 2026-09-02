package com.liang.xz.aiagent.agent;

import com.liang.xz.aiagent.config.AiProperties;
import com.liang.xz.aiagent.llm.LlmClient;
import com.liang.xz.aiagent.rag.RerankService;
import com.liang.xz.aiagent.repository.AlertRecordRepository;
import com.liang.xz.aiagent.repository.KnowledgeDocRepository;
import com.liang.xz.aiagent.search.LocalSearchEngine;
import com.liang.xz.aiagent.service.AiInvokeLogService;
import com.liang.xz.aiagent.service.AnalysisAlertService;
import com.liang.xz.aiagent.vector.VectorDocument;
import com.liang.xz.aiagent.vector.VectorStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * <p>LangChain4j Agent 工具集 — 供AI Agent调用的业务工具函数</p>
 * <p>使用 @Tool 注解标注，LangChain4j 会自动将方法注册为 LLM 可调用的工具</p>
 *
 * <p>每个工具调用均具备：
 * <ul>
 *   <li><b>幂等</b>：相同会话 + 相同工具 + 相同入参，在 {@code IDEMPOTENT_TTL_MS} 内直接复用缓存结果，避免重复触发外部系统（如 triggerAnalysis）</li>
 *   <li><b>重试</b>：外部系统调用失败时，最多重试 {@code MAX_RETRIES} 次，采用指数退避</li>
 * </ul>
 * </p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
public class AgentTools {

    /**
     * 工具调用最大重试次数（指数退避）。
     */
    private static final int MAX_RETRIES = 3;

    /**
     * 幂等缓存有效期（毫秒）：默认 10 分钟。
     */
    private static final long IDEMPOTENT_TTL_MS = 10L * 60 * 1000;

    private final AlertRecordRepository alertRepository;
    private final KnowledgeDocRepository knowledgeRepository;
    private final AnalysisAlertService analysisService;
    private final LocalSearchEngine searchEngine;
    private final VectorStore vectorStore;
    private final LlmClient llmClient;
    private final AiProperties aiProperties;
    private final NamedParameterJdbcTemplate jdbc;
    private final RerankService rerankService;
    private final AiInvokeLogService aiInvokeLogService;

    /**
     * 工具调用幂等缓存：key=会话ID:工具名:入参哈希 -> 缓存结果。
     */
    private final Map<String, CachedResult> idempotentCache = new ConcurrentHashMap<>();

    /**
     * 查询 embedding 语义缓存（跨会话共享）：对同一查询文本的向量化结果进行复用，
     * 减少重复调用 embedding 模型 API（语义查询减 API 调用）。
     */
    private static final QueryEmbeddingCache QUERY_EMBEDDING_CACHE = new QueryEmbeddingCache(200);

    public AgentTools(AlertRecordRepository alertRepository,
                      KnowledgeDocRepository knowledgeRepository,
                      AnalysisAlertService analysisService,
                      LocalSearchEngine searchEngine,
                      VectorStore vectorStore,
                      LlmClient llmClient,
                      AiProperties aiProperties,
                      NamedParameterJdbcTemplate jdbc,
                      RerankService rerankService,
                      AiInvokeLogService aiInvokeLogService) {
        this.alertRepository = alertRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.analysisService = analysisService;
        this.searchEngine = searchEngine;
        this.vectorStore = vectorStore;
        this.llmClient = llmClient;
        this.aiProperties = aiProperties;
        this.jdbc = jdbc;
        this.rerankService = rerankService;
        this.aiInvokeLogService = aiInvokeLogService;
    }

    /**
     * 获取查询向量：优先复用语义缓存，未命中才调用 embedding 模型 API。
     * 命中/未命中均记录到 AI 调用日志，用于观测语义缓存收益。
     *
     * @param query 查询文本
     * @return 查询向量；embedding 失败返回空列表
     */
    private List<Double> embedWithCache(String query) {
        long start = System.currentTimeMillis();
        List<Double> cached = QUERY_EMBEDDING_CACHE.get(query);
        if (cached != null) {
            QUERY_EMBEDDING_CACHE.recordHit();
            aiInvokeLogService.logEmbedding(null, true, 0, query,
                    System.currentTimeMillis() - start, true, null);
            return cached;
        }
        QUERY_EMBEDDING_CACHE.recordMiss();
        List<Double> embedding = llmClient.embed(query);
        boolean ok = embedding != null && !embedding.isEmpty();
        if (ok) {
            QUERY_EMBEDDING_CACHE.put(query, embedding);
        }
        aiInvokeLogService.logEmbedding(null, false, ok ? estimateTokens(query) : 0, query,
                System.currentTimeMillis() - start, ok, ok ? null : "embedding 返回空");
        return embedding == null ? List.of() : embedding;
    }

    /** 粗略估算 token（按字符数 / 2），仅用于日志统计 */
    private static int estimateTokens(String text) {
        return text == null ? 0 : Math.max(1, text.length() / 2);
    }

    /**
     * 统一工具执行入口：先查幂等缓存，未命中则带重试执行并缓存结果。
     *
     * @param toolName 工具名称（同时写入 ToolInvocationContext 供前端展示）
     * @param argKey   幂等入参标识（如 sql、query 文本）
     * @param action   实际业务动作
     * @return 工具执行结果文本
     */
    private String runTool(String toolName, String argKey, Supplier<String> action) {
        ToolInvocationContext.record(toolName);
        String idemKey = buildIdempotencyKey(toolName, argKey);
        CachedResult cached = idempotentCache.get(idemKey);
        if (cached != null && !cached.isExpired()) {
            log.info("[AgentTools] 命中幂等缓存, 跳过重复执行 tool={} key={}", toolName, idemKey);
            return cached.result();
        }

        String result = null;
        Throwable lastError = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                result = action.get();
                lastError = null;
                break;
            } catch (Exception e) {
                lastError = e;
                long backoffMs = (long) (Math.pow(2, attempt - 1) * 500L);
                log.warn("[AgentTools] 工具 {} 第 {}/{} 次执行失败，{}ms 后重试: {}",
                        toolName, attempt, MAX_RETRIES, backoffMs, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        if (lastError != null) {
            log.error("[AgentTools] 工具 {} 重试 {} 次后仍失败", toolName, MAX_RETRIES, lastError);
            return "工具 " + toolName + " 执行失败: " + lastError.getMessage();
        }
        idempotentCache.put(idemKey, new CachedResult(result));
        return result;
    }

    private String buildIdempotencyKey(String toolName, String argKey) {
        String sessionId = ToolInvocationContext.getSessionId();
        String base = (sessionId != null ? sessionId : "GLOBAL") + ":" + toolName + ":" + (argKey != null ? argKey : "");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(base.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(sessionId != null ? sessionId : "GLOBAL");
            sb.append(':').append(toolName).append(':');
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return base;
        }
    }

    // ==================== 预警分析工具 ====================

    @Tool("获取当前所有的分析预警记录，返回预警名称、级别、状态等信息")
    public String getAlerts() {
        return runTool("getAlerts", "unresolved", () -> {
            var alerts = analysisService.getUnresolvedAlerts();
            if (alerts.isEmpty()) return "当前没有未处理的预警";
            StringBuilder sb = new StringBuilder("未处理预警列表:\n");
            for (var a : alerts) {
                sb.append(String.format("- [%s] %s: %s\n", a.getAlertLevel(), a.getAlertName(), a.getAlertContent()));
            }
            return sb.toString();
        });
    }

    @Tool("获取预警汇总统计，包括各级别(CRITICAL/WARN/INFO)的未处理数量和总数")
    public String getAlertSummary() {
        return runTool("getAlertSummary", "summary", () -> {
            Map<String, Object> summary = analysisService.getAlertSummary();
            return String.format("预警汇总: 待处理=%d, 严重=%d, 警告=%d, 信息=%d",
                    summary.get("totalUnresolved"), summary.get("criticalCount"),
                    summary.get("warnCount"), summary.get("infoCount"));
        });
    }

    @Tool("触发一次实时的业务数据分析预警，采集当前业务指标并运行规则引擎检测异常")
    public String triggerAnalysis() {
        return runTool("triggerAnalysis", "once", () -> {
            var result = analysisService.triggerAnalysis();
            return String.format("分析完成: 采集%s个指标, 触发%s条预警, 洞察: %s",
                    result.metrics().size(), result.alerts().size(),
                    result.llmInsight() != null
                            ? result.llmInsight().substring(0, Math.min(200, result.llmInsight().length())) : "无");
        });
    }

    @Tool("获取当前关键业务指标，包括工作流(待审批数/驳回率)、消息(发送失败率)、预警(积压数)等")
    public String getMetrics() {
        return runTool("getMetrics", "current", () -> {
            Map<String, Double> metrics = analysisService.getCurrentMetrics();
            StringBuilder sb = new StringBuilder("当前业务指标:\n");
            metrics.forEach((k, v) -> sb.append(String.format("  %s = %.2f\n", k, v)));
            return sb.toString();
        });
    }

    // ==================== 知识库工具 ====================

    @Tool("""
            搜索知识库中的文档内容，基于语义相似度匹配最相关的内容片段。
            用于回答用户关于平台操作手册、FAQ、业务规则等问题。
            """)
    public String searchKnowledge(String query) {
        return runTool("searchKnowledge", query, () -> {
            try {
                // 优先复用查询 embedding 缓存，避免重复调用 embedding 模型 API（语义查询减 API 调用）
                List<Double> queryEmbedding = embedWithCache(query);
                if (queryEmbedding.isEmpty()) {
                    return "知识库嵌入服务不可用，请检查LLM配置";
                }
                // 宽召回 + 重排序：向量检索多召回候选，再交由重排序模型精排
                int topK = aiProperties.getVector().getTopK();
                int candidateCount = topK * Math.max(1, aiProperties.getSearch().getRetrieveTopK());
                List<VectorDocument> candidates = vectorStore.search(
                        queryEmbedding, candidateCount, aiProperties.getVector().getSimilarityThreshold());
                if (candidates.isEmpty()) return "知识库中没有找到相关内容";
                List<VectorDocument> results = rerankService.rerank(query, candidates);
                if (results.size() > topK) {
                    results = new ArrayList<>(results.subList(0, topK));
                }
                StringBuilder sb = new StringBuilder("知识库检索结果:\n");
                for (VectorDocument doc : results) {
                    sb.append(String.format("- 来源: %s, 内容: %s\n  相似度: %.2f\n",
                            doc.getMetadata() != null ? doc.getMetadata().getOrDefault("title", "未知") : "未知",
                            truncateText(doc.getText(), 300),
                            doc.getScore()));
                }
                return sb.toString();
            } catch (Exception e) {
                log.error("知识库搜索失败", e);
                return "知识库搜索失败: " + e.getMessage();
            }
        });
    }

    @Tool("列出所有知识库及其文档数量")
    public String listKnowledgeBases() {
        return runTool("listKnowledgeBases", "list", () -> {
            try {
                List<String> kbs = jdbc.getJdbcTemplate()
                        .queryForList("SELECT DISTINCT kb_name FROM ai_knowledge_doc", String.class);
                if (kbs.isEmpty()) return "当前没有知识库";
                StringBuilder sb = new StringBuilder("知识库列表:\n");
                for (String kb : kbs) {
                    Long count = jdbc.getJdbcTemplate()
                            .queryForObject("SELECT COUNT(1) FROM ai_knowledge_doc WHERE kb_name = ?", Long.class, kb);
                    sb.append(String.format("  - %s (%d篇文档)\n", kb, count != null ? count : 0));
                }
                return sb.toString();
            } catch (Exception e) {
                return "获取知识库列表失败: " + e.getMessage();
            }
        });
    }

    // ==================== 本地搜索工具 ====================

    @Tool("使用本地Lucene搜索引擎检索已索引的知识库文档内容，支持标题和全文搜索")
    public String localSearch(String query) {
        return runTool("localSearch", query, () -> {
            try {
                var results = searchEngine.search(query, 10);
                if (results.isEmpty()) return "本地搜索无结果";
                StringBuilder sb = new StringBuilder("本地搜索结果:\n");
                for (var r : results) {
                    sb.append(String.format("- %s\n  %s\n  相关度: %.2f\n",
                            r.getTitle(), truncateText(r.getContent() != null ? r.getContent() : "", 200),
                            r.getScore()));
                }
                return sb.toString();
            } catch (Exception e) {
                return "搜索异常: " + e.getMessage();
            }
        });
    }

    // ==================== 审批/工作流工具 ====================

    @Tool("查询当前待审批的任务数量，检测是否存在审批积压")
    public String getPendingTasks() {
        return runTool("getPendingTasks", "pending", () -> {
            Long count = jdbc.getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM wf_task WHERE status = 'PENDING'", Long.class);
            long pending = count != null ? count : 0L;
            String status = pending > 50 ? "⚠️ 严重积压" : pending > 20 ? "⚠️ 需要关注" : "✅ 正常";
            return String.format("待审批任务数: %d (%s)", pending, status);
        });
    }

    // ==================== 视觉/文档解析工具 ====================

    @Tool("对图片或文档进行视觉分析/内容解析，提取关键信息")
    public String analyzeAttachment(String attachmentSummary) {
        return runTool("analyzeAttachment", attachmentSummary, () -> "附件分析结果:\n" + attachmentSummary);
    }

    // ==================== 私有方法 ====================

    private record CachedResult(String result, long expireAt) {
        CachedResult(String result) {
            this(result, System.currentTimeMillis() + IDEMPOTENT_TTL_MS);
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    /**
     * 由 {@link ChatAgentService} 在工具循环中调用，根据工具请求反射执行对应 {@code @Tool} 方法。
     *
     * <p>工具名默认等于方法名（@Tool 未指定 name 时），参数通过 Jackson 反序列化为方法入参类型。</p>
     *
     * @param request LangChain4j 工具执行请求
     * @return 工具执行结果文本
     */
    public String execute(ToolExecutionRequest request) {
        if (request == null || request.name() == null) {
            return "工具请求为空";
        }
        for (Method method : AgentTools.class.getMethods()) {
            Tool tool = method.getAnnotation(Tool.class);
            if (tool == null || !method.getName().equals(request.name())) {
                continue;
            }
            try {
                Class<?>[] paramTypes = method.getParameterTypes();
                Object arg = null;
                if (paramTypes.length == 1) {
                    arg = toolObjectMapper.readValue(request.arguments(), paramTypes[0]);
                }
                // 无参方法必须传空数组，传 null 会被解析成长度为 1 的参数列表导致异常
                Object result = paramTypes.length == 0
                        ? method.invoke(this)
                        : method.invoke(this, arg);
                return result != null ? result.toString() : "";
            } catch (Exception e) {
                log.error("[AgentTools] 工具 {} 反射执行失败, arguments={}", request.name(), request.arguments(), e);
                return "工具执行失败: " + e.getMessage();
            }
        }
        return "未找到工具: " + request.name();
    }

    private final ObjectMapper toolObjectMapper = new ObjectMapper();

    // ==================== 私有方法 ====================

    private String truncateText(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    /**
     * <p>查询文本的 embedding 语义缓存（LRU + 软上限）。</p>
     *
     * <p>Agent 的语义检索工具（如 {@code searchKnowledge}）对同一查询反复调用 embedding 模型 API，
     * 使用此缓存复用结果，减少不必要的模型服务调用。基于 {@link LinkedHashMap} 实现线程安全的 LRU 淘汰。</p>
     *
     * <p>内置命中/未命中计数，用于可观测语义缓存收益（命中率 = hit / (hit + miss)）。</p>
     */
    private static class QueryEmbeddingCache {
        private final int maxSize;
        private final Map<String, List<Double>> store;
        private long hitCount = 0;
        private long missCount = 0;
        private long totalRequests = 0;

        QueryEmbeddingCache(int maxSize) {
            this.maxSize = maxSize;
            this.store = new LinkedHashMap<String, List<Double>>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Double>> eldest) {
                    return size() > maxSize;
                }
            };
        }

        synchronized List<Double> get(String query) {
            return store.get(normalize(query));
        }

        synchronized void put(String query, List<Double> embedding) {
            store.put(normalize(query), embedding);
        }

        synchronized void recordHit() {
            hitCount++;
            totalRequests++;
        }

        synchronized void recordMiss() {
            missCount++;
            totalRequests++;
        }

        synchronized double hitRate() {
            return totalRequests == 0 ? 0.0 : (double) hitCount / totalRequests;
        }

        synchronized long getHitCount() {
            return hitCount;
        }

        synchronized long getMissCount() {
            return missCount;
        }

        synchronized long getTotalRequests() {
            return totalRequests;
        }

        synchronized int size() {
            return store.size();
        }

        private static String normalize(String query) {
            return query == null ? "" : query.trim().toLowerCase();
        }
    }
}
