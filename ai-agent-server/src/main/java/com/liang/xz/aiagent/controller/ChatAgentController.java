package com.liang.xz.aiagent.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.aiagent.agent.ChatAgentService;
import com.liang.xz.aiagent.agent.ChatAgentService.ChatResponse;
import com.liang.xz.aiagent.agent.ChatAgentService.ChatResponseMeta;
import com.liang.xz.aiagent.agent.session.SessionManager;
import com.liang.xz.aiagent.dto.ChatMultipartRequest;
import com.liang.xz.aiagent.repository.ChatMemoryRepository;
import com.liang.xz.aiagent.dto.ChatStreamRequest;
import com.liang.xz.common.core.model.R;
import com.liang.xz.resource.security.RequirePermission;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <p>AI 智能对话Agent API — 增强版</p>
 *
 * <p>新增能力: 复杂度路由 / 多文件相关性 / 响应缓存 / 工作流编排 / 会话持久化 / 供应商故障切换 / 内容安全过滤</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "AI智能对话", description = "LangChain4j Agent 智能问答 / 多模态 / 缓存 / 工作流 / 会话管理 / 主备供应商切换")
@RestController
@RequestMapping("/api/ai/chat")
public class ChatAgentController {

    private final ChatAgentService chatAgentService;
    private final SessionManager sessionManager;
    private final ChatMemoryRepository chatMemoryRepository;
    private final ObjectMapper objectMapper;
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    public ChatAgentController(ChatAgentService chatAgentService, SessionManager sessionManager,
                              ChatMemoryRepository chatMemoryRepository, ObjectMapper objectMapper) {
        this.chatAgentService = chatAgentService;
        this.sessionManager = sessionManager;
        this.chatMemoryRepository = chatMemoryRepository;
        this.objectMapper = objectMapper;
    }

    // ==================== 同步对话 ====================

