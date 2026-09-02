package com.liang.xz.aiagent.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * <p>AI 调用记录写入仓储(与 system-server 共用同一张表 sys_ai_invoke_log)</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Repository
public class AiInvokeLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public AiInvokeLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 写入一条调用记录
     *
     * @param params 字段顺序与 SQL 占位符一致
     */
    public void insert(AiInvokeLogParams params) {
        String sql = "INSERT INTO sys_ai_invoke_log (session_id, user_id, provider_code, provider_name, "
                + "provider_type, model_name, complexity, cached, prompt_tokens, completion_tokens, "
                + "total_tokens, tool_names, tool_count, rag_references, context_content, user_input, "
                + "ai_output, elapsed_ms, success, error_msg, invoke_time, create_time) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, NOW())";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, params.sessionId);
            ps.setString(2, params.userId);
            ps.setString(3, params.providerCode);
            ps.setString(4, params.providerName);
            ps.setString(5, params.providerType);
            ps.setString(6, params.modelName);
            ps.setString(7, params.complexity);
            ps.setBoolean(8, params.cached);
            ps.setObject(9, params.promptTokens);
            ps.setObject(10, params.completionTokens);
            ps.setObject(11, params.totalTokens);
            ps.setString(12, params.toolNames);
            ps.setInt(13, params.toolCount);
            ps.setString(14, params.ragReferences);
            ps.setString(15, params.contextContent);
            ps.setString(16, params.userInput);
            ps.setString(17, params.aiOutput);
            ps.setObject(18, params.elapsedMs);
            ps.setBoolean(19, params.success);
            ps.setString(20, params.errorMsg);
            ps.setTimestamp(21, params.invokeTime != null
                    ? Timestamp.valueOf(params.invokeTime) : Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);
    }

    /**
     * AI 调用记录参数载体
     */
    public static class AiInvokeLogParams {
        public String sessionId;
        public String userId;
        public String providerCode;
        public String providerName;
        public String providerType;
        public String modelName;
        public String complexity;
        public boolean cached;
        public Integer promptTokens;
        public Integer completionTokens;
        public Integer totalTokens;
        public String toolNames;
        public int toolCount;
        public String ragReferences;
        public String contextContent;
        public String userInput;
        public String aiOutput;
        public Integer elapsedMs;
        public boolean success;
        public String errorMsg;
        public LocalDateTime invokeTime;
    }
}
