package com.liang.xz.aiagent.repository;

import com.liang.xz.aiagent.config.DbProviderConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>从 sys_ai_provider 表动态读取供应商配置，用于 ai-agent 运行时的主备供应商故障切换</p>
 *
 * <p>查询策略：按 is_primary DESC, priority ASC 排序，主供应商排在第一位</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Repository
public class DbProviderConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public DbProviderConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询所有已启用的供应商，按优先级排序：主供应商 → 备用供应商(按 priority 升序)
     */
    public List<DbProviderConfig> findAllEnabled() {
        String sql = "SELECT id, provider_code, provider_name, provider_type, base_url, "
                + "api_key, secret_key, default_model, embedding_model, max_tokens, models, "
                + "is_primary, priority, timeout_ms, max_retries, temperature "
                + "FROM sys_ai_provider WHERE enabled = 1 "
                + "ORDER BY is_primary DESC, priority ASC, id ASC";

        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> DbProviderConfig.builder()
                    .id(rs.getLong("id"))
                    .providerCode(rs.getString("provider_code"))
                    .providerName(rs.getString("provider_name"))
                    .providerType(rs.getString("provider_type"))
                    .baseUrl(rs.getString("base_url"))
                    .apiKey(rs.getString("api_key"))
                    .secretKey(rs.getString("secret_key"))
                    .defaultModel(rs.getString("default_model"))
                    .embeddingModel(rs.getString("embedding_model"))
                    .maxTokens(rs.getObject("max_tokens") != null ? rs.getInt("max_tokens") : null)
                    .models(rs.getString("models"))
                    .isPrimary(rs.getBoolean("is_primary"))
                    .priority(rs.getInt("priority"))
                    .timeoutMs(rs.getObject("timeout_ms") != null ? rs.getInt("timeout_ms") : 30000)
                    .maxRetries(rs.getObject("max_retries") != null ? rs.getInt("max_retries") : 2)
                    .temperature(rs.getObject("temperature") != null ? rs.getDouble("temperature") : null)
                    .build());
        } catch (Exception e) {
            log.error("[DbProviderConfig] 读取供应商配置失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 按供应商编码查询启用状态的供应商（用于嵌入供应商加载）。
     */
    public DbProviderConfig findByCodeAndEnabled(String code) {
        String sql = "SELECT id, provider_code, provider_name, provider_type, base_url, "
                + "api_key, secret_key, default_model, embedding_model, max_tokens, models, "
                + "is_primary, priority, timeout_ms, max_retries, temperature "
                + "FROM sys_ai_provider WHERE provider_code = ? AND enabled = 1";
        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> DbProviderConfig.builder()
                    .id(rs.getLong("id"))
                    .providerCode(rs.getString("provider_code"))
                    .providerName(rs.getString("provider_name"))
                    .providerType(rs.getString("provider_type"))
                    .baseUrl(rs.getString("base_url"))
                    .apiKey(rs.getString("api_key"))
                    .secretKey(rs.getString("secret_key"))
                    .defaultModel(rs.getString("default_model"))
                    .embeddingModel(rs.getString("embedding_model"))
                    .maxTokens(rs.getObject("max_tokens") != null ? rs.getInt("max_tokens") : null)
                    .models(rs.getString("models"))
                    .isPrimary(rs.getBoolean("is_primary"))
                    .priority(rs.getInt("priority"))
                    .timeoutMs(rs.getObject("timeout_ms") != null ? rs.getInt("timeout_ms") : 30000)
                    .maxRetries(rs.getObject("max_retries") != null ? rs.getInt("max_retries") : 2)
                    .temperature(rs.getObject("temperature") != null ? rs.getDouble("temperature") : null)
                    .build())
                    .stream().findFirst().orElse(null);
        } catch (Exception e) {
            log.error("[DbProviderConfig] 查询供应商({})失败", code, e);
            return null;
        }
    }

    /**
     * 校验并获取可用的供应商ID
     */
    public boolean isProviderEnabled(Long providerId) {
        String sql = "SELECT COUNT(*) FROM sys_ai_provider WHERE id = ? AND enabled = 1";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, providerId);
        return count != null && count > 0;
    }
}
