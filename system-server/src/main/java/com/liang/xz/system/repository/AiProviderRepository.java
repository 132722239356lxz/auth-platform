package com.liang.xz.system.repository;

import com.liang.xz.system.dto.AiProviderPageQuery;
import com.liang.xz.system.entity.SysAiProviderEntity;
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
 * <p>AI 供应商配置数据访问层 —— 操作 sys_ai_provider 表</p>
 *
 * <p>密钥字段(api_key/secret_key)在落库前由 Service 层加密，本类只负责读写。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Repository
public class AiProviderRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<SysAiProviderEntity> rowMapper = (rs, rowNum) -> {
        SysAiProviderEntity e = new SysAiProviderEntity();
        e.setId(rs.getLong("id"));
        e.setProviderCode(rs.getString("provider_code"));
        e.setProviderName(rs.getString("provider_name"));
        e.setProviderType(rs.getString("provider_type"));
        e.setBaseUrl(rs.getString("base_url"));
        e.setApiKey(rs.getString("api_key"));
        e.setSecretKey(rs.getString("secret_key"));
        e.setDefaultModel(rs.getString("default_model"));
        e.setEmbeddingModel(rs.getString("embedding_model"));
        e.setModels(rs.getString("models"));
        e.setTimeoutMs(rs.getInt("timeout_ms"));
        e.setMaxRetries(rs.getInt("max_retries"));
        e.setTemperature(rs.getBigDecimal("temperature"));
        e.setEnabled(rs.getBoolean("enabled"));
        e.setIsPrimary(rs.getBoolean("is_primary"));
        e.setPriority(rs.getInt("priority"));
        Timestamp ct = rs.getTimestamp("create_time");
        if (ct != null) e.setCreateTime(ct.toLocalDateTime());
        Timestamp ut = rs.getTimestamp("update_time");
        if (ut != null) e.setUpdateTime(ut.toLocalDateTime());
        return e;
    };

    public AiProviderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ======================== 查询 ========================

    public long count(AiProviderPageQuery query) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM sys_ai_provider WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendWhere(sql, args, query);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count != null ? count : 0;
    }

    public List<SysAiProviderEntity> findPage(AiProviderPageQuery query) {
        StringBuilder sql = new StringBuilder("SELECT * FROM sys_ai_provider WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendWhere(sql, args, query);
        sql.append(" ORDER BY priority ASC, id DESC LIMIT ? OFFSET ?");
        int pageSize = query.getPageSize() != null && query.getPageSize() > 0 ? query.getPageSize() : 10;
        int page = query.getPage() != null && query.getPage() > 0 ? query.getPage() : 1;
        args.add(pageSize);
        args.add((page - 1) * pageSize);
        return jdbcTemplate.query(sql.toString(), rowMapper, args.toArray());
    }

    private void appendWhere(StringBuilder sql, List<Object> args, AiProviderPageQuery query) {
        if (query.getProviderName() != null && !query.getProviderName().trim().isEmpty()) {
            sql.append(" AND (provider_name LIKE ? OR provider_code LIKE ?)");
            String kw = "%" + query.getProviderName().trim() + "%";
            args.add(kw);
            args.add(kw);
        }
        if (query.getProviderType() != null && !query.getProviderType().trim().isEmpty()) {
            sql.append(" AND provider_type = ?");
            args.add(query.getProviderType().trim());
        }
        if (query.getEnabled() != null) {
            sql.append(" AND enabled = ?");
            args.add(query.getEnabled());
        }
    }

    public List<SysAiProviderEntity> findAllEnabled() {
        return jdbcTemplate.query(
                "SELECT * FROM sys_ai_provider WHERE enabled = TRUE ORDER BY priority ASC, id ASC", rowMapper);
    }

    public List<SysAiProviderEntity> findAvailable(Long excludeRoutingId) {
        String sql = "SELECT id, provider_code, provider_name, provider_type, default_model, models "
                + "FROM sys_ai_provider WHERE enabled = TRUE AND id NOT IN ("
                + "SELECT provider_id FROM sys_ai_complexity_routing WHERE 1=1";
        List<Object> args = new ArrayList<>();
        if (excludeRoutingId != null) {
            sql += " AND id <> ?";
            args.add(excludeRoutingId);
        }
        sql += ") ORDER BY priority ASC, id ASC";
        return jdbcTemplate.query(sql, (rs, rn) -> {
            SysAiProviderEntity e = new SysAiProviderEntity();
            e.setId(rs.getLong("id"));
            e.setProviderCode(rs.getString("provider_code"));
            e.setProviderName(rs.getString("provider_name"));
            e.setProviderType(rs.getString("provider_type"));
            e.setDefaultModel(rs.getString("default_model"));
            e.setModels(rs.getString("models"));
            return e;
        }, args.toArray());
    }

    public Optional<SysAiProviderEntity> findById(Long id) {
        List<SysAiProviderEntity> list = jdbcTemplate.query(
                "SELECT * FROM sys_ai_provider WHERE id = ?", rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public SysAiProviderEntity findByCode(String code) {
        List<SysAiProviderEntity> list = jdbcTemplate.query(
                "SELECT * FROM sys_ai_provider WHERE provider_code = ?", rowMapper, code);
        return list.isEmpty() ? null : list.get(0);
    }

    public boolean existsByProviderCode(String code, Long excludeId) {
        if (excludeId == null) {
            Integer c = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_ai_provider WHERE provider_code = ?", Integer.class, code);
            return c != null && c > 0;
        }
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_ai_provider WHERE provider_code = ? AND id <> ?",
                Integer.class, code, excludeId);
        return c != null && c > 0;
    }

    // ======================== 写入 ========================

    public long insert(SysAiProviderEntity e) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO sys_ai_provider (provider_code, provider_name, provider_type, base_url, "
                            + "api_key, secret_key, default_model, embedding_model, models, timeout_ms, "
                            + "max_retries, temperature, enabled, is_primary, priority, remark, create_time, "
                            + "update_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, e.getProviderCode());
            ps.setString(2, e.getProviderName());
            ps.setString(3, e.getProviderType());
            ps.setString(4, e.getBaseUrl());
            ps.setString(5, e.getApiKey());
            ps.setString(6, e.getSecretKey());
            ps.setString(7, e.getDefaultModel());
            ps.setString(8, e.getEmbeddingModel());
            ps.setString(9, e.getModels());
            ps.setInt(10, e.getTimeoutMs() != null ? e.getTimeoutMs() : 30000);
            ps.setInt(11, e.getMaxRetries() != null ? e.getMaxRetries() : 3);
            ps.setBigDecimal(12, e.getTemperature());
            ps.setBoolean(13, e.getEnabled() != null ? e.getEnabled() : true);
            ps.setBoolean(14, e.getIsPrimary() != null ? e.getIsPrimary() : true);
            ps.setInt(15, e.getPriority() != null ? e.getPriority() : 100);
            ps.setString(16, e.getRemark());
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key != null ? key.longValue() : 0;
    }

    public int update(SysAiProviderEntity e) {
        return jdbcTemplate.update(
                "UPDATE sys_ai_provider SET provider_code=?, provider_name=?, provider_type=?, base_url=?, "
                        + "api_key=?, secret_key=?, default_model=?, embedding_model=?, models=?, timeout_ms=?, "
                        + "max_retries=?, temperature=?, enabled=?, is_primary=?, priority=?, remark=?, "
                        + "update_time=NOW() WHERE id=?",
                e.getProviderCode(), e.getProviderName(), e.getProviderType(), e.getBaseUrl(),
                e.getApiKey(), e.getSecretKey(), e.getDefaultModel(), e.getEmbeddingModel(), e.getModels(),
                e.getTimeoutMs(), e.getMaxRetries(), e.getTemperature(), e.getEnabled(),
                e.getIsPrimary(), e.getPriority(), e.getRemark(), e.getId());
    }

    public int deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM sys_ai_provider WHERE id = ?", id);
    }

    public int clearOtherPrimaryFlags(Long excludeId) {
        return jdbcTemplate.update(
                "UPDATE sys_ai_provider SET is_primary = FALSE WHERE id <> ? AND is_primary = TRUE",
                excludeId);
    }
}
