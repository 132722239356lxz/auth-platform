package com.liang.xz.system.repository;

import com.liang.xz.system.entity.SubsystemToken;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 子系统Token Repository
 */
@Repository
public class SubsystemTokenRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<SubsystemToken> rowMapper = (rs, rowNum) -> {
        SubsystemToken t = new SubsystemToken();
        t.setId(rs.getLong("id"));
        t.setClientId(rs.getString("client_id"));
        t.setUserId(rs.getObject("user_id") != null ? rs.getLong("user_id") : null);
        t.setUsername(rs.getString("username"));
        t.setAccessToken(rs.getString("access_token"));
        t.setRefreshToken(rs.getString("refresh_token"));
        t.setTokenType(rs.getString("token_type"));
        t.setAccessTokenExpiresAt(toLocal(rs.getTimestamp("access_token_expires_at")));
        t.setRefreshTokenExpiresAt(toLocal(rs.getTimestamp("refresh_token_expires_at")));
        t.setStatus(rs.getString("status"));
        t.setParentTokenId(rs.getObject("parent_token_id") != null
                ? rs.getLong("parent_token_id") : null);
        t.setIssuedIp(rs.getString("issued_ip"));
        t.setUserAgent(rs.getString("user_agent"));
        t.setCreateTime(toLocal(rs.getTimestamp("create_time")));
        t.setLastRefreshTime(toLocal(rs.getTimestamp("last_refresh_time")));
        t.setRefreshCount(rs.getObject("refresh_count") != null
                ? rs.getInt("refresh_count") : 0);
        t.setRevokeTime(toLocal(rs.getTimestamp("revoke_time")));
        t.setRevokeReason(rs.getObject("revoke_reason") != null
                ? rs.getInt("revoke_reason") : null);
        t.setRemark(rs.getString("remark"));
        return t;
    };

    public SubsystemTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long insert(SubsystemToken token) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO subsystem_token (client_id, user_id, username, access_token, " +
                    "refresh_token, token_type, access_token_expires_at, refresh_token_expires_at, " +
                    "status, parent_token_id, issued_ip, user_agent, create_time, refresh_count) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    PreparedStatement.RETURN_GENERATED_KEYS);
            ps.setString(1, token.getClientId());
            if (token.getUserId() != null) ps.setLong(2, token.getUserId());
            else ps.setNull(2, java.sql.Types.BIGINT);
            ps.setString(3, token.getUsername());
            ps.setString(4, token.getAccessToken());
            ps.setString(5, token.getRefreshToken());
            ps.setString(6, token.getTokenType() != null ? token.getTokenType() : "JWT_BEARER");
            ps.setTimestamp(7, token.getAccessTokenExpiresAt() != null
                    ? Timestamp.valueOf(token.getAccessTokenExpiresAt()) : null);
            ps.setTimestamp(8, token.getRefreshTokenExpiresAt() != null
                    ? Timestamp.valueOf(token.getRefreshTokenExpiresAt()) : null);
            ps.setString(9, token.getStatus() != null ? token.getStatus() : "ACTIVE");
            if (token.getParentTokenId() != null) ps.setLong(10, token.getParentTokenId());
            else ps.setNull(10, java.sql.Types.BIGINT);
            ps.setString(11, token.getIssuedIp());
            ps.setString(12, token.getUserAgent());
            ps.setTimestamp(13, Timestamp.valueOf(token.getCreateTime() != null
                    ? token.getCreateTime() : LocalDateTime.now()));
            ps.setInt(14, token.getRefreshCount() != null ? token.getRefreshCount() : 0);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    public Optional<SubsystemToken> findById(Long id) {
        List<SubsystemToken> list = jdbcTemplate.query(
                "SELECT * FROM subsystem_token WHERE id = ?", rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<SubsystemToken> findByRefreshToken(String refreshToken) {
        List<SubsystemToken> list = jdbcTemplate.query(
                "SELECT * FROM subsystem_token WHERE refresh_token = ? AND status = 'ACTIVE'",
                rowMapper, refreshToken);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<SubsystemToken> findActiveByClientAndUser(String clientId, String username) {
        return jdbcTemplate.query(
                "SELECT * FROM subsystem_token WHERE client_id = ? AND username = ? "
                + "AND status = 'ACTIVE' ORDER BY create_time DESC",
                rowMapper, clientId, username);
    }

    public List<SubsystemToken> findAllByClientAndUser(String clientId, String username,
            int limit, int offset) {
        return jdbcTemplate.query(
                "SELECT * FROM subsystem_token WHERE client_id = ? AND username = ? "
                + "ORDER BY create_time DESC LIMIT ? OFFSET ?",
                rowMapper, clientId, username, limit, offset);
    }

    public List<SubsystemToken> findByClientId(String clientId, int limit, int offset) {
        return jdbcTemplate.query(
                "SELECT * FROM subsystem_token WHERE client_id = ? "
                + "ORDER BY create_time DESC LIMIT ? OFFSET ?",
                rowMapper, clientId, limit, offset);
    }

    public List<SubsystemToken> findByUsername(String username, int limit, int offset) {
        return jdbcTemplate.query(
                "SELECT * FROM subsystem_token WHERE username = ? "
                + "ORDER BY create_time DESC LIMIT ? OFFSET ?",
                rowMapper, username, limit, offset);
    }

    public int markAsRefreshed(Long id) {
        return jdbcTemplate.update(
                "UPDATE subsystem_token SET status = 'REFRESHED' WHERE id = ?", id);
    }

    public int revoke(Long id, int revokeReason, String remark) {
        return jdbcTemplate.update(
                "UPDATE subsystem_token SET status = 'REVOKED', revoke_time = NOW(), " +
                "revoke_reason = ?, remark = ? WHERE id = ?",
                revokeReason, remark, id);
    }

    public int revokeAllActiveByClientAndUser(String clientId, String username,
            int revokeReason, String remark) {
        return jdbcTemplate.update(
                "UPDATE subsystem_token SET status = 'REVOKED', revoke_time = NOW(), " +
                "revoke_reason = ?, remark = ? " +
                "WHERE client_id = ? AND username = ? AND status = 'ACTIVE'",
                revokeReason, remark, clientId, username);
    }

    public int updateRefreshInfo(Long id, LocalDateTime refreshTime, int refreshCount) {
        return jdbcTemplate.update(
                "UPDATE subsystem_token SET last_refresh_time = ?, refresh_count = ? WHERE id = ?",
                Timestamp.valueOf(refreshTime), refreshCount, id);
    }

    public int markExpiredTokens() {
        return jdbcTemplate.update(
                "UPDATE subsystem_token SET status = 'EXPIRED' " +
                "WHERE status = 'ACTIVE' AND access_token_expires_at < NOW()");
    }

    public long countActive() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subsystem_token WHERE status = 'ACTIVE' AND access_token_expires_at >= NOW()",
                Long.class);
        return count != null ? count : 0;
    }

    public int countActiveByUser(String username) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subsystem_token WHERE username = ? AND status = 'ACTIVE'",
                Long.class, username);
        return count.intValue();
    }

    public int countAll() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subsystem_token", Long.class);
        return count != null ? count.intValue() : 0;
    }

    public int cleanExpiredTokens(int retentionDays) {
        return jdbcTemplate.update(
                "DELETE FROM subsystem_token WHERE status IN ('EXPIRED', 'REVOKED') " +
                "AND create_time < DATE_SUB(NOW(), INTERVAL ? DAY)", retentionDays);
    }

    /**
     * 分页查询Token，支持按客户端ID、用户名、状态、过期状态筛选
     */
    public List<SubsystemToken> findPageByFilter(String clientId, String username, String status, Boolean expired, int offset, int limit) {
        StringBuilder sql = buildFilterSql(clientId, username, status, expired);
        sql.insert(0, "SELECT * ");
        sql.append(" ORDER BY create_time DESC LIMIT ? OFFSET ?");
        List<Object> params = buildFilterParams(clientId, username, status, expired);
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql.toString(), rowMapper, params.toArray());
    }

    /**
     * 按筛选条件统计Token总数
     */
    public long countByFilter(String clientId, String username, String status, Boolean expired) {
        StringBuilder sql = buildFilterSql(clientId, username, status, expired);
        sql.insert(0, "SELECT COUNT(*) ");
        List<Object> params = buildFilterParams(clientId, username, status, expired);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0;
    }

    private StringBuilder buildFilterSql(String clientId, String username, String status, Boolean expired) {
        StringBuilder sql = new StringBuilder("FROM subsystem_token WHERE 1=1");
        if (clientId != null && !clientId.trim().isEmpty()) {
            sql.append(" AND client_id = ?");
        }
        if (username != null && !username.trim().isEmpty()) {
            sql.append(" AND username = ?");
        }
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND status = ?");
        }
        if (expired != null) {
            sql.append(expired ? " AND access_token_expires_at < NOW()" : " AND access_token_expires_at >= NOW()");
        }
        return sql;
    }

    private List<Object> buildFilterParams(String clientId, String username, String status, Boolean expired) {
        List<Object> params = new ArrayList<>();
        if (clientId != null && !clientId.trim().isEmpty()) {
            params.add(clientId.trim());
        }
        if (username != null && !username.trim().isEmpty()) {
            params.add(username.trim());
        }
        if (status != null && !status.trim().isEmpty()) {
            params.add(status.trim());
        }
        return params;
    }

    private static LocalDateTime toLocal(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }
}
