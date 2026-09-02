package com.liang.xz.aiagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.aiagent.agent.cache.ResponseCacheManager;
import com.liang.xz.aiagent.agent.session.SessionManager;
import com.liang.xz.aiagent.agent.session.SessionManager.SessionContext;
import com.liang.xz.aiagent.agent.session.SessionManager.SessionLoadResult;
import com.liang.xz.aiagent.config.AiProperties;
import com.liang.xz.aiagent.entity.ChatMessage;
import com.liang.xz.aiagent.rag.RetrievalPipeline;
import com.liang.xz.aiagent.vector.VectorDocument;

import com.liang.xz.aiagent.filter.ContentFilterService;
import org.springframework.jdbc.core.JdbcTemplate;
import com.liang.xz.aiagent.llm.LlmClient;
import com.liang.xz.aiagent.llm.ProviderFailoverService;
import com.liang.xz.aiagent.agent.workflow.WorkflowOrchestrator;
import com.liang.xz.aiagent.llm.ProviderFailoverService.ProviderInfo;
import com.liang.xz.common.core.util.BeijingTimeUtil;
import com.liang.xz.aiagent.repository.AiInvokeLogRepository;
import com.liang.xz.aiagent.repository.ChatMemoryRepository;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p>AI 对话与智能体核心服务。</p>
 *
 * <p>职责说明：</p>
 * <ul>
 *   <li>处理普通对话与流式对话，支持会话管理与历史消息加载。</li>
 *   <li>集成模型路由、RAG 检索、工具调用与多供应商故障转移能力。</li>
 *   <li>对 AI 调用进行统一的日志记录与可观测数据采集。</li>
 *   <li>对接语义缓存，命中时直接返回缓存结果以减少模型调用。</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class ChatAgentService {

    private final AiProperties aiProperties;
    private final ChatMemoryRepository chatMemoryRepository;
    private final SessionManager sessionManager;
    private final ProviderFailoverService providerFailoverService;
    private final ContentFilterService contentFilterService;
    private final ModelRouter modelRouter;
    private final ResponseCacheManager responseCacheManager;
    private final AiInvokeLogRepository aiInvokeLogRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AgentTools agentTools;
    private final ToolRegistry toolRegistry;
    private final RetrievalPipeline retrievalPipeline;
    private final LlmClient llmClient;
    private final WorkflowOrchestrator workflowOrchestrator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatAgentService(AiProperties aiProperties,
                            ChatMemoryRepository chatMemoryRepository,
                            SessionManager sessionManager,
                            ProviderFailoverService providerFailoverService,
                            ContentFilterService contentFilterService,
                            ModelRouter modelRouter,
                            ResponseCacheManager responseCacheManager,
                            AiInvokeLogRepository aiInvokeLogRepository,
                            JdbcTemplate jdbcTemplate,
                            AgentTools agentTools,
                            ToolRegistry toolRegistry,
                            RetrievalPipeline retrievalPipeline,
                            LlmClient llmClient,
                            WorkflowOrchestrator workflowOrchestrator) {
        this.aiProperties = aiProperties;
        this.chatMemoryRepository = chatMemoryRepository;
        this.sessionManager = sessionManager;
        this.providerFailoverService = providerFailoverService;
        this.contentFilterService = contentFilterService;
        this.modelRouter = modelRouter;
        this.responseCacheManager = responseCacheManager;
        this.aiInvokeLogRepository = aiInvokeLogRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.agentTools = agentTools;
        this.toolRegistry = toolRegistry;
        this.retrievalPipeline = retrievalPipeline;
        this.llmClient = llmClient;
        this.workflowOrchestrator = workflowOrchestrator;
    }

    // ==================== 普通对话 ====================

    public ChatResponse chat(ChatRequest request) {
        long startMs = System.currentTimeMillis();
        String sessionId = null;
        ModelRouter.RouteResult route = null;
        try {
            validateAndFilter(request);
            sessionId = ensureSession(request);
            List<String> fileDigests = extractFileDigests(request.getAttachments());

            // 先尝试命中语义缓存，命中则直接返回缓存结果以减少模型调用
            var cacheLookup = responseCacheManager.lookup(request.getQuestion(), fileDigests);
            if (cacheLookup.isHit()) {
                String cachedAnswer = cacheLookup.getCachedResponse().getAnswer();
                route = modelRouter.route(request.getQuestion());
                ToolInvocationContext.start(sessionId, request.getQuestion());
                ChatResponseMeta meta = buildMeta(route, new AtomicReference<>(), Collections.emptyList(),
                        Collections.emptyList(), true);
                ChatResponse response = ChatResponse.builder()
                        .answer(cachedAnswer)
                        .cached(true)
                        .toolsUsed(meta.getToolsUsed())
                        .ragReferences(meta.getRagReferences())
                        .modelUsed(meta.getModelUsed())
                        .providerCode(meta.getProviderCode())
                        .providerName(meta.getProviderName())
                        .complexity(meta.getComplexity())
                        .build();
            saveMessages(sessionId, request, request.getQuestion(), cachedAnswer, response);
            writeInvokeLog(request, route, meta, new AtomicReference<>(), null,
                    cachedAnswer, (int) (System.currentTimeMillis() - startMs), true, null);
            log.info("[ChatAgentService] 命中语义缓存 questionHash={}", cacheLookup.getCacheKey());
            return response;

            }

            route = modelRouter.route(request.getQuestion());
            ToolInvocationContext.start(sessionId, request.getQuestion());
            AtomicReference<ProviderInfo> providerInfoRef = new AtomicReference<>();

            // 复杂问题优先走多 Agent 编排：由编排器拆分任务、调度专业 Agent 协作并汇总。
            // 编排返回 null 表示"不适用或失败"，此时无缝回退到原有的单 Agent 工具链路，
            // 保证编排能力是增强项而非依赖项——编排不可用时系统行为与改造前完全一致。
            WorkflowOrchestrator.OrchestrationResult orchestration =
                    tryOrchestrateWithResult(request.getQuestion(), sessionId);
            ToolExecutionResult toolResult;
            if (orchestration != null) {
                // 编排路径不经过 providerFailoverService，模型信息留空，
                // 由 buildMeta 依据 route 自行解析（见 buildMeta 的 resolveProviderInfo 回退逻辑）
                toolResult = new ToolExecutionResult(orchestration.answer(),
                        ToolInvocationContext.getUsedTools(), Collections.emptyList(),
                        null, null, null);
                log.info("[ChatAgentService] 采用多Agent编排结果 sessionId={} traceId={}",
                        sessionId, orchestration.traceId());
            } else {
                toolResult = generateWithTools(request, sessionId, route, providerInfoRef, false);
            }

            String answer = toolResult.getAnswer();
            if (answer == null) {
                answer = "";
            }
            int elapsedMs = (int) (System.currentTimeMillis() - startMs);

            // 未命中缓存，将本次回答写入语义缓存
            responseCacheManager.put(request.getQuestion(), answer, fileDigests);

            ChatResponseMeta meta = buildMeta(route, providerInfoRef, toolResult.getToolsUsed(),
                    toolResult.getRagReferences(), false);
            meta.applyOrchestration(orchestration);
            meta.setElapsedMs(System.currentTimeMillis() - startMs);
            ChatResponse response = ChatResponse.builder()
                    .answer(answer)
                    .cached(false)
                    .toolsUsed(meta.getToolsUsed())
                    .ragReferences(meta.getRagReferences())
                    .modelUsed(meta.getModelUsed())
                    .providerCode(meta.getProviderCode())
                    .providerName(meta.getProviderName())
                    .complexity(meta.getComplexity())
                    .orchestrated(meta.isOrchestrated())
                    .agentIds(meta.getAgentIds())
                    .traceId(meta.getTraceId())
                    .taskCount(meta.getTaskCount())
                    .build();

            saveMessages(sessionId, request, request.getQuestion(), answer, response);
            writeInvokeLog(request, route, meta, providerInfoRef,
                    null,
                    answer, elapsedMs, true, null);
            return response;
        } catch (Exception e) {
            int elapsedMs = (int) (System.currentTimeMillis() - startMs);
            ProviderInfo failedInfo = resolveProviderInfo(route, null);
            writeInvokeLog(request, route, null,
                    new AtomicReference<>(failedInfo),
                    null, null, elapsedMs, false, e.getMessage());
            String providerCode = failedInfo != null ? failedInfo.getProviderCode() : "N/A";
            String modelUsed = failedInfo != null ? failedInfo.getModelUsed() : "N/A";
            log.error("[AI调用失败] 时间={} sessionId={} provider={} model={} cost={}ms 失败原因={}",
                    BeijingTimeUtil.formatNow(), request != null ? request.getSessionId() : "N/A",
                    providerCode, modelUsed, elapsedMs, e.getMessage(), e);
            throw e;
        }
    }

    public ChatResponse quickAsk(String question) {
        ChatRequest request = ChatRequest.builder()
                .question(question)
                .useMemory(false)
                .build();
        return chat(request);
    }

    // ==================== 多 Agent 编排 ====================

    /**
     * 尝试用多 Agent 编排回答问题。
     *
     * <p><b>为什么需要开关与兜底：</b>编排链路会额外消耗多次 LLM 调用（规划 + 各子任务 + 汇总），
     * 成本与延迟都高于单链路。因此:</p>
     * <ul>
     *   <li>通过 {@code ai.orchestration.enabled} 提供总开关，便于线上快速关闭；</li>
     *   <li>所有异常在此收敛，绝不让编排失败影响主流程——这是"增强而非依赖"原则的落点。</li>
     * </ul>
     *
     * @param question  用户问题
     * @param sessionId 会话ID，用于把编排日志关联回具体对话
     * @return 编排结果（含答案与元数据）；不适用或失败返回 null，由调用方回退单链路
     */
    private WorkflowOrchestrator.OrchestrationResult tryOrchestrateWithResult(
            String question, String sessionId) {
        if (workflowOrchestrator == null || !isOrchestrationEnabled()) {
            return null;
        }
        try {
            return workflowOrchestrator.orchestrate(question, sessionId);
        } catch (Exception e) {
            log.warn("[ChatAgentService] 多Agent编排异常，已回退单链路: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 编排总开关，默认开启；线上出现问题时可设 {@code ai.orchestration.enabled=false} 即时关闭。
     */
    private boolean isOrchestrationEnabled() {
        AiProperties.OrchestrationConfig orchestration = aiProperties.getOrchestration();
        return orchestration == null || orchestration.isEnabled();
    }

    // ==================== 流式对话 ====================

    public void streamChat(ChatRequest request, StreamCallback callback) {
        long startMs = System.currentTimeMillis();
        ModelRouter.RouteResult route = null;
        try {
            validateAndFilter(request);
            String sessionId = ensureSession(request);
            List<String> fileDigests = extractFileDigests(request.getAttachments());
            route = modelRouter.route(request.getQuestion());

            // 流式对话优先命中语义缓存，命中后直接通过 onToken 回传，不再调用 LLM
                var cacheLookup = responseCacheManager.lookup(request.getQuestion(), fileDigests);
                if (cacheLookup.isHit()) {
                    String cachedAnswer = cacheLookup.getCachedResponse().getAnswer();
                    ToolInvocationContext.start(sessionId, request.getQuestion());
                    callback.onToken(cachedAnswer);
                    ChatResponseMeta meta = buildMeta(route, new AtomicReference<>(), Collections.emptyList(),
                            Collections.emptyList(), true);
                    callback.onMeta(meta);
                    callback.onComplete(Response.from(AiMessage.from(cachedAnswer)));

                    ChatResponse resp = ChatResponse.builder()
                            .answer(cachedAnswer)
                            .cached(true)
                            .toolsUsed(meta.getToolsUsed())
                            .ragReferences(meta.getRagReferences())
                            .modelUsed(meta.getModelUsed())
                            .providerCode(meta.getProviderCode())
                            .providerName(meta.getProviderName())
                            .complexity(meta.getComplexity())
                            .build();
                saveMessages(sessionId, request, request.getQuestion(), cachedAnswer, resp);
                log.info("[ChatAgentService] 命中语义缓存 questionHash={}", cacheLookup.getCacheKey());
                writeInvokeLog(request, route, meta, new AtomicReference<>(), null,
                        cachedAnswer, (int) (System.currentTimeMillis() - startMs), true, null);
                return;
            }

            ToolInvocationContext.start(sessionId, request.getQuestion());
            AtomicReference<ProviderInfo> providerInfoRef = new AtomicReference<>();

            // 与非流式链路保持一致：复杂问题优先走多 Agent 编排。
            // 本方法本就是"先完整生成再整体吐出"的伪流式，因此接入编排不会额外增加等待感。
            WorkflowOrchestrator.OrchestrationResult orchestration =
                    tryOrchestrateWithResult(request.getQuestion(), sessionId);
            ToolExecutionResult toolResult;
            if (orchestration != null) {
                toolResult = new ToolExecutionResult(orchestration.answer(),
                        ToolInvocationContext.getUsedTools(), Collections.emptyList(),
                        null, null, null);
                log.info("[ChatAgentService] 流式链路采用多Agent编排结果 sessionId={} traceId={}",
                        sessionId, orchestration.traceId());
            } else {
                toolResult = generateWithTools(request, sessionId, route, providerInfoRef, false);
            }
            String finalAnswer = toolResult.getAnswer();
            if (finalAnswer == null) {
                finalAnswer = "";
            }

            StringBuilder answerBuilder = new StringBuilder(finalAnswer);
            callback.onToken(finalAnswer);

            ChatResponseMeta meta = buildMeta(route, providerInfoRef, toolResult.getToolsUsed(),
                    toolResult.getRagReferences(), false);
            meta.applyOrchestration(orchestration);
            meta.setElapsedMs(System.currentTimeMillis() - startMs);
            callback.onMeta(meta);
            callback.onComplete(Response.from(AiMessage.from(finalAnswer)));

            ChatResponse resp = ChatResponse.builder()
                    .answer(finalAnswer)
                    .cached(false)
                    .toolsUsed(meta.getToolsUsed())
                    .ragReferences(meta.getRagReferences())
                    .modelUsed(meta.getModelUsed())
                    .providerCode(meta.getProviderCode())
                    .providerName(meta.getProviderName())
                    .complexity(meta.getComplexity())
                    .build();
            saveMessages(sessionId, request, request.getQuestion(), finalAnswer, resp);
            responseCacheManager.put(request.getQuestion(), finalAnswer, fileDigests);
            writeInvokeLog(request, route, meta, providerInfoRef,
                    null,
                    finalAnswer, (int) (System.currentTimeMillis() - startMs), true, null);
        } catch (Exception e) {
            int elapsedMs = (int) (System.currentTimeMillis() - startMs);
            ProviderInfo failedInfo = resolveProviderInfo(route, null);
            writeInvokeLog(request, route, null, new AtomicReference<>(failedInfo), null, null,
                    elapsedMs, false, e.getMessage());
            String providerCode = failedInfo != null ? failedInfo.getProviderCode() : "N/A";
            String modelUsed = failedInfo != null ? failedInfo.getModelUsed() : "N/A";
            log.error("[AI调用失败] 时间={} sessionId={} provider={} model={} cost={}ms 失败原因={}",
                    BeijingTimeUtil.formatNow(), request != null ? request.getSessionId() : "N/A",
                    providerCode, modelUsed, elapsedMs, e.getMessage(), e);
            callback.onError(e);
        }
    }

    /**
     * 写入调用日志
     */
    private void writeInvokeLog(ChatRequest request,
                                ModelRouter.RouteResult route,
                                ChatResponseMeta meta,
                                AtomicReference<ProviderInfo> providerInfoRef,
                                TokenUsage tokenUsage,
                                String aiOutput,
                                int elapsedMs,
                                boolean success,
                                String errorMsg) {
        try {
            AiInvokeLogRepository.AiInvokeLogParams params = new AiInvokeLogRepository.AiInvokeLogParams();
            params.sessionId = request != null ? request.getSessionId() : null;
            params.userId = request != null ? request.getUserId() : null;
            params.complexity = route != null ? route.getComplexity() : null;
            params.userInput = truncate(request != null ? request.getQuestion() : null, 8000);
            params.aiOutput = truncate(aiOutput, 8000);
            params.elapsedMs = elapsedMs;
            params.success = success;
            params.errorMsg = truncate(errorMsg, 500);
            params.invokeTime = BeijingTimeUtil.now();

            ProviderInfo info = providerInfoRef != null ? providerInfoRef.get() : null;
            if (meta != null) {
                params.providerCode = meta.getProviderCode();
                params.providerName = meta.getProviderName();
                params.modelName = meta.getModelUsed();
                params.toolNames = toJsonArray(meta.getToolsUsed());
                params.toolCount = meta.getToolsUsed() != null ? meta.getToolsUsed().size() : 0;
            }
            if (info != null) {
                if (params.providerCode == null) {
                    params.providerCode = info.getProviderCode();
                }
                if (params.providerName == null) {
                    params.providerName = info.getProviderName();
                }
                if (params.providerType == null) {
                    params.providerType = info.getProviderType();
                }
                if (params.modelName == null) {
                    params.modelName = info.getModelUsed();
                }
            }
            if (!hasText(params.providerCode) || !hasText(params.modelName)) {
                ProviderInfo fallback = providerFailoverService.resolveProviderInfo(
                        route != null ? route.getProvider() : null,
                        route != null ? route.getModel() : null);
                if (fallback != null) {
                    if (!hasText(params.providerCode)) {
                        params.providerCode = fallback.getProviderCode();
                    }
                    if (!hasText(params.providerName)) {
                        params.providerName = fallback.getProviderName();
                    }
                    if (!hasText(params.providerType)) {
                        params.providerType = fallback.getProviderType();
                    }
                    if (!hasText(params.modelName)) {
                        params.modelName = fallback.getModelUsed();
                    }
                }
            }
            if (meta != null) {
                params.cached = meta.isCached();
            }
            if (tokenUsage != null) {
                params.promptTokens = tokenUsage.inputTokenCount();
                params.completionTokens = tokenUsage.outputTokenCount();
                params.totalTokens = tokenUsage.totalTokenCount();
            }
            aiInvokeLogRepository.insert(params);
        } catch (Exception e) {
            log.error("[ChatAgentService] 写入 AI 调用记录失败: {}", e.getMessage(), e);
        }
    }

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }

    private String toJsonArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 会话管理 ====================

    public SessionContext createSession(String userId, String title) {
        String sessionId = java.util.UUID.randomUUID().toString().substring(0, 8);
        return sessionManager.getOrCreateSession(sessionId, userId, title);
    }

    public List<SessionContext> listSessions(String userId) {
        return sessionManager.listUserSessions(userId);
    }

    public SessionContext switchSession(String sessionId, String userId) {
        return sessionManager.switchSession(sessionId, userId);
    }

    public SessionLoadResult loadSessionHistory(String sessionId) {
        return sessionManager.loadSession(sessionId);
    }

    public void clearSession(String sessionId) {
        sessionManager.deleteSession(sessionId);
    }

    /**
     * 删除会话中指定下标的消息，删除后重建该会话的消息列表。下标从 0 开始。
     */
    public boolean deleteMessage(String sessionId, int index) {
        List<ChatMessage> messages = chatMemoryRepository.findMessagesBySession(sessionId);
        if (index < 0 || index >= messages.size()) {
            return false;
        }
        messages.remove(index);
        chatMemoryRepository.deleteMessagesBySession(sessionId);
        for (ChatMessage msg : messages) {
            chatMemoryRepository.saveMessage(msg);
        }
        return true;
    }

    public Map<String, Object> getCacheStats() {
        return responseCacheManager.statsMap();
    }

    public int getActiveSessionCount() {
        return sessionManager.getActiveSessionCount();
    }

    // ==================== 请求/响应 DTO ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatRequest {
        private String sessionId;
        private String userId;
        private String question;
        @Builder.Default
        private boolean useMemory = true;
        @Builder.Default
        private boolean thinking = true;
        private List<ChatAttachment> attachments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatAttachment {
        private String type;
        private String name;
        private String mimeType;
        private String url;
        private byte[] bytes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatResponse {
        private String answer;
        private boolean cached;
        private List<String> toolsUsed;
        private List<RagReference> ragReferences;
        private String modelUsed;
        private String providerCode;
        private String providerName;
        private String complexity;

        /** 是否由多 Agent 编排产出 */
        private boolean orchestrated;
        /** 参与协作的 Agent 标识列表 */
        private List<String> agentIds;
        /** 编排链路追踪ID，可用于查询子Agent调用明细 */
        private String traceId;
        /** 子任务总数 */
        private int taskCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatResponseMeta {
        private boolean cached;
        private List<String> toolsUsed;
        private List<RagReference> ragReferences;
        private String modelUsed;
        private String providerCode;
        private String providerName;
        private String complexity;

        /** 是否由多 Agent 编排产出（true 表示本次回答由多个 Agent 协作完成） */
        private boolean orchestrated;
        /** 参与协作的 Agent 标识列表 */
        private List<String> agentIds;
        /** 编排链路追踪ID，可用于查询每个子Agent的调用明细 */
        private String traceId;
        /** 子任务总数 */
        private int taskCount;
        /** 执行层级数，反映任务图深度 */
        private int layerCount;
        /** 编排整体耗时毫秒 */
        private long orchestrationElapsedMs;
        /** 整体耗时毫秒 */
        private long elapsedMs;

        /**
         * 用编排结果填充元数据。
         */
        public void applyOrchestration(WorkflowOrchestrator.OrchestrationResult result) {
            if (result == null) {
                return;
            }
            this.orchestrated = true;
            this.agentIds = result.agentIds();
            this.traceId = result.traceId();
            this.taskCount = result.taskCount();
            this.layerCount = result.layerCount();
            this.orchestrationElapsedMs = result.elapsedMs();
        }
    }

    /**
     * RAG 检索引用的来源信息，包含文档来源、标题、片段内容与相似度得分。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagReference {
        private String source;
        private String title;
        private String content;
        private Double score;
        private String sourceType;
    }

    public interface StreamCallback {
        void onToken(String token);
        void onComplete(Response<AiMessage> response);
        void onError(Throwable error);
        default void onMeta(ChatResponseMeta meta) {}
    }

    // ==================== 输入校验与预处理 ====================

    private void validateAndFilter(ChatRequest request) {
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new IllegalArgumentException("问题内容不能为空");
        }
        if (!contentFilterService.isEnabled()) {
            return;
        }
        Optional<String> reject = contentFilterService.check(request.getQuestion());
        if (reject.isPresent()) {
            throw new IllegalArgumentException(reject.get());
        }
    }

    private String ensureSession(ChatRequest request) {
        if (request.getSessionId() != null && !request.getSessionId().isBlank()) {
            sessionManager.getOrCreateSession(request.getSessionId(), request.getUserId(), null);
            return request.getSessionId();
        }
        String sessionId = java.util.UUID.randomUUID().toString().substring(0, 8);
        sessionManager.getOrCreateSession(sessionId, request.getUserId(), null);
        request.setSessionId(sessionId);
        return sessionId;
    }

    private List<String> extractFileDigests(List<ChatAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> digests = new ArrayList<>();
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            log.warn("初始化 SHA-256 摘要算法失败，文件指纹将无法参与缓存计算", e);
            return Collections.emptyList();
        }
        for (ChatAttachment att : attachments) {
            if (att.getBytes() != null && att.getBytes().length > 0) {
                byte[] hash = md.digest(att.getBytes());
                digests.add(HexFormat.of().formatHex(hash));
                md.reset();
            }
        }
        return digests;
    }

    private List<dev.langchain4j.data.message.ChatMessage> buildMessages(ChatRequest request, String sessionId,
                                                                       String ragContext, boolean enableTools) {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        messages.add(buildSystemMessage(enableTools));
        if (request.isUseMemory()) {
            messages.addAll(loadHistoryMessages(sessionId));
        }

        if (ragContext != null && !ragContext.isBlank()) {
            messages.add(SystemMessage.from(
                    "以下是检索到的参考知识库内容，请优先依据这些内容回答用户问题：\n" + ragContext));
        }

        UserMessage userMessage = buildUserMessage(request);
        messages.add(userMessage);
        return messages;
    }

    private SystemMessage buildSystemMessage(boolean enableTools) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 auth-platform 智能助手，负责用中文回答用户问题。");
        if (enableTools) {
            prompt.append("当用户询问以下业务数据时，你必须调用对应工具获取实时数据，禁止直接回答无法访问或工具不可用：")
                    .append("1) 待审批任务、审批事项、我的待办 → 调用 getPendingTasks；")
                    .append("2) 知识库列表、有哪些知识库 → 调用 listKnowledgeBases；")
                    .append("3) 其他需要查询项目内部系统才能回答的问题，优先尝试调用可用工具。")
                    .append("可用的工具及调用格式如下（请使用 function calling 协议调用）：\n")
                    .append("- listKnowledgeBases(): 列出所有知识库及其文档数量\n")
                    .append("- getPendingTasks(): 查询当前待审批的任务数量\n")
                    .append("- getMetrics(): 获取当前关键业务指标\n")
                    .append("- getAlerts(): 获取当前所有的分析预警记录\n")
                    .append("- getAlertSummary(): 获取预警汇总统计\n")
                    .append("- searchKnowledge(query): 基于语义搜索知识库内容\n")
                    .append("- localSearch(query): 使用本地Lucene搜索引擎检索知识库\n")
                    .append("- queryDatabase(sql): 执行安全的数据统计查询\n")
                    .append("- getDatabaseMetadata(): 获取数据库可用表的元数据信息\n")
                    .append("- triggerAnalysis(): 触发一次实时的业务数据分析预警\n")
                    .append("- analyzeAttachment(attachmentSummary): 对图片或文档进行视觉分析\n");
        }
        prompt.append("回答应简洁、准确。");
        return SystemMessage.from(prompt.toString());
    }

    /**
     * 判断用户问题是否命中工具调用意图，用于首轮模型未触发工具时强制补刀。
     */
    private boolean isToolIntentQuestion(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String q = question.toLowerCase();
        return q.contains("待审批") || q.contains("审批") || q.contains("待办") || q.contains("待我审批")
                || q.contains("知识库") || q.contains("有哪些知识库") || q.contains("知识库列表")
                || q.contains("pending") || q.contains("knowledge");
    }

    /**
     * 判断模型回答是否声称当前没有可用工具/无法获取实时数据。
     */
    private boolean isNoToolAvailableAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String a = answer.toLowerCase();
        return a.contains("未提供可用工具")
                || a.contains("没有可用工具")
                || a.contains("无法获取实时")
                || a.contains("无法访问")
                || a.contains("工具不可用");
    }

    /**
     * 根据问题意图选择兜底工具名。
     */
    private String resolveFallbackTool(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }
        String q = question.toLowerCase();
        if (q.contains("知识库") || q.contains("有哪些知识库") || q.contains("知识库列表")
                || q.contains("knowledge base")) {
            return "listKnowledgeBases";
        }
        if (q.contains("待审批") || q.contains("审批") || q.contains("待办") || q.contains("pending")) {
            return "getPendingTasks";
        }
        if (q.contains("预警") || q.contains("alert")) {
            return "getAlerts";
        }
        if (q.contains("指标") || q.contains("统计") || q.contains("metrics")) {
            return "getMetrics";
        }
        return null;
    }

    private UserMessage buildUserMessage(ChatRequest request) {
        String question = request.getQuestion();
        List<ChatAttachment> attachments = request.getAttachments();

        if (attachments == null || attachments.isEmpty()) {
            return UserMessage.from(question);
        }

        StringBuilder imageDescBuilder = new StringBuilder();
        boolean hasImage = false;
        for (ChatAttachment att : attachments) {
            if ("image".equals(att.getType()) && att.getBytes() != null && att.getBytes().length > 0) {
                hasImage = true;
                try {
                    String description = llmClient.describeImage(att.getBytes(), att.getMimeType());
                    if (description != null && !description.isBlank() && !description.startsWith("[LLM")) {
                        imageDescBuilder.append("\n[图片 ").append(att.getName() == null ? "附件" : att.getName())
                                .append(" 内容描述] ").append(description);
                    } else {
                        imageDescBuilder.append("\n[图片 ").append(att.getName() == null ? "附件" : att.getName())
                                .append("] (视觉模型分析失败，无法读取图片内容)");
                    }
                } catch (Exception e) {
                    log.warn("[ChatAgentService] 图片视觉分析失败: {}", e.getMessage());
                    imageDescBuilder.append("\n[图片 ").append(att.getName() == null ? "附件" : att.getName())
                            .append("] (视觉模型调用异常，无法读取图片内容)");
                }
            }
        }

        if (!hasImage) {
            return UserMessage.from(question);
        }

        String finalText = question + imageDescBuilder;
        return UserMessage.from(finalText);
    }

    private List<dev.langchain4j.data.message.ChatMessage> loadHistoryMessages(String sessionId) {
        List<ChatMessage> entities = chatMemoryRepository.findMessagesBySession(sessionId);
        List<dev.langchain4j.data.message.ChatMessage> result = new ArrayList<>();
        for (ChatMessage entity : entities) {
            dev.langchain4j.data.message.ChatMessage msg = toLangChainMessage(entity);
            if (msg != null) {
                result.add(msg);
            }
        }
        return result;
    }

    private dev.langchain4j.data.message.ChatMessage toLangChainMessage(ChatMessage entity) {
        if (entity == null || entity.getRole() == null) {
            return null;
        }
        return switch (entity.getRole().toUpperCase()) {
            case "USER" -> UserMessage.from(entity.getContent());
            case "ASSISTANT" -> AiMessage.from(entity.getContent());
            case "SYSTEM" -> dev.langchain4j.data.message.SystemMessage.from(entity.getContent());
            default -> null;
        };
    }

    private ChatResponseMeta buildMeta(ModelRouter.RouteResult route,
                                       AtomicReference<ProviderInfo> providerInfoRef,
                                       List<String> toolsUsed,
                                       List<RagReference> ragReferences,
                                       boolean cached) {
        ProviderInfo info = resolveProviderInfo(route, providerInfoRef);
        return ChatResponseMeta.builder()
                .cached(cached)
                .toolsUsed(toolsUsed != null ? toolsUsed : Collections.emptyList())
                .ragReferences(ragReferences != null ? ragReferences : Collections.emptyList())
                .modelUsed(info != null ? info.getModelUsed() : route.getModel())
                .providerCode(info != null ? info.getProviderCode() : null)
                .providerName(info != null ? info.getProviderName() : null)
                .complexity(route.getComplexity())
                .build();
    }

    /**
     * 解决提供者信息，优先使用工具调用阶段已确定的提供者。
     *
     * <p>若 {@code providerInfoRef} 中已存在有效提供者则直接返回；否则，
     * 回退到 {@link ProviderFailoverService} 根据路由结果解析提供者信息。</p>
     */
    private ProviderInfo resolveProviderInfo(ModelRouter.RouteResult route,
                                             AtomicReference<ProviderInfo> providerInfoRef) {
        ProviderInfo fromRef = providerInfoRef != null ? providerInfoRef.get() : null;
        if (fromRef != null && hasText(fromRef.getProviderCode())) {
            return fromRef;
        }
        String model = route != null ? route.getModel() : null;
        String providerCode = route != null ? route.getProvider() : null;
        ProviderInfo fallback = providerFailoverService.resolveProviderInfo(providerCode, model);
        if (fallback != null && hasText(fallback.getProviderCode())) {
            if (providerInfoRef != null) {
                providerInfoRef.set(fallback);
            }
            return fallback;
        }
        return fromRef;
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * 工具执行结果，承载模型最终回答、已调用工具列表与 RAG 引用信息。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ToolExecutionResult {
        private String answer;
        private List<String> toolsUsed;
        private List<RagReference> ragReferences;
        private String modelUsed;
        private String providerCode;
        private String providerName;
    }

    /**
     *
     * @param request 请求参数
     * @param sessionId 会话ID
     * @param route 路由结果
     * @param providerInfoRef 提供者信息引用
     * @param skipTools 是否跳过工具
     * @return 工具执行结果
     */
    private ToolExecutionResult generateWithTools(ChatRequest request, String sessionId,
                                                  ModelRouter.RouteResult route,
                                                  AtomicReference<ProviderInfo> providerInfoRef,
                                                  boolean skipTools) {
        List<RagReference> ragReferences = Collections.emptyList();
        if (!skipTools) {
            try {
                ragReferences = doRetrieve(request.getQuestion());
            } catch (Exception e) {
                log.warn("[ChatAgentService] RAG 检索失败，将跳过检索上下文继续使用模型应答: {}", e.getMessage());
            }
        }

        List<ProviderFailoverService.ChatModelWithTools> models = providerFailoverService
                .buildAllChatModelsWithTools(route.getProvider(), route.getModel(),
                        route.getTemperature(), route.getMaxTokens(),
                        skipTools ? Collections.emptyList() : toolRegistry.allToolSpecifications());

        ProviderFailoverService.ChatModelWithTools firstModel = models.get(0);
        updateProviderInfoRef(providerInfoRef, firstModel);
        if (log.isDebugEnabled()) {
            List<ToolSpecification> specs = firstModel.getToolSpecifications();
            log.debug("[ChatAgentService] 工具规格数量={}, 名称={}",
                    specs != null ? specs.size() : 0,
                    specs != null ? specs.stream().map(ToolSpecification::name).toList() : Collections.emptyList());
        }

        List<dev.langchain4j.data.message.ChatMessage> messages =
                buildMessages(request, sessionId, skipTools ? null : ragContextText(ragReferences), !skipTools);

        int maxLoops = skipTools ? 1 : MAX_TOOL_LOOPS;
        AiMessage aiMessage = null;
        boolean forcedToolCall = false;
        for (int loop = 0; loop < maxLoops; loop++) {
            Response<AiMessage> response = generateWithRetry(models, messages, providerInfoRef);
            aiMessage = response.content();
            List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();
            if (toolRequests == null || toolRequests.isEmpty()) {
                // 首轮未触发工具但问题命中工具意图时，强制注入指令再尝试一次，避免模型"装不知道"
                if (!forcedToolCall && !skipTools && loop == 0 && isToolIntentQuestion(request.getQuestion())) {
                    forcedToolCall = true;
                    messages.add(SystemMessage.from(
                            "你刚才没有调用任何工具。检测到用户问题需要实时业务数据，"
                                    + "你必须调用 getPendingTasks 或 listKnowledgeBases 中的一个来获取数据，"
                                    + "禁止直接回答无法访问。请立即重新生成并包含工具调用。"));
                    continue;
                }
                break;
            }
            // 执行工具调用并回灌结果，继续下一轮模型决策
            for (ToolExecutionRequest toolRequest : toolRequests) {
                String toolResult = executeTool(toolRequest);
                messages.add(aiMessage);
                messages.add(ToolExecutionResultMessage.from(toolRequest.id(), toolRequest.name(), toolResult));
            }
        }

        // 兜底：模型声称没有可用工具/无法获取实时数据时，根据问题意图直接手动调用一次工具
        if (!skipTools && aiMessage != null
                && (aiMessage.toolExecutionRequests() == null || aiMessage.toolExecutionRequests().isEmpty())
                && isNoToolAvailableAnswer(aiMessage.text())
                && isToolIntentQuestion(request.getQuestion())) {
            String fallbackTool = resolveFallbackTool(request.getQuestion());
            if (fallbackTool != null) {
                log.info("[ChatAgentService] 模型未产生工具调用但回答声称无可用工具，fallback 手动执行: {}", fallbackTool);
                String toolCallId = java.util.UUID.randomUUID().toString();
                String toolResult = executeTool(ToolExecutionRequest.builder()
                        .id(toolCallId)
                        .name(fallbackTool)
                        .arguments("{}")
                        .build());
                messages.add(aiMessage);
                messages.add(ToolExecutionResultMessage.from(toolCallId, fallbackTool, toolResult));
                Response<AiMessage> finalResponse = generateWithRetry(models, messages, providerInfoRef);
                aiMessage = finalResponse.content();
            }
        }

        ProviderFailoverService.ChatModelWithTools usedModel = providerInfoRef != null
                ? findModelByProviderCode(models, providerInfoRef.get())
                : firstModel;
        if (usedModel == null) {
            usedModel = firstModel;
        }
        String answer = aiMessage != null ? aiMessage.text() : "";
        List<String> usedTools = ToolInvocationContext.getUsedTools();
        return ToolExecutionResult.builder()
                .answer(answer)
                .toolsUsed(usedTools)
                .ragReferences(ragReferences)
                .modelUsed(usedModel.getModelUsed())
                .providerCode(usedModel.getProviderCode())
                .providerName(usedModel.getProviderName())
                .build();
    }

    private static void updateProviderInfoRef(AtomicReference<ProviderInfo> ref,
                                              ProviderFailoverService.ChatModelWithTools model) {
        if (ref == null) {
            return;
        }
        ref.set(ProviderInfo.builder()
                .providerCode(model.getProviderCode())
                .providerName(model.getProviderName())
                .providerType(model.getProviderType())
                .modelUsed(model.getModelUsed())
                .build());
    }

    private static ProviderFailoverService.ChatModelWithTools findModelByProviderCode(
            List<ProviderFailoverService.ChatModelWithTools> models,
            ProviderInfo info) {
        if (info == null || models == null || models.isEmpty()) {
            return null;
        }
        return models.stream()
                .filter(m -> m.getProviderCode() != null
                        && m.getProviderCode().equals(info.getProviderCode()))
                .findFirst()
                .orElse(models.get(0));
    }

    private static final int MAX_TOOL_LOOPS = 5;
    private static final int MAX_LLM_RETRIES_PER_PROVIDER = 2;
    private static final long LLM_RETRY_BASE_DELAY_MS = 500L;

    /**
     * 带跨供应商切换的重试调用模型。
     *
     * <p>对同一供应商先重试 {@link #MAX_LLM_RETRIES_PER_PROVIDER} 次；若仍失败且异常属于
     * 可重试异常（如 503/502/504、超时、连接异常），则切换到下一个供应商继续尝试。
     * 所有供应商均失败时抛出最后一次异常。</p>
     */
    private Response<AiMessage> generateWithRetry(
            List<ProviderFailoverService.ChatModelWithTools> models,
            List<dev.langchain4j.data.message.ChatMessage> messages,
            AtomicReference<ProviderInfo> providerInfoRef) {
        Exception lastException = null;
        for (int providerIndex = 0; providerIndex < models.size(); providerIndex++) {
            ProviderFailoverService.ChatModelWithTools modelWithTools = models.get(providerIndex);
            updateProviderInfoRef(providerInfoRef, modelWithTools);
            String providerCode = modelWithTools.getProviderCode();
            String modelName = modelWithTools.getModelUsed();
            // 模型不存在/不支持是"该模型"的永久性配置错误：同一模型只试一次，立即切下一个候选模型/供应商，
            // 避免对坏模型空转重试；其余可重试异常（503/超时/连接）才在同一模型内退避重试。
            int maxAttempt = isModelMissingException(lastException) ? 0 : MAX_LLM_RETRIES_PER_PROVIDER;
            for (int attempt = 0; attempt <= maxAttempt; attempt++) {
                try {
                    log.debug("[ChatAgentService] 调用模型 provider={} model={} attempt={}",
                            providerCode, modelName, attempt + 1);
                    return modelWithTools.getChatModel().generate(messages, modelWithTools.getToolSpecifications());
                } catch (Exception e) {
                    lastException = e;
                    boolean modelMissing = isModelMissingException(e);
                    if (modelMissing) {
                        // 模型不存在：不重试该模型，直接跳出到下一个供应商/模型
                        log.warn("[ChatAgentService] 供应商 {} 模型 {} 不存在或不可用，跳过该模型: {}",
                                providerCode, modelName, e.getMessage());
                        break;
                    }
                    boolean retryable = isRetryableException(e);
                    if (attempt == MAX_LLM_RETRIES_PER_PROVIDER || !retryable) {
                        log.warn("[ChatAgentService] 供应商 {} 模型 {} 第 {} 次调用失败，类型={}, 原因={}",
                                providerCode, modelName, attempt + 1, e.getClass().getSimpleName(), e.getMessage());
                        break;
                    }
                    long delay = LLM_RETRY_BASE_DELAY_MS * (attempt + 1);
                    log.warn("[ChatAgentService] 供应商 {} 模型 {} 第 {} 次调用失败，{}ms 后重试，原因={}",
                            providerCode, modelName, attempt + 1, delay, e.getMessage());
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(ie);
                    }
                }
            }
            if (providerIndex < models.size() - 1) {
                log.warn("[ChatAgentService] 供应商 {} 模型 {} 调用失败，切换到下一个候选模型/供应商重试",
                        providerCode, modelName);
            }
        }
        log.error("[ChatAgentService] 所有候选模型/供应商均调用失败，最后一个异常: {}",
                lastException != null ? lastException.getMessage() : "unknown");
        if (lastException instanceof RuntimeException) {
            throw (RuntimeException) lastException;
        }
        throw new RuntimeException(lastException);
    }

    /**
     * 判断异常是否为"模型不存在/不可用"类错误。
     * 这类错误属于该模型本身的永久性配置问题，重试同一个模型无意义，应直接切换到下一个候选模型/供应商。
     */
    private boolean isModelMissingException(Exception e) {
        if (e == null) {
            return false;
        }
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return msg.contains("modelcode")
                || msg.contains("model not found")
                || msg.contains("model does not exist")
                || msg.contains("model not exist")
                || msg.contains("invalid model")
                || msg.contains("model unavailable")
                || msg.contains("unknown model");
    }

    private boolean isRetryableException(Exception e) {
        if (e == null) {
            return false;
        }
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        String clazz = e.getClass().getSimpleName().toLowerCase();
        return clazz.contains("openaihttpexception")
                || clazz.contains("http")
                || clazz.contains("timeout")
                || clazz.contains("connect")
                || clazz.contains("socket")
                || clazz.contains("retry")
                || msg.contains("upstream service temporarily unavailable")
                || msg.contains("temporarily unavailable")
                || msg.contains("503")
                || msg.contains("502")
                || msg.contains("504")
                || msg.contains("timeout")
                || msg.contains("connection");
    }

    /**
     * 执行单个工具调用请求，返回工具结果文本。
     */
    private String executeTool(ToolExecutionRequest request) {
        try {
            // 委托 ToolRegistry：遍历所有带 @Tool 注解的工具类实例反射执行,
            // 模型名定位到具体工具类, 不在 ChatAgentService 中重复维护工具实现。
            return toolRegistry.execute(request);
        } catch (Exception e) {
            log.error("[ChatAgentService] 执行工具 {} 失败", request.name(), e);
            return "工具执行异常: " + e.getMessage();
        }
    }

    /**
     * 执行 RAG 检索并将命中的向量文档转换为供模型消费的 RagReference 列表。
     */
    private List<RagReference> doRetrieve(String question) {
        int topK = aiProperties.getVector() != null ? aiProperties.getVector().getTopK() : 5;
        List<VectorDocument> docs = retrievalPipeline.retrieve(question, topK);
        if (docs == null || docs.isEmpty()) {
            return Collections.emptyList();
        }
        List<RagReference> refs = new ArrayList<>();
        for (VectorDocument doc : docs) {
            Map<String, Object> meta = doc.getMetadata();
            String title = meta != null ? String.valueOf(meta.getOrDefault("title", "")) : "";
            String source = meta != null ? String.valueOf(meta.getOrDefault("source", "")) : "";
            String kbName = meta != null ? String.valueOf(meta.getOrDefault("kbName", "")) : "";
            refs.add(RagReference.builder()
                    .source(source)
                    .title(title)
                    .content(doc.getText() != null ? doc.getText() : "")
                    .score(doc.getScore())
                    .sourceType(kbName)
                    .build());
        }
        return refs;
    }

    /**
     * 将 RAG 检索得到的 RagReference 列表拼接为可注入系统提示的参考文本。
     */
    private String ragContextText(List<RagReference> references) {
        if (references == null || references.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < references.size(); i++) {
            RagReference ref = references.get(i);
            sb.append(String.format("\n[参考 %d] 来源: %s", i + 1, ref.getSource()));
            if (ref.getTitle() != null && !ref.getTitle().isBlank()) {
                sb.append(" 标题: ").append(ref.getTitle());
            }
            if (ref.getContent() != null && !ref.getContent().isBlank()) {
                sb.append("\n内容: ").append(ref.getContent());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private void saveMessages(String sessionId, ChatRequest request, String question, String answer,
                               ChatResponse meta) {
        String userId = request != null ? request.getUserId() : null;
        String userName = resolveUserName(userId);
        String attachmentsJson = null;
        if (request != null && request.getAttachments() != null && !request.getAttachments().isEmpty()) {
            try {
                attachmentsJson = objectMapper.writeValueAsString(request.getAttachments());
            } catch (Exception e) {
                log.warn("附件列表序列化为 JSON 失败", e);
            }
        }

        chatMemoryRepository.saveMessage(ChatMessage.builder()
                .sessionId(sessionId)
                .userId(userId)
                .userName(userName)
                .role("USER")
                .content(question)
                .attachmentsJson(attachmentsJson)
                .createTime(LocalDateTime.now())
                .build());

        String toolsUsedJson = null;
        if (meta != null && meta.getToolsUsed() != null && !meta.getToolsUsed().isEmpty()) {
            try {
                toolsUsedJson = objectMapper.writeValueAsString(meta.getToolsUsed());
            } catch (Exception e) {
                log.warn("工具调用列表序列化为 JSON 失败", e);
            }
        }

        chatMemoryRepository.saveMessage(ChatMessage.builder()
                .sessionId(sessionId)
                .userId(null)
                .userName(null)
                .role("ASSISTANT")
                .content(answer)
                .toolsUsed(toolsUsedJson)
                .createTime(LocalDateTime.now())
                .build());
    }

    /**
     * 根据 userId 解析用户展示名：优先使用 {@code sys_user.nickname}，回退到 {@code username}。
     * userId 无法识别（为空、非数字且无对应 username）时返回 {@code null}。
     *
     * @param userId 用户ID，可能直接是 username 或数字ID
     * @return 解析出的昵称/用户名；无法解析时返回 {@code null}
     */
    private String resolveUserName(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            String sql = "SELECT COALESCE(nickname, username) FROM sys_user "
                    + "WHERE username = ? OR (id = CAST(? AS UNSIGNED) AND ? REGEXP '^[0-9]+$') LIMIT 1";
            return jdbcTemplate.query(sql, (rs, rn) -> rs.getString(1), userId, userId, userId)
                    .stream().findFirst().orElse(null);
        } catch (Exception e) {
            log.warn("[ChatAgentService] 解析用户名称失败 userId={}", userId, e);
            return null;
        }
    }
}
