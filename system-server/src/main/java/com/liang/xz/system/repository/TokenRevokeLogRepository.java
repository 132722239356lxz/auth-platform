package com.liang.xz.system.repository;

import com.liang.xz.system.entity.TokenRevokeLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 令牌吊销日志 Repository
 */
@Repository
public class TokenRevokeLogRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<TokenRevokeLog> rowMapper = (rs, rowNum) -> {
        TokenRevokeLog log = new TokenRevokeLog();
        log.setId(rs.getLong("id"));
        log.setUserId(rs.getString("user_id"));
        log.setClientId(rs.getString("client_id"));
        log.setTokenType(rs.getString("token_type"));
        log.setTokenSnip(rs.getString("token_snip"));
        log.setRevokeType(rs.getInt("revoke_type"));
        Timestamp ts = rs.getTimestamp("create_time");
        if (ts != null) log.setCreateTime(ts.toLocalDateTime());
        log.setRemark(rs.getString("remark"));
        return log;
    };

    public TokenRevokeLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insert(TokenRevokeLog log) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO sys_token_revoke_log (user_id, client_id, token_type, " +
                    "token_snip, revoke_type, remark) VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, log.getUserId());
            ps.setString(2, log.getClientId());
            ps.setString(3, log.getTokenType());
            ps.setString(4, log.getTokenSnip());
            ps.setInt(5, log.getRevokeType());
            ps.setString(6, log.getRemark());
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public List<TokenRevokeLog> findAll(int limit, int offset) {
        return jdbcTemplate.query(
                "SELECT * FROM sys_token_revoke_log ORDER BY create_time DESC LIMIT ? OFFSET ?",
                rowMapper, limit, offset);
    }

    public List<TokenRevokeLog> findByUserId(String userId) {
        return jdbcTemplate.query(
                "SELECT * FROM sys_token_revoke_log WHERE user_id = ? ORDER BY create_time DESC",
                rowMapper, userId);
    }

    public List<TokenRevokeLog> findByClientId(String clientId) {
        return jdbcTemplate.query(
                "SELECT * FROM sys_token_revoke_log WHERE client_id = ? ORDER BY create_time DESC",
                rowMapper, clientId);
    }

    public List<TokenRevokeLog> findByRevokeType(int revokeType) {
        return jdbcTemplate.query(
                "SELECT * FROM sys_token_revoke_log WHERE revoke_type = ? ORDER BY create_time DESC",
                rowMapper, revokeType);
    }

    public int countAll() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_token_revoke_log", Long.class);
        return count != null ? count.intValue() : 0;
    }

    /**
     * 分页查询吊销日志，支持按客户端ID、用户名和吊销类型多条件筛选
     */
    public List<TokenRevokeLog> findPageByFilter(String clientId, String username,
            Integer revokeType, int offset, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM sys_token_revoke_log WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (clientId != null && !clientId.trim().isEmpty()) {
            sql.append(" AND client_id = ?");
            params.add(clientId.trim());
        }
        if (username != null && !username.trim().isEmpty()) {
            sql.append(" AND user_id = ?");
            params.add(username.trim());
        }
        if (revokeType != null) {
            sql.append(" AND revoke_type = ?");
            params.add(revokeType);
        }

        sql.append(" ORDER BY create_time DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), rowMapper, params.toArray());
    }

    /**
     * 按筛选条件统计吊销日志总数
     */
    public long countByFilter(String clientId, String username, Integer revokeType) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM sys_token_revoke_log WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (clientId != null && !clientId.trim().isEmpty()) {
            sql.append(" AND client_id = ?");
            params.add(clientId.trim());
        }
        if (username != null && !username.trim().isEmpty()) {
            sql.append(" AND user_id = ?");
            params.add(username.trim());
        }
        if (revokeType != null) {
            sql.append(" AND revoke_type = ?");
            params.add(revokeType);
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0;
    }
}
