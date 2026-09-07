package com.liang.xz.system.repository;

import com.liang.xz.system.entity.SysAiInvokeLogEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>AI 调用记录数据访问层</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Repository
public class AiInvokeLogRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<SysAiInvokeLogEntity> ROW_MAPPER = (rs, rowNum) -> {
        SysAiInvokeLogEntity e = new SysAiInvokeLogEntity();
        e.setId(rs.getLong("id"));
        e.setSessionId(rs.getString("session_id"));
        e.setUserId(rs.getString("user_id"));
        e.setProviderCode(rs.getString("provider_code"));
        e.setProviderName(rs.getString("provider_name"));
        e.setProviderType(rs.getString("provider_type"));
        e.setModelName(rs.getString("model_name"));
        e.setComplexity(rs.getString("complexity"));
        e.setCached(rs.getBoolean("cached"));
        e.setPromptTokens(rs.getObject("prompt_tokens") != null ? rs.getInt("prompt_tokens") : null);
        e.setCompletionTokens(rs.getObject("completion_tokens") != null ? rs.getInt("completion_tokens") : null);
        e.setTotalTokens(rs.getObject("total_tokens") != null ? rs.getInt("total_tokens") : null);
        e.setToolNames(rs.getString("tool_names"));
        e.setToolCount(rs.getObject("tool_count") != null ? rs.getInt("tool_count") : 0);
        e.setRagReferences(rs.getString("rag_references"));
        e.setContextContent(rs.getString("context_content"));
        e.setUserInput(rs.getString("user_input"));
        e.setAiOutput(rs.getString("ai_output"));
        e.setElapsedMs(rs.getObject("elapsed_ms") != null ? rs.getInt("elapsed_ms") : null);
        e.setSuccess(rs.getBoolean("success"));
        Timestamp invokeTs = rs.getTimestamp("invoke_time");
        e.setInvokeTime(invokeTs != null ? invokeTs.toLocalDateTime() : null);
        Timestamp createTs = rs.getTimestamp("create_time");
        e.setCreateTime(createTs != null ? createTs.toLocalDateTime() : null);
        // JOIN sys_user 后填的非持久化字段
        try {
            e.setUserName(rs.getString("user_name"));
        } catch (java.sql.SQLException ignore) {
            // SELECT 全列表（无 JOIN）时不存在该列，保持空
            e.setUserName(null);
        }
        return e;
    };

    public AiInvokeLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long insert(SysAiInvokeLogEntity e) {
        String sql = "INSERT INTO sys_ai_invoke_log (session_id, user_id, provider_code, provider_name, "
                + "provider_type, model_name, complexity, cached, prompt_tokens, completion_tokens, "
                + "total_tokens, tool_names, tool_count, rag_references, context_content, user_input, "
                + "ai_output, elapsed_ms, success, error_msg, invoke_time, create_time) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, NOW())";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, e.getSessionId());
            ps.setString(2, e.getUserId());
            ps.setString(3, e.getProviderCode());
            ps.setString(4, e.getProviderName());
            ps.setString(5, e.getProviderType());
            ps.setString(6, e.getModelName());
            ps.setString(7, e.getComplexity());
            ps.setBoolean(8, e.getCached() != null ? e.getCached() : false);
            ps.setObject(9, e.getPromptTokens());
            ps.setObject(10, e.getCompletionTokens());
            ps.setObject(11, e.getTotalTokens());
            ps.setString(12, e.getToolNames());
            ps.setInt(13, e.getToolCount() != null ? e.getToolCount() : 0);
            ps.setString(14, e.getRagReferences());
            ps.setString(15, e.getContextContent());
            ps.setString(16, e.getUserInput());
            ps.setString(17, e.getAiOutput());
            ps.setObject(18, e.getElapsedMs());
            ps.setBoolean(19, e.getSuccess() != null ? e.getSuccess() : true);
            ps.setString(20, e.getErrorMsg());
            ps.setTimestamp(21, e.getInvokeTime() != null
                    ? Timestamp.valueOf(e.getInvokeTime()) : Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);
        return keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
    }

    public SysAiInvokeLogEntity findById(Long id) {
        String sql = """
                SELECT a.*, COALESCE(u.nickname, u.username) AS user_name
                  FROM sys_ai_invoke_log a
                  LEFT JOIN sys_user u
                    ON u.username = a.user_id
                    OR (a.user_id REGEXP '^[0-9]+$'
                        AND u.id = CAST(a.user_id AS UNSIGNED))
                    OR (CHAR_LENGTH(a.user_id) >= 7
                        AND u.phone = a.user_id)
                 WHERE a.id = ?
                """;
        List<SysAiInvokeLogEntity> list = jdbcTemplate.query(sql, ROW_MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<SysAiInvokeLogEntity> pageQuery(int offset, int size, String providerCode,
                                               String modelName, String userId, Boolean success,
                                               LocalDateTime start, LocalDateTime end) {
        // sys_ai_invoke_log.user_id 可能存 username / 数字 id / 手机号；
        // 三种身份在 sys_user 上都能命中，因此使用 LEFT JOIN，
        // 并在 Service 层把 JOIN 出的 user_name 映射到 AiInvokeLogResponse.userName
        StringBuilder sql = new StringBuilder("""
                SELECT a.*, COALESCE(u.nickname, u.username) AS user_name
                  FROM sys_ai_invoke_log a
                  LEFT JOIN sys_user u
                    ON u.username = a.user_id
                    OR (a.user_id REGEXP '^[0-9]+$'
                        AND u.id = CAST(a.user_id AS UNSIGNED))
                    OR (CHAR_LENGTH(a.user_id) >= 7
                        AND u.phone = a.user_id)
                 WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (providerCode != null && !providerCode.isBlank()) {
            sql.append(" AND provider_code = ?");
            args.add(providerCode);
        }
        if (modelName != null && !modelName.isBlank()) {
            sql.append(" AND model_name LIKE ?");
            args.add("%" + modelName + "%");
        }
        if (userId != null && !userId.isBlank()) {
            sql.append(" AND user_id = ?");
            args.add(userId);
        }
        if (success != null) {
            sql.append(" AND success = ?");
            args.add(success);
        }
        if (start != null) {
            sql.append(" AND invoke_time >= ?");
            args.add(Timestamp.valueOf(start));
        }
        if (end != null) {
            sql.append(" AND invoke_time <= ?");
            args.add(Timestamp.valueOf(end));
        }
        sql.append(" ORDER BY invoke_time DESC, id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    public long count(String providerCode, String modelName, String userId, Boolean success,
                      LocalDateTime start, LocalDateTime end) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM sys_ai_invoke_log WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (providerCode != null && !providerCode.isBlank()) {
            sql.append(" AND provider_code = ?");
            args.add(providerCode);
        }
        if (modelName != null && !modelName.isBlank()) {
            sql.append(" AND model_name LIKE ?");
            args.add("%" + modelName + "%");
        }
        if (userId != null && !userId.isBlank()) {
            sql.append(" AND user_id = ?");
            args.add(userId);
        }
        if (success != null) {
            sql.append(" AND success = ?");
            args.add(success);
        }
        if (start != null) {
            sql.append(" AND invoke_time >= ?");
            args.add(Timestamp.valueOf(start));
        }
        if (end != null) {
            sql.append(" AND invoke_time <= ?");
            args.add(Timestamp.valueOf(end));
        }
        Long result = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return result != null ? result : 0L;
    }

    public int deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM sys_ai_invoke_log WHERE id = ?", id);
    }

    public int clearAll() {
        return jdbcTemplate.update("DELETE FROM sys_ai_invoke_log");
    }

    /**
     * 聚合统计：总调用数、成功数、缓存命中数、总 token、平均耗时。
     * 全部以一条 SQL 聚合，时间范围可选（用于前端可视化统计卡片）。
     */
    public AiInvokeLogStats stats(LocalDateTime start, LocalDateTime end) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) AS total,
                       SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS success_cnt,
                       SUM(CASE WHEN cached = 1 THEN 1 ELSE 0 END) AS cache_hit_cnt,
                       COALESCE(SUM(total_tokens), 0) AS total_tokens,
                       COALESCE(AVG(elapsed_ms), 0) AS avg_elapsed
                  FROM sys_ai_invoke_log
                 WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (start != null) {
            sql.append(" AND invoke_time >= ?");
            args.add(Timestamp.valueOf(start));
        }
        if (end != null) {
            sql.append(" AND invoke_time <= ?");
            args.add(Timestamp.valueOf(end));
        }
        return jdbcTemplate.queryForObject(sql.toString(), (rs, rowNum) -> {
            AiInvokeLogStats s = new AiInvokeLogStats();
            s.total = rs.getLong("total");
            s.success = rs.getLong("success_cnt");
            s.cacheHit = rs.getLong("cache_hit_cnt");
            s.totalTokens = rs.getLong("total_tokens");
            s.avgElapsedMs = rs.getDouble("avg_elapsed");
            return s;
        }, args.toArray());
    }

    /** AI 调用日志聚合统计结果 */
    public static class AiInvokeLogStats {
        public long total;
        public long success;
        public long cacheHit;
        public long totalTokens;
        public double avgElapsedMs;

        /** 缓存命中率（0~1，无数据时返回 0） */
        public double cacheHitRate() {
            return total == 0 ? 0.0 : (double) cacheHit / total;
        }

        /** 成功率（0~1，无数据时返回 0） */
        public double successRate() {
            return total == 0 ? 0.0 : (double) success / total;
        }
    }
}
