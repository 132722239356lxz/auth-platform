package com.liang.xz.system.repository;

import com.liang.xz.system.entity.AuthorizationRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * 授权记录 Repository
 */
@Repository
public class AuthorizationRecordRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<AuthorizationRecord> rowMapper = (rs, rowNum) -> {
        AuthorizationRecord r = new AuthorizationRecord();
        r.setId(rs.getString("id"));
        r.setRegisteredClientId(rs.getString("registered_client_id"));
        r.setPrincipalName(rs.getString("principal_name"));
        r.setAuthorizationGrantType(rs.getString("authorization_grant_type"));
        r.setAuthorizedScopes(rs.getString("authorized_scopes"));
        r.setAttributes(rs.getString("attributes"));
        r.setState(rs.getString("state"));
        r.setAuthorizationCodeValue(rs.getString("authorization_code_value"));
        r.setAuthorizationCodeIssuedAt(toLocal(rs.getTimestamp("authorization_code_issued_at")));
        r.setAuthorizationCodeExpiresAt(toLocal(rs.getTimestamp("authorization_code_expires_at")));
        r.setAuthorizationCodeMetadata(rs.getString("authorization_code_metadata"));
        r.setAccessTokenValue(rs.getString("access_token_value"));
        r.setAccessTokenIssuedAt(toLocal(rs.getTimestamp("access_token_issued_at")));
        r.setAccessTokenExpiresAt(toLocal(rs.getTimestamp("access_token_expires_at")));
        r.setAccessTokenMetadata(rs.getString("access_token_metadata"));
        r.setRefreshTokenValue(rs.getString("refresh_token_value"));
        r.setRefreshTokenIssuedAt(toLocal(rs.getTimestamp("refresh_token_issued_at")));
        r.setRefreshTokenExpiresAt(toLocal(rs.getTimestamp("refresh_token_expires_at")));
        r.setRefreshTokenMetadata(rs.getString("refresh_token_metadata"));
        r.setOidcIdTokenValue(rs.getString("oidc_id_token_value"));
        r.setOidcIdTokenIssuedAt(toLocal(rs.getTimestamp("oidc_id_token_issued_at")));
        r.setOidcIdTokenExpiresAt(toLocal(rs.getTimestamp("oidc_id_token_expires_at")));
        r.setOidcIdTokenMetadata(rs.getString("oidc_id_token_metadata"));
        r.setDeviceCodeValue(rs.getString("device_code_value"));
        r.setDeviceCodeIssuedAt(toLocal(rs.getTimestamp("device_code_issued_at")));
        r.setDeviceCodeExpiresAt(toLocal(rs.getTimestamp("device_code_expires_at")));
        r.setDeviceCodeMetadata(rs.getString("device_code_metadata"));
        r.setUserCodeValue(rs.getString("user_code_value"));
        r.setUserCodeIssuedAt(toLocal(rs.getTimestamp("user_code_issued_at")));
        r.setUserCodeExpiresAt(toLocal(rs.getTimestamp("user_code_expires_at")));
        r.setUserCodeMetadata(rs.getString("user_code_metadata"));
        return r;
    };

    public AuthorizationRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AuthorizationRecord> findByClientId(String registeredClientId) {
        return jdbcTemplate.query(
                "SELECT * FROM oauth2_authorization WHERE registered_client_id = ? " +
                "ORDER BY access_token_issued_at DESC", rowMapper, registeredClientId);
    }

    public List<AuthorizationRecord> findByPrincipal(String principalName) {
        return jdbcTemplate.query(
                "SELECT * FROM oauth2_authorization WHERE principal_name = ? " +
                "ORDER BY access_token_issued_at DESC", rowMapper, principalName);
    }

    public List<AuthorizationRecord> findByPrincipalAndClient(
            String principalName, String registeredClientId) {
        return jdbcTemplate.query(
                "SELECT * FROM oauth2_authorization " +
                "WHERE principal_name = ? AND registered_client_id = ? " +
                "ORDER BY access_token_issued_at DESC",
                rowMapper, principalName, registeredClientId);
    }

    public Optional<AuthorizationRecord> findByAccessTokenPrefix(String tokenPrefix) {
        List<AuthorizationRecord> list = jdbcTemplate.query(
                "SELECT * FROM oauth2_authorization WHERE access_token_value LIKE ? LIMIT 1",
                rowMapper, tokenPrefix + "%");
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<AuthorizationRecord> findByRefreshTokenPrefix(String tokenPrefix) {
        List<AuthorizationRecord> list = jdbcTemplate.query(
                "SELECT * FROM oauth2_authorization WHERE refresh_token_value LIKE ? LIMIT 1",
                rowMapper, tokenPrefix + "%");
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int countActiveTokensByClient(String registeredClientId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oauth2_authorization " +
                "WHERE registered_client_id = ? AND access_token_expires_at > NOW()",
                Long.class, registeredClientId);
        return count != null ? count.intValue() : 0;
    }

    public int countActiveTokensByPrincipal(String principalName) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oauth2_authorization " +
                "WHERE principal_name = ? AND access_token_expires_at > NOW()",
                Long.class, principalName);
        return count != null ? count.intValue() : 0;
    }

    public long countActiveTokens() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oauth2_authorization WHERE access_token_expires_at > NOW()",
                Long.class);
        return count != null ? count : 0L;
    }

    public int countAll() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oauth2_authorization", Long.class);
        return count != null ? count.intValue() : 0;
    }

    public int deleteById(String id) {
        return jdbcTemplate.update("DELETE FROM oauth2_authorization WHERE id = ?", id);
    }

    public int deleteByPrincipalAndClient(String principalName, String registeredClientId) {
        return jdbcTemplate.update(
                "DELETE FROM oauth2_authorization " +
                "WHERE principal_name = ? AND registered_client_id = ?",
                principalName, registeredClientId);
    }

    private static java.time.LocalDateTime toLocal(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }
}
