package com.liang.xz.server.repository;

import com.liang.xz.server.entity.UserEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * <p>用户 Repository —— 操作 sys_user 表</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<UserEntity> rowMapper = (rs, rowNum) -> {
        UserEntity u = new UserEntity();
        u.setId(rs.getLong("id"));
        u.setUsername(rs.getString("username"));
        u.setPassword(rs.getString("password"));
        u.setNickname(rs.getString("nickname"));
        u.setEmail(rs.getString("email"));
        u.setPhone(rs.getString("phone"));
        u.setUserType(rs.getString("user_type"));
        u.setTenantId(rs.getString("tenant_id"));
        u.setEnabled(rs.getBoolean("enabled"));
        u.setAccountNonExpired(rs.getBoolean("account_non_expired"));
        u.setAccountNonLocked(rs.getBoolean("account_non_locked"));
        u.setCredentialsNonExpired(rs.getBoolean("credentials_non_expired"));
        Timestamp createTime = rs.getTimestamp("create_time");
        if (createTime != null) u.setCreateTime(createTime.toLocalDateTime());
        Timestamp lastLogin = rs.getTimestamp("last_login_time");
        if (lastLogin != null) u.setLastLoginTime(lastLogin.toLocalDateTime());
        u.setLastLoginIp(rs.getString("last_login_ip"));
        return u;
    };

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ======================== 查询 ========================

    /** 按用户名查询（兼容旧数据） */
    public Optional<UserEntity> findByUsername(String username) {
        List<UserEntity> list = jdbcTemplate.query(
                "SELECT * FROM sys_user WHERE username = ?", rowMapper, username);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** 按手机号查询（主要登录方式） */
    public Optional<UserEntity> findByPhone(String phone) {
        List<UserEntity> list = jdbcTemplate.query(
                "SELECT * FROM sys_user WHERE phone = ? AND enabled = 1", rowMapper, phone);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** 按手机号查询（不限状态，用于 TokenIssuerService 全量校验） */
    public Optional<UserEntity> findByPhoneIncludeDisabled(String phone) {
        List<UserEntity> list = jdbcTemplate.query(
                "SELECT * FROM sys_user WHERE phone = ?", rowMapper, phone);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** 按用户名查询（不限状态，用于 TokenIssuerService 全量校验） */
    public Optional<UserEntity> findByUsernameIncludeDisabled(String username) {
        List<UserEntity> list = jdbcTemplate.query(
                "SELECT * FROM sys_user WHERE username = ? or phone = ?", rowMapper, username,username);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** 按主键查询 */
    public Optional<UserEntity> findById(Long id) {
        List<UserEntity> list = jdbcTemplate.query(
                "SELECT * FROM sys_user WHERE id = ?", rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** 查询所有用户 */
    public List<UserEntity> findAll() {
        return jdbcTemplate.query(
                "SELECT * FROM sys_user ORDER BY create_time DESC", rowMapper);
    }

    /** 统计用户总数 */
    public int count() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user", Long.class);
        return count != null ? count.intValue() : 0;
    }

    // ======================== 写入 ========================

    /** 插入新用户 */
    public int insert(UserEntity user) {
        return jdbcTemplate.update(
                "INSERT INTO sys_user (username, password, nickname, email, phone, " +
                "user_type, tenant_id, enabled, account_non_expired, account_non_locked, " +
                "credentials_non_expired, create_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                user.getUsername(), user.getPassword(), user.getNickname(),
                user.getEmail(), user.getPhone(), user.getUserType(),
                user.getTenantId(), user.getEnabled(),
                user.getAccountNonExpired(), user.getAccountNonLocked(),
                user.getCredentialsNonExpired());
    }

    // ======================== 更新 ========================

    /** 更新最后登录信息（按用户名） */
    @Deprecated
    public int updateLoginInfo(String username, String lastLoginIp) {
        return jdbcTemplate.update(
                "UPDATE sys_user SET last_login_time = NOW(), last_login_ip = ? WHERE username = ?",
                lastLoginIp, username);
    }

    /** 更新最后登录信息（按手机号） */
    public int updateLoginInfoByPhone(String phone, String lastLoginIp) {
        return jdbcTemplate.update(
                "UPDATE sys_user SET last_login_time = NOW(), last_login_ip = ? WHERE phone = ?",
                lastLoginIp, phone);
    }

    /** 更新最后登录信息（按用户名） */
    public int updateLoginInfoByUsername(String username, String lastLoginIp) {
        return jdbcTemplate.update(
                "UPDATE sys_user SET last_login_time = NOW(), last_login_ip = ? WHERE username = ?",
                lastLoginIp, username);
    }

    /** 更新账号启用状态 */
    public int updateEnabled(Long id, boolean enabled) {
        return jdbcTemplate.update(
                "UPDATE sys_user SET enabled = ? WHERE id = ?", enabled, id);
    }

    /** 更新密码 */
    public int updatePassword(Long id, String newPassword) {
        return jdbcTemplate.update(
                "UPDATE sys_user SET password = ? WHERE id = ?", newPassword, id);
    }

    /** 更新昵称 */
    public int updateNickname(Long id, String nickname) {
        return jdbcTemplate.update(
                "UPDATE sys_user SET nickname = ? WHERE id = ?", nickname, id);
    }

    /** 更新邮箱 */
    public int updateEmail(Long id, String email) {
        return jdbcTemplate.update(
                "UPDATE sys_user SET email = ? WHERE id = ?", email, id);
    }

    /** 更新手机号 */
    public int updatePhone(Long id, String phone) {
        return jdbcTemplate.update(
                "UPDATE sys_user SET phone = ? WHERE id = ?", phone, id);
    }

    /** 更新头像 */
    public int updateAvatar(Long id, String avatar) {
        return jdbcTemplate.update(
                "UPDATE sys_user SET avatar = ? WHERE id = ?", avatar, id);
    }

    /** 按主键删除用户 */
    public int deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", id);
    }
}