    @Operation(summary = "智能对话(同步，支持多模态)",
            description = "发送自然语言问题，可附带图片/文件，AI Agent自动评估复杂度并选择最优模型回答")
    @RequirePermission("ai:chat:send")
    @PostMapping(value = "/ask", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<ChatResponse> ask(ChatMultipartRequest multipartRequest) {
        if (multipartRequest.getSessionId() == null || multipartRequest.getSessionId().isEmpty()) {
            multipartRequest.setSessionId(UUID.randomUUID().toString().substring(0, 8));
        }
        try {
            ChatResponse response = chatAgentService.chat(multipartRequest.toServiceRequest());
            return R.ok(response);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @Operation(summary = "快速问答", description = "轻量问答，不使用对话记忆，但仍会进行复杂度路由")
    @RequirePermission("ai:chat:send")
    @GetMapping("/quick")
    public R<ChatResponse> quickAsk(
            @Parameter(description = "问题") @RequestParam String q) {
        try {
            return R.ok(chatAgentService.quickAsk(q));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    // ==================== 流式对话(SSE) ====================

    @Operation(summary = "流式智能对话(SSE，multipart，支持多模态)",
            description = "通过Server-Sent Events实时返回AI生成内容，支持图片/文件附件")
    @RequirePermission("ai:chat:send")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE,
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SseEmitter streamAsk(ChatMultipartRequest multipartRequest) {
        return doStream(multipartRequest.toServiceRequest());
    }

    @Operation(summary = "流式智能对话(SSE，JSON，仅支持base64图片)",
            description = "通过Server-Sent Events实时返回AI生成内容，仅支持base64图片，不支持文件附件")
    @RequirePermission("ai:chat:send")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public SseEmitter streamAskJson(@RequestBody ChatStreamRequest streamRequest) {
        return doStream(streamRequest.toServiceRequest());
    }

    private SseEmitter doStream(ChatAgentService.ChatRequest request) {
        if (request.getSessionId() == null || request.getSessionId().isEmpty()) {
            request.setSessionId(UUID.randomUUID().toString().substring(0, 8));
        }
        final String sid = request.getSessionId();

        SseEmitter emitter = new SseEmitter(300_000L);
        // SSE 结束事件(onComplete/onError/异常)在流式场景下可能被多个 provider 重试、OkHttp 异步线程
        // 并发或重复触发，必须保证只真正结束一次，避免 "ResponseBodyEmitter has already completed"。
        final AtomicBoolean finished = new AtomicBoolean(false);

        // 线程安全地结束 SSE：仅第一次有效；sendError=true 时发送 error 事件再 complete，否则按 completeAction 处理。
        final java.util.function.Consumer<Runnable> safeFinish = (endAction) -> {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            try {
                endAction.run();
            } catch (Exception ex) {
                try {
                    emitter.completeWithError(ex);
                } catch (IllegalStateException ignored) {
                    // 已结束，忽略
                }
            }
        };

        streamExecutor.execute(() -> {
            try {
                chatAgentService.streamChat(request, new ChatAgentService.StreamCallback() {
                    private final AtomicResponseMeta metaHolder = new AtomicResponseMeta();

                    @Override
                    public void onToken(String token) {
                        // token 可能在 ended 之后到达（首 token 后失败等），此时 emitter 已关闭，
                        // send 会抛 IOException，直接静默忽略即可。
                        if (finished.get()) {
                            return;
                        }
                        try {
                            emitter.send(SseEmitter.event().name("token").data(token));
                        } catch (IOException e) {
                            log.debug("SSE发送token异常(连接可能已关闭): {}", e.getMessage());
                        } catch (IllegalStateException ignored) {
                            // emitter 已结束，忽略
                        }
                    }

                    @Override
                    public void onMeta(ChatResponseMeta meta) {
                        metaHolder.setMeta(meta);
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        safeFinish.accept(() -> {
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("complete")
                                        .data(buildCompleteData(metaHolder.getMeta())));
                            } catch (IOException sendEx) {
                                log.debug("SSE发送complete事件异常: {}", sendEx.getMessage());
                            }
                            emitter.complete();
                        });
                    }

                    @Override
                    public void onError(Throwable error) {
                        String rawMsg = error.getMessage();
                        final String errMsg;
                        if (rawMsg != null && rawMsg.contains("Invalid content-type")) {
                            errMsg = "LLM 服务返回了非流式响应(可能是地址、Key 或模型配置错误)";
                        } else {
                            errMsg = rawMsg != null ? rawMsg : "AI 服务调用失败";
                        }
                        log.error("[ChatAgent] 流式对话异常: {}", rawMsg, error);
                        safeFinish.accept(() -> {
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("error")
                                        .data(errMsg));
                            } catch (IOException sendEx) {
                                log.debug("SSE发送error事件异常: {}", sendEx.getMessage());
                            }
                            emitter.complete();
                        });
                    }
                });
            } catch (Exception e) {
                log.error("[ChatAgent] 流式对话失败", e);
                safeFinish.accept(() -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data(e.getMessage() != null ? e.getMessage() : "AI 服务调用失败"));
                    } catch (IOException sendEx) {
                        log.debug("SSE发送error事件异常: {}", sendEx.getMessage());
                    }
                    emitter.complete();
                });
            }
        });

        emitter.onCompletion(() -> log.debug("[SSE] 会话完成: {}", sid));
        emitter.onTimeout(() -> {
            // 超时未结束则主动结束，避免客户端一直挂起
            safeFinish.accept(emitter::complete);
            log.debug("[SSE] 会话超时: {}", sid);
        });
        return emitter;
    }

    private Map<String, Object> buildCompleteData(ChatResponseMeta meta) {
        Map<String, Object> data = new HashMap<>();
        data.put("finishReason", "stop");
        if (meta != null) {
            data.put("cached", meta.isCached());
            data.put("toolsUsed", meta.getToolsUsed());
            data.put("modelUsed", meta.getModelUsed());
            data.put("providerCode", meta.getProviderCode());
            data.put("providerName", meta.getProviderName());
            data.put("complexity", meta.getComplexity());
            data.put("elapsedMs", meta.getElapsedMs());
            // 多Agent编排元数据：前端据此展示"本次回答由哪些Agent协作完成"
            data.put("orchestrated", meta.isOrchestrated());
            data.put("agentIds", meta.getAgentIds());
            data.put("traceId", meta.getTraceId());
            data.put("taskCount", meta.getTaskCount());
            data.put("layerCount", meta.getLayerCount());
            data.put("orchestrationElapsedMs", meta.getOrchestrationElapsedMs());
        }
        return data;
    }

    /**
     * 线程安全的元信息容器
     */
    private static class AtomicResponseMeta {
        private ChatResponseMeta meta;

        synchronized void setMeta(ChatResponseMeta meta) {
            this.meta = meta;
        }

        synchronized ChatResponseMeta getMeta() {
            return meta;
        }
    }

    // ==================== 会话管理(增强版) ====================

    @Operation(summary = "创建新会话")
    @RequirePermission("ai:chat:session:add")
    @PostMapping("/session")
    public R<Map<String, Object>> createSession(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "新对话") String title) {
        var ctx = chatAgentService.createSession(userId, title);
        return R.ok(Map.of(
                "sessionId", ctx.getSessionId(),
                "title", ctx.getTitle(),
                "createdAt", ctx.getCreatedAt().toString()
        ));
    }

    @Operation(summary = "获取用户会话列表")
    @RequirePermission("ai:chat:session:list")
    @GetMapping("/sessions")
    public R<List<SessionManager.SessionContext>> listSessions(
            @Parameter(description = "用户ID") @RequestParam(required = false) String userId) {
        return R.ok(chatAgentService.listSessions(userId));
    }

    @Operation(summary = "切换活动会话")
    @RequirePermission("ai:chat:session:edit")
    @PostMapping("/session/{sessionId}/switch")
    public R<Map<String, Object>> switchSession(
            @PathVariable String sessionId,
            @RequestParam(required = false) String userId) {
        var ctx = chatAgentService.switchSession(sessionId, userId);
        if (ctx == null) {
            return R.fail("会话不存在: " + sessionId);
        }
        return R.ok(Map.of(
                "sessionId", ctx.getSessionId(),
                "title", ctx.getTitle(),
                "messageCount", ctx.getMessageCount()
        ));
    }

    @Operation(summary = "加载会话历史(含压缩后的上下文)")
    @RequirePermission("ai:chat:session:list")
    @GetMapping("/session/{sessionId}/history")
    public R<Map<String, Object>> loadHistory(@PathVariable String sessionId) {
        var result = chatAgentService.loadSessionHistory(sessionId);
        return R.ok(Map.of(
                "sessionId", sessionId,
                "messageCount", result.getMessages().size(),
                "isCompressed", result.isCompressed(),
                "compressionSummary", result.getCompressionSummary() != null
                        ? result.getCompressionSummary() : "",
                "messages", result.getMessages().stream()
                        .map(m -> Map.of(
                                "role", m.getRole(),
                                "content", m.getContent(),
                                "userId", m.getUserId() != null ? m.getUserId() : "",
                                "userName", m.getUserName() != null ? m.getUserName() : "",
                                "attachments", chatMemoryRepository.attachmentsFromJson(m.getAttachmentsJson()),
                                "toolsUsed", parseToolsUsed(m.getToolsUsed()),
                                "createTime", m.getCreateTime() != null
                                        ? com.liang.xz.common.core.util.BeijingTimeUtil.format(m.getCreateTime())
                                        : ""))
                        .toList()
        ));
    }

    private List<String> parseToolsUsed(String toolsUsedJson) {
        if (toolsUsedJson == null || toolsUsedJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(toolsUsedJson, new TypeReference<>() { });
        } catch (Exception e) {
            log.warn("[ChatAgentController] 解析 toolsUsed 失败: {}", toolsUsedJson, e);
            return Collections.singletonList(toolsUsedJson);
        }
    }

    @Operation(summary = "清除/关闭会话")
    @RequirePermission("ai:chat:session:delete")
    @DeleteMapping("/session/{sessionId}")
    public R<Void> clearSession(@PathVariable String sessionId) {
        chatAgentService.clearSession(sessionId);
        return R.ok(null);
    }

    @Operation(summary = "删除会话中的单条消息")
    @RequirePermission("ai:chat:session:delete")
    @DeleteMapping("/session/{sessionId}/message/{index}")
    public R<Void> deleteMessage(
            @PathVariable String sessionId,
            @PathVariable int index) {
        boolean deleted = chatAgentService.deleteMessage(sessionId, index);
        if (!deleted) {
            return R.fail("消息索引越界或会话不存在");
        }
        return R.ok(null);
    }

    // ==================== 缓存管理 ====================

    @Operation(summary = "获取缓存统计")
    @RequirePermission("ai:chat:list")
    @GetMapping("/cache/stats")
    public R<Map<String, Object>> cacheStats() {
        return R.ok(chatAgentService.getCacheStats());
    }

    @Operation(summary = "获取服务状态")
    @RequirePermission("ai:chat:list")
    @GetMapping("/status")
    public R<Map<String, Object>> status() {
        return R.ok(Map.of(
                "activeSessions", chatAgentService.getActiveSessionCount(),
                "cacheStats", chatAgentService.getCacheStats(),
                "status", "running"
        ));
    }
}
