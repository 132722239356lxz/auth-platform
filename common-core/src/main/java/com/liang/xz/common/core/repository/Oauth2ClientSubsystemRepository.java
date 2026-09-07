package com.liang.xz.common.core.repository;

import com.liang.xz.common.core.entity.Oauth2ClientSubsystem;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * 客户端子系统数据访问层 —— 操作 oauth2_client_subsystem 表
 */
@Repository
@RequiredArgsConstructor
public class Oauth2ClientSubsystemRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Oauth2ClientSubsystem> rowMapper = (rs, rowNum) -> Oauth2ClientSubsystem.builder()
            .id(rs.getLong("id"))
            .clientId(rs.getString("client_id"))
            .code(rs.getString("code"))
            .name(rs.getString("name"))
            .iconUrl(rs.getString("icon_url"))
            .redirectUri(rs.getString("redirect_uri"))
            .description(rs.getString("description"))
            .sortOrder(rs.getInt("sort_order"))
            .visiblePortal(rs.getBoolean("visible_portal"))
            .createTime(rs.getTimestamp("create_time") != null
                    ? rs.getTimestamp("create_time").toLocalDateTime() : null)
            .updateTime(rs.getTimestamp("update_time") != null
                    ? rs.getTimestamp("update_time").toLocalDateTime() : null)
            .build();

    /**
     * 查询某客户端下的所有子系统（按排序号和创建时间排序）
     */
    public List<Oauth2ClientSubsystem> findByClientId(String clientId) {
        String sql = "SELECT * FROM oauth2_client_subsystem WHERE client_id = ? ORDER BY sort_order ASC, create_time ASC";
        return jdbcTemplate.query(sql, rowMapper, clientId);
    }

    /**
     * 按主键查询子系统
     */
    public Optional<Oauth2ClientSubsystem> findById(Long id) {
        String sql = "SELECT * FROM oauth2_client_subsystem WHERE id = ?";
        List<Oauth2ClientSubsystem> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * 查询所有门户可见的子系统（按排序号排序）
     */
    public List<Oauth2ClientSubsystem> findAllVisible() {
        String sql = "SELECT s.* FROM oauth2_client_subsystem s " +
                "INNER JOIN oauth2_registered_client c ON s.client_id = c.client_id " +
                "WHERE s.visible_portal = 1 " +
                "AND (c.client_settings NOT LIKE '%\"status\":\"disabled\"%' OR c.client_settings IS NULL) " +
                "ORDER BY s.sort_order ASC, s.create_time ASC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    /**
     * 查询所有子系统（不限可见性）
     */
    public List<Oauth2ClientSubsystem> findAll() {
        String sql = "SELECT * FROM oauth2_client_subsystem ORDER BY sort_order ASC, create_time ASC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    /**
     * 插入新子系统，返回自增ID
     */
    public Long insert(Oauth2ClientSubsystem entity) {
        String sql = "INSERT INTO oauth2_client_subsystem " +
                "(client_id, code, name, icon_url, redirect_uri, description, sort_order, visible_portal, create_time, update_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, entity.getClientId());
            ps.setString(2, entity.getCode() != null ? entity.getCode() : "web");
            ps.setString(3, entity.getName());
            ps.setString(4, entity.getIconUrl());
            ps.setString(5, entity.getRedirectUri());
            ps.setString(6, entity.getDescription());
            ps.setInt(7, entity.getSortOrder() != null ? entity.getSortOrder() : 0);
            ps.setBoolean(8, entity.getVisiblePortal() != null ? entity.getVisiblePortal() : true);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    /**
     * 更新子系统
     */
    public int update(Oauth2ClientSubsystem entity) {
        String sql = "UPDATE oauth2_client_subsystem SET " +
                "code = ?, name = ?, icon_url = ?, redirect_uri = ?, description = ?, " +
                "sort_order = ?, visible_portal = ?, update_time = NOW() " +
                "WHERE id = ?";
        return jdbcTemplate.update(sql,
                entity.getCode() != null ? entity.getCode() : "web",
                entity.getName(),
                entity.getIconUrl(),
                entity.getRedirectUri(),
                entity.getDescription(),
                entity.getSortOrder() != null ? entity.getSortOrder() : 0,
                entity.getVisiblePortal() != null ? entity.getVisiblePortal() : true,
                entity.getId());
    }

    /**
     * 删除子系统
     */
    public int deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM oauth2_client_subsystem WHERE id = ?", id);
    }

    /**
     * 删除某客户端下的所有子系统
     */
    public int deleteByClientId(String clientId) {
        return jdbcTemplate.update("DELETE FROM oauth2_client_subsystem WHERE client_id = ?", clientId);
    }
}
