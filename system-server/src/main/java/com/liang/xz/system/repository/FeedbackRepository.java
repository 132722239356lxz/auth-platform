package com.liang.xz.system.repository;

import com.liang.xz.system.entity.FeedbackEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 意见反馈 Repository
 */
@Repository
public class FeedbackRepository {

    private final JdbcTemplate jdbcTemplate;

    public FeedbackRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insert(FeedbackEntity entity) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO sys_feedback (username, content, contact, type, status, create_time) " +
                    "VALUES (?, ?, ?, ?, ?, NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, entity.getUsername());
            ps.setString(2, entity.getContent());
            ps.setString(3, entity.getContact());
            ps.setString(4, entity.getType());
            ps.setString(5, entity.getStatus());
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public List<FeedbackEntity> findByUsername(String username) {
        return jdbcTemplate.query(
                "SELECT id, username, content, contact, type, status, create_time " +
                "FROM sys_feedback WHERE username = ? ORDER BY create_time DESC",
                (rs, rowNum) -> {
                    FeedbackEntity f = new FeedbackEntity();
                    f.setId(rs.getLong("id"));
                    f.setUsername(rs.getString("username"));
                    f.setContent(rs.getString("content"));
                    f.setContact(rs.getString("contact"));
                    f.setType(rs.getString("type"));
                    f.setStatus(rs.getString("status"));
                    Timestamp ts = rs.getTimestamp("create_time");
                    if (ts != null) f.setCreateTime(ts.toLocalDateTime());
                    return f;
                }, username);
    }
}
