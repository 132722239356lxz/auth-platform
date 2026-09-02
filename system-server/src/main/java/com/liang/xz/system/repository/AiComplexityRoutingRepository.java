package com.liang.xz.system.repository;

import com.liang.xz.system.dto.AiRoutingPageQuery;
import com.liang.xz.system.entity.SysAiComplexityRoutingEntity;
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
import java.util.Optional;

/**
 * <p>AI 复杂度路由配置数据访问层 —— 操作 sys_ai_complexity_routing 表</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Repository
public class AiComplexityRoutingRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<SysAiComplexityRoutingEntity> baseRowMapper = (rs, rowNum) -> {
        SysAiComplexityRoutingEntity e = new SysAiComplexityRoutingEntity();
        e.setId(rs.getLong("id"));
        e.setRoutingName(rs.getString("routing_name"));
        e.setProviderId(rs.getLong("provider_id"));
        e.setEnabled(rs.getBoolean("enabled"));
        e.setSimpleModel(rs.getString("simple_model"));
        e.setMediumModel(rs.getString("medium_model"));
        e.setComplexModel(rs.getString("complex_model"));
        e.setSimpleMaxTokens(rs.getInt("simple_max_tokens"));
        e.setMediumMaxTokens(rs.getInt("medium_max_tokens"));
        e.setComplexMaxTokens(rs.getInt("complex_max_tokens"));
        e.setRemark(rs.getString("remark"));
        Timestamp ct = rs.getTimestamp("create_time");
        if (ct != null) e.setCreateTime(ct.toLocalDateTime());
        Timestamp ut = rs.getTimestamp("update_time");
        if (ut != null) e.setUpdateTime(ut.toLocalDateTime());
        return e;
    };

    private final RowMapper<SysAiComplexityRoutingEntity> joinRowMapper = (rs, rowNum) -> {
        SysAiComplexityRoutingEntity e = baseRowMapper.mapRow(rs, rowNum);
        e.setProviderCode(rs.getString("provider_code"));
        e.setProviderName(rs.getString("provider_name"));
        return e;
    };

    public AiComplexityRoutingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ======================== 查询 ========================

    public long count(AiRoutingPageQuery query) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM sys_ai_complexity_routing r WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendWhere(sql, args, query);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count != null ? count : 0;
    }

    public List<SysAiComplexityRoutingEntity> findPage(AiRoutingPageQuery query) {
        StringBuilder sql = new StringBuilder(
                "SELECT r.*, p.provider_code, p.provider_name FROM sys_ai_complexity_routing r "
                        + "LEFT JOIN sys_ai_provider p ON r.provider_id = p.id WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendWhere(sql, args, query);
        sql.append(" ORDER BY r.id DESC LIMIT ? OFFSET ?");
        int pageSize = query.getPageSize() != null && query.getPageSize() > 0 ? query.getPageSize() : 10;
        int page = query.getPage() != null && query.getPage() > 0 ? query.getPage() : 1;
        args.add(pageSize);
        args.add((page - 1) * pageSize);
        return jdbcTemplate.query(sql.toString(), joinRowMapper, args.toArray());
    }

    private void appendWhere(StringBuilder sql, List<Object> args, AiRoutingPageQuery query) {
        if (query.getKeyword() != null && !query.getKeyword().trim().isEmpty()) {
            sql.append(" AND (r.routing_name LIKE ? OR p.provider_code LIKE ? OR p.provider_name LIKE ?)");
            String kw = "%" + query.getKeyword().trim() + "%";
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        if (query.getProviderId() != null) {
            sql.append(" AND r.provider_id = ?");
            args.add(query.getProviderId());
        }
        if (query.getEnabled() != null) {
            sql.append(" AND r.enabled = ?");
            args.add(query.getEnabled());
        }
    }

    public List<SysAiComplexityRoutingEntity> findAllEnabled() {
        return jdbcTemplate.query(
                "SELECT * FROM sys_ai_complexity_routing WHERE enabled = TRUE ORDER BY id ASC", baseRowMapper);
    }

    public Optional<SysAiComplexityRoutingEntity> findById(Long id) {
        List<SysAiComplexityRoutingEntity> list = jdbcTemplate.query(
                "SELECT r.*, p.provider_code, p.provider_name FROM sys_ai_complexity_routing r "
                        + "LEFT JOIN sys_ai_provider p ON r.provider_id = p.id WHERE r.id = ?",
                joinRowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public boolean existsByProviderId(Long providerId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_ai_complexity_routing WHERE provider_id = ?",
                Integer.class, providerId);
        return c != null && c > 0;
    }

    public long countByProviderId(Long providerId, Long excludeId) {
        String sql = "SELECT COUNT(*) FROM sys_ai_complexity_routing WHERE provider_id = ?";
        List<Object> args = new ArrayList<>();
        args.add(providerId);
        if (excludeId != null) {
            sql += " AND id <> ?";
            args.add(excludeId);
        }
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        return count != null ? count : 0;
    }

    // ======================== 写入 ========================

    public long insert(SysAiComplexityRoutingEntity e) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO sys_ai_complexity_routing (routing_name, provider_id, enabled, "
                            + "simple_model, medium_model, complex_model, simple_max_tokens, "
                            + "medium_max_tokens, complex_max_tokens, remark, create_time, update_time) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, e.getRoutingName());
            ps.setLong(2, e.getProviderId());
            ps.setBoolean(3, e.getEnabled() != null ? e.getEnabled() : true);
            ps.setString(4, e.getSimpleModel());
            ps.setString(5, e.getMediumModel());
            ps.setString(6, e.getComplexModel());
            ps.setInt(7, e.getSimpleMaxTokens() != null ? e.getSimpleMaxTokens() : 1024);
            ps.setInt(8, e.getMediumMaxTokens() != null ? e.getMediumMaxTokens() : 2048);
            ps.setInt(9, e.getComplexMaxTokens() != null ? e.getComplexMaxTokens() : 4096);
            ps.setString(10, e.getRemark());
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key != null ? key.longValue() : 0;
    }

    public int update(SysAiComplexityRoutingEntity e) {
        return jdbcTemplate.update(
                "UPDATE sys_ai_complexity_routing SET routing_name=?, provider_id=?, enabled=?, "
                        + "simple_model=?, medium_model=?, complex_model=?, simple_max_tokens=?, "
                        + "medium_max_tokens=?, complex_max_tokens=?, remark=?, update_time=NOW() WHERE id=?",
                e.getRoutingName(), e.getProviderId(), e.getEnabled(),
                e.getSimpleModel(), e.getMediumModel(), e.getComplexModel(),
                e.getSimpleMaxTokens(), e.getMediumMaxTokens(), e.getComplexMaxTokens(),
                e.getRemark(), e.getId());
    }

    public int deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM sys_ai_complexity_routing WHERE id = ?", id);
    }
}
