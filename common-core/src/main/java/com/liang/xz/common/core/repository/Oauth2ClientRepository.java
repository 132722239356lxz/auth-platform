package com.liang.xz.common.core.repository;

import com.liang.xz.common.core.entity.Oauth2Client;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * OAuth2 客户端注册 Repository
 */
@Repository
public class Oauth2ClientRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Oauth2Client> rowMapper = (rs, rowNum) -> {
        Oauth2Client c = new Oauth2Client();
        c.setId(rs.getString("id"));
        c.setClientId(rs.getString("client_id"));
        Timestamp issuedAt = rs.getTimestamp("client_id_issued_at");
        if (issuedAt != null) c.setClientIdIssuedAt(issuedAt.toLocalDateTime());
        c.setClientSecret(rs.getString("client_secret"));
        Timestamp expiresAt = rs.getTimestamp("client_secret_expires_at");
        if (expiresAt != null) c.setClientSecretExpiresAt(expiresAt.toLocalDateTime());
        c.setClientName(rs.getString("client_name"));
        c.setClientAuthenticationMethods(rs.getString("client_authentication_methods"));
        c.setAuthorizationGrantTypes(rs.getString("authorization_grant_types"));
        c.setRedirectUris(rs.getString("redirect_uris"));
        c.setPostLogoutRedirectUris(rs.getString("post_logout_redirect_uris"));
        c.setScopes(rs.getString("scopes"));
        c.setClientSettings(rs.getString("client_settings"));
        c.setTokenSettings(rs.getString("token_settings"));
        return c;
    };

    public Oauth2ClientRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Oauth2Client> findAll() {
        return jdbcTemplate.query(
                "SELECT * FROM oauth2_registered_client ORDER BY client_id_issued_at DESC", rowMapper);
    }

    public Optional<Oauth2Client> findById(String id) {
        List<Oauth2Client> list = jdbcTemplate.query(
                "SELECT * FROM oauth2_registered_client WHERE id = ?", rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<Oauth2Client> findByClientId(String clientId) {
        List<Oauth2Client> list = jdbcTemplate.query(
                "SELECT * FROM oauth2_registered_client WHERE client_id = ?", rowMapper, clientId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * 根据 client_id 列表批量查询客户端。
     */
    public List<Oauth2Client> findByClientIdIn(List<String> clientIds) {
        if (clientIds == null || clientIds.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = String.join(",", Collections.nCopies(clientIds.size(), "?"));
        return jdbcTemplate.query(
                "SELECT * FROM oauth2_registered_client WHERE client_id IN ("
                        + placeholders + ") ORDER BY client_id_issued_at DESC",
                rowMapper, clientIds.toArray());
    }

    /**
     * 查询所有已启用客户端。
     * 启用状态由 client_settings JSON 中的 status 字段判断。
     */
    public List<Oauth2Client> findAllEnabled() {
        return jdbcTemplate.query(
                "SELECT * FROM oauth2_registered_client WHERE client_settings NOT LIKE ? "
                        + "ORDER BY client_id_issued_at DESC",
                rowMapper, "%\"status\":\"disabled\"%");
    }

    public int count() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oauth2_registered_client", Long.class);
        return count != null ? count.intValue() : 0;
    }

    /**
     * 分页查询客户端，支持关键词和启用状态筛选
     */
    public List<Oauth2Client> findPageByKeyword(String keyword, Boolean enabled, int offset, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM oauth2_registered_client WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (client_id LIKE ? OR client_name LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
        }
        if (enabled != null) {
            // client_settings JSON 中 status 字段判断启用/禁用
            sql.append(" AND client_settings ").append(enabled ? "NOT " : "").append("LIKE ?");
            params.add("%\"status\":\"disabled\"%");
        }

        sql.append(" ORDER BY client_id_issued_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), rowMapper, params.toArray());
    }

    /**
     * 按关键词和启用状态统计客户端总数
     */
    public long countByKeyword(String keyword, Boolean enabled) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM oauth2_registered_client WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (client_id LIKE ? OR client_name LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
        }
        if (enabled != null) {
            sql.append(" AND client_settings ").append(enabled ? "NOT " : "").append("LIKE ?");
            params.add("%\"status\":\"disabled\"%");
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0;
    }

    public void insert(Oauth2Client client) {
        jdbcTemplate.update(
                "INSERT INTO oauth2_registered_client " +
                "(id, client_id, client_id_issued_at, client_secret, client_secret_expires_at, " +
                "client_name, client_authentication_methods, authorization_grant_types, " +
                "redirect_uris, post_logout_redirect_uris, scopes, client_settings, token_settings) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                client.getId(), client.getClientId(),
                client.getClientIdIssuedAt(), client.getClientSecret(),
                client.getClientSecretExpiresAt(), client.getClientName(),
                client.getClientAuthenticationMethods(), client.getAuthorizationGrantTypes(),
                client.getRedirectUris(), client.getPostLogoutRedirectUris(),
                client.getScopes(), client.getClientSettings(), client.getTokenSettings());
    }

    public int update(Oauth2Client client) {
        return jdbcTemplate.update(
                "UPDATE oauth2_registered_client SET " +
                "client_name = ?, client_authentication_methods = ?, authorization_grant_types = ?, " +
                "redirect_uris = ?, post_logout_redirect_uris = ?, scopes = ?, " +
                "client_settings = ?, token_settings = ?, client_secret_expires_at = ? WHERE id = ?",
                client.getClientName(), client.getClientAuthenticationMethods(),
                client.getAuthorizationGrantTypes(), client.getRedirectUris(),
                client.getPostLogoutRedirectUris(), client.getScopes(),
                client.getClientSettings(), client.getTokenSettings(),
                client.getClientSecretExpiresAt(), client.getId());
    }

    public int updateSecret(String id, String encryptedSecret) {
        return jdbcTemplate.update(
                "UPDATE oauth2_registered_client SET client_secret = ? WHERE id = ?",
                encryptedSecret, id);
    }

    public int deleteById(String id) {
        return jdbcTemplate.update("DELETE FROM oauth2_registered_client WHERE id = ?", id);
    }
}
