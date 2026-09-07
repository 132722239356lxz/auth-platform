package com.liang.xz.aiagent.agent.session;

import com.liang.xz.aiagent.config.AiProperties;
import com.liang.xz.aiagent.entity.ChatMessage;
import com.liang.xz.aiagent.entity.ChatSession;
import com.liang.xz.aiagent.repository.ChatMemoryRepository;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>会话管理器 — 增强的会话生命周期管理</p>
 *
 * <p>核心能力：
 * <ol>
 *   <li><b>会话创建/切换/关闭</b>: 支持用户多会话并存，自由切换</li>
 *   <li><b>会话列表</b>: 按用户ID查询所有会话及元数据</li>
 *   <li><b>上下文压缩</b>: 当消息过多时自动触发 ContextCompressor</li>
 *   <li><b>会话恢复</b>: 加载历史消息，包含压缩后的摘要上下文</li>
 *   <li><b>过期清理</b>: 超过空闲时间的会话自动关闭</li>
 * </ol>
 * </p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class SessionManager {

    private final ChatMemoryRepository chatMemoryRepository;
    private final ContextCompressor contextCompressor;
    private final AiProperties aiProperties;

    /** 内存中的活跃会话缓存(避免频繁查库) */
    private final Map<String, SessionContext> activeSessions = new ConcurrentHashMap<>();

    public SessionManager(ChatMemoryRepository chatMemoryRepository,
                          ContextCompressor contextCompressor,
                          AiProperties aiProperties) {
        this.chatMemoryRepository = chatMemoryRepository;
        this.contextCompressor = contextCompressor;
        this.aiProperties = aiProperties;
    }

    @Data
    @Builder
    public static class SessionContext {
        private String sessionId;
        private String userId;
        private String title;
        private String status;
        private int messageCount;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private boolean compressed;
        private String summary;
    }

    @Data
    @Builder
    public static class SessionLoadResult {
        private SessionContext context;
        private List<ChatMessage> messages;
        private boolean isCompressed;
        private String compressionSummary;
    }

    /**
     * 创建或获取会话
     */
    public SessionContext getOrCreateSession(String sessionId, String userId, String title) {
        SessionContext cached = activeSessions.get(sessionId);
        if (cached != null) {
            cached.setUpdatedAt(LocalDateTime.now());
            return cached;
        }

        Optional<ChatSession> existing = chatMemoryRepository.findSession(sessionId);
        if (existing.isPresent()) {
            ChatSession s = existing.get();
            SessionContext ctx = SessionContext.builder()
                    .sessionId(s.getSessionId())
                    .userId(s.getUserId())
                    .title(s.getTitle())
                    .status(s.getStatus())
                    .createdAt(s.getCreatedAt())
                    .updatedAt(LocalDateTime.now())
                    .compressed(false)
                    .build();
            activeSessions.put(sessionId, ctx);
            return ctx;
        }

        // 新会话
        String finalSessionId = (sessionId != null && !sessionId.isBlank())
                ? sessionId : UUID.randomUUID().toString().substring(0, 8);
        ChatSession newSession = ChatSession.builder()
                .sessionId(finalSessionId)
                .userId(userId)
                .title(title != null ? title : "新对话")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        chatMemoryRepository.saveSession(newSession);

        SessionContext ctx = SessionContext.builder()
                .sessionId(finalSessionId)
                .userId(userId)
                .title(newSession.getTitle())
                .status("ACTIVE")
                .messageCount(0)
                .createdAt(newSession.getCreatedAt())
                .updatedAt(newSession.getUpdatedAt())
                .compressed(false)
                .build();
        activeSessions.put(finalSessionId, ctx);
        log.info("[SessionManager] 创建新会话: sessionId={}, userId={}", finalSessionId, userId);
        return ctx;
    }

    /**
     * 加载会话的完整消息历史（含压缩处理）
     */
    public SessionLoadResult loadSession(String sessionId) {
        List<ChatMessage> allMessages = chatMemoryRepository.findMessagesBySession(sessionId);
        if (allMessages.isEmpty()) {
            SessionContext ctx = activeSessions.get(sessionId);
            return SessionLoadResult.builder()
                    .context(ctx)
                    .messages(List.of())
                    .isCompressed(false)
                    .build();
        }

        // 检查是否需要压缩
        var sessionConfig = aiProperties.getSession();
        if (allMessages.size() > sessionConfig.getCompressThreshold()) {
            ContextCompressor.CompressResult compressed = contextCompressor.compress(allMessages);
            SessionContext ctx = activeSessions.get(sessionId);
            if (ctx != null) {
                ctx.setCompressed(true);
                ctx.setSummary(compressed.getSummary());
                ctx.setMessageCount(compressed.getCompressedCount());
            }
            log.info("[SessionManager] 会话 {} 已压缩: {}条 -> {}条",
                    sessionId, compressed.getOriginalCount(), compressed.getCompressedCount());
            return SessionLoadResult.builder()
                    .context(ctx)
                    .messages(compressed.getCompressed())
                    .isCompressed(true)
                    .compressionSummary(compressed.getSummary())
                    .build();
        }

        SessionContext ctx = activeSessions.get(sessionId);
        if (ctx != null) {
            ctx.setMessageCount(allMessages.size());
        }
        return SessionLoadResult.builder()
                .context(ctx)
                .messages(allMessages)
                .isCompressed(false)
                .build();
    }

    /**
     * 获取用户的所有会话列表
     */
    public List<SessionContext> listUserSessions(String userId) {
        List<ChatSession> sessions = chatMemoryRepository.findAllByUserId(userId);
        List<SessionContext> result = new ArrayList<>();
        for (ChatSession s : sessions) {
            int msgCount = countMessages(s.getSessionId());
            result.add(SessionContext.builder()
                    .sessionId(s.getSessionId())
                    .userId(s.getUserId())
                    .title(s.getTitle())
                    .status(s.getStatus())
                    .messageCount(msgCount)
                    .createdAt(s.getCreatedAt())
                    .updatedAt(s.getUpdatedAt())
                    .compressed(msgCount > aiProperties.getSession().getCompressThreshold())
                    .build());
        }
        return result;
    }

    /**
     * 切换活动会话
     */
    public SessionContext switchSession(String newSessionId, String userId) {
        Optional<ChatSession> session = chatMemoryRepository.findSession(newSessionId);
        if (session.isEmpty()) {
            log.warn("[SessionManager] 会话不存在: {}", newSessionId);
            return null;
        }
        ChatSession s = session.get();
        SessionContext ctx = SessionContext.builder()
                .sessionId(s.getSessionId())
                .userId(s.getUserId())
                .title(s.getTitle())
                .status(s.getStatus())
                .createdAt(s.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .compressed(false)
                .build();
        activeSessions.put(newSessionId, ctx);
        log.info("[SessionManager] 切换到会话: sessionId={}, title={}", newSessionId, s.getTitle());
        return ctx;
    }

    /**
     * 关闭会话
     */
    public void closeSession(String sessionId) {
        activeSessions.remove(sessionId);
        chatMemoryRepository.updateSessionStatus(sessionId, "CLOSED");
        log.info("[SessionManager] 关闭会话: sessionId={}", sessionId);
    }

    /**
     * 删除会话（连同消息一起删除）
     */
    public void deleteSession(String sessionId) {
        activeSessions.remove(sessionId);
        chatMemoryRepository.deleteSession(sessionId);
        log.info("[SessionManager] 删除会话: sessionId={}", sessionId);
    }

    /**
     * 更新会话标题
     */
    public void updateTitle(String sessionId, String title) {
        chatMemoryRepository.updateSessionTitle(sessionId, title);
        SessionContext ctx = activeSessions.get(sessionId);
        if (ctx != null) {
            ctx.setTitle(title);
        }
    }

    /**
     * 清理过期会话，每天凌晨 2 点执行一次
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredSessions() {
        int expireHours = aiProperties.getSession().getIdleExpireHours();
        LocalDateTime deadline = LocalDateTime.now().minusHours(expireHours);
        chatMemoryRepository.closeExpiredSessions(deadline);
        // 清理内存缓存
        activeSessions.entrySet().removeIf(entry -> {
            SessionContext ctx = entry.getValue();
            return ctx.getUpdatedAt() != null && ctx.getUpdatedAt().isBefore(deadline);
        });
        log.info("[SessionManager] 过期会话清理完成, 阈值={}小时", expireHours);
    }

    /**
     * 获取活跃会话数（从内存）
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    private int countMessages(String sessionId) {
        try {
            List<ChatMessage> msgs = chatMemoryRepository.findMessagesBySession(sessionId, 1);
            return msgs.size();
        } catch (Exception e) {
            return 0;
        }
    }
}
