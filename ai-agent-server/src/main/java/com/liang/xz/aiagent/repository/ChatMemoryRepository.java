package com.liang.xz.aiagent.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.aiagent.entity.ChatMessage;
import com.liang.xz.aiagent.entity.ChatSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * <p>AI 对话会话/消息持久化仓库</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Repository
public class ChatMemoryRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ChatMemoryRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    // ==================== 会话管理 ====================

    public void saveSession(ChatSession session) {
        String sql = """
                INSERT INTO ai_chat_session (session_id, user_id, title, status, created_at, updated_at)
                VALUES (:sessionId, :userId, :title, :status, :createdAt, :updatedAt)
                ON DUPLICATE KEY UPDATE
                    user_id = VALUES(user_id),
                    title = VALUES(title),
                    status = VALUES(status),
                    updated_at = VALUES(updated_at)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("sessionId", session.getSessionId());
        params.addValue("userId", session.getUserId());
        params.addValue("title", session.getTitle());
        params.addValue("status", session.getStatus() != null ? session.getStatus() : "ACTIVE");
        params.addValue("createdAt", session.getCreatedAt() != null ? session.getCreatedAt() : LocalDateTime.now());
        params.addValue("updatedAt", LocalDateTime.now());
        jdbc.update(sql, params);
    }

    public Optional<ChatSession> findSession(String sessionId) {
        String sql = "SELECT * FROM ai_chat_session WHERE session_id = :sessionId";
        try {
            ChatSession session = jdbc.queryForObject(sql, Map.of("sessionId", sessionId), this::mapSession);
            return Optional.ofNullable(session);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void deleteSession(String sessionId) {
        jdbc.update("DELETE FROM ai_chat_message WHERE session_id = :sessionId", Map.of("sessionId", sessionId));
        jdbc.update("DELETE FROM ai_chat_session WHERE session_id = :sessionId", Map.of("sessionId", sessionId));
    }

    // ==================== 消息管理 ====================

    public Long saveMessage(ChatMessage message) {
        String sql = """
                INSERT INTO ai_chat_message
                    (session_id, user_id, user_name, role, content, attachments_json, tools_used, latency_ms, create_time)
                VALUES
                    (:sessionId, :userId, :userName, :role, :content, :attachmentsJson, :toolsUsed, :latencyMs, :createTime)
                """;
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("sessionId", message.getSessionId());
        params.addValue("userId", message.getUserId());
        params.addValue("userName", message.getUserName());
        params.addValue("role", message.getRole());
        params.addValue("content", message.getContent());
        params.addValue("attachmentsJson", message.getAttachmentsJson());
        params.addValue("toolsUsed", message.getToolsUsed());
        params.addValue("latencyMs", message.getLatencyMs());
        params.addValue("createTime", message.getCreateTime() != null ? message.getCreateTime() : LocalDateTime.now());
        jdbc.update(sql, params, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    public List<ChatMessage> findMessagesBySession(String sessionId, int limit) {
        String sql = """
                SELECT * FROM ai_chat_message
                WHERE session_id = :sessionId
                ORDER BY create_time DESC, id DESC
                LIMIT :limit
                """;
        List<ChatMessage> messages = jdbc.query(sql,
                Map.of("sessionId", sessionId, "limit", limit), this::mapMessage);
        Collections.reverse(messages);
        return messages;
    }

    public List<ChatMessage> findMessagesBySession(String sessionId) {
        return findMessagesBySession(sessionId, 1000);
    }

    public void deleteMessagesBySession(String sessionId) {
        jdbc.update("DELETE FROM ai_chat_message WHERE session_id = :sessionId", Map.of("sessionId", sessionId));
    }

    // ==================== 会话增强操作 ====================

    public List<ChatSession> findAllByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return jdbc.query("SELECT * FROM ai_chat_session WHERE status = 'ACTIVE' ORDER BY updated_at DESC LIMIT 50",
                    this::mapSession);
        }
        String sql = "SELECT * FROM ai_chat_session WHERE user_id = :userId ORDER BY updated_at DESC";
        return jdbc.query(sql, Map.of("userId", userId), this::mapSession);
    }

    public void updateSessionStatus(String sessionId, String status) {
        jdbc.update("UPDATE ai_chat_session SET status = :status, updated_at = :updatedAt WHERE session_id = :sessionId",
                Map.of("sessionId", sessionId, "status", status, "updatedAt", java.time.LocalDateTime.now()));
    }

    public void updateSessionTitle(String sessionId, String title) {
        jdbc.update("UPDATE ai_chat_session SET title = :title, updated_at = :updatedAt WHERE session_id = :sessionId",
                Map.of("sessionId", sessionId, "title", title, "updatedAt", java.time.LocalDateTime.now()));
    }

    public void closeExpiredSessions(java.time.LocalDateTime deadline) {
        jdbc.update("UPDATE ai_chat_session SET status = 'EXPIRED', updated_at = :now WHERE status = 'ACTIVE' AND updated_at < :deadline",
                Map.of("now", java.time.LocalDateTime.now(), "deadline", deadline));
    }

    public int countMessagesBySession(String sessionId) {
        try {
            Integer count = jdbc.getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM ai_chat_message WHERE session_id = ?", Integer.class, sessionId);
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    // ==================== 辅助方法 ====================

    public String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON序列化失败", e);
            return "{}";
        }
    }

    public Map<String, Object> attachmentsFromJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("附件JSON解析失败", e);
            return Map.of();
        }
    }

    private ChatSession mapSession(ResultSet rs, int rowNum) throws SQLException {
        return ChatSession.builder()
                .id(rs.getLong("id"))
                .sessionId(rs.getString("session_id"))
                .userId(rs.getString("user_id"))
                .title(rs.getString("title"))
                .status(rs.getString("status"))
                .createdAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null)
                .updatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null)
                .build();
    }

    private ChatMessage mapMessage(ResultSet rs, int rowNum) throws SQLException {
        return ChatMessage.builder()
                .id(rs.getLong("id"))
                .sessionId(rs.getString("session_id"))
                .userId(rs.getString("user_id"))
                .userName(rs.getString("user_name"))
                .role(rs.getString("role"))
                .content(rs.getString("content"))
                .attachmentsJson(rs.getString("attachments_json"))
                .toolsUsed(rs.getString("tools_used"))
                .latencyMs(rs.getLong("latency_ms"))
                .createTime(rs.getTimestamp("create_time") != null ? rs.getTimestamp("create_time").toLocalDateTime() : null)
                .build();
    }
}
