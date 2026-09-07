package com.liang.xz.system.repository;

import com.liang.xz.system.dto.UserPageQuery;
import com.liang.xz.system.entity.UserEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * <p>用户 Repository —— 操作 sys_user 表(system-server侧)</p>
 * <p>与auth-server中的UserRepository操作同一张表，但仅用于查询和用户管理</p>
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
        long deptId = rs.getLong("dept_id");
        u.setDeptId(rs.wasNull() ? null : deptId);
        u.setEnabled(rs.getBoolean("enabled"));
        u.setAccountNonExpired(rs.getBoolean("account_non_expired"));
        u.setAccountNonLocked(rs.getBoolean("account_non_locked"));
        u.setCredentialsNonExpired(rs.getBoolean("credentials_non_expired"));
        Timestamp createTime = rs.getTimestamp("create_time");
        if (createTime != null) u.setCreateTime(createTime.toLocalDateTime());
        Timestamp lastLogin = rs.getTimestamp("last_login_time");
        if (lastLogin != null) u.setLastLoginTime(lastLogin.toLocalDateTime());
        u.setLastLoginIp(rs.getString("last_login_ip"));
        u.setAvatar(rs.getString("avatar"));
        return u;
    };

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserEntity> findByUsername(String username) {
        List<UserEntity> list = jdbcTemplate.query(
                "SELECT * FROM sys_user WHERE username = ?", rowMapper, username);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** 按手机号查询（不限状态，兼容旧 JWT sub 为手机号的场景） */
    public Optional<UserEntity> findByPhone(String phone) {
        List<UserEntity> list = jdbcTemplate.query(
                "SELECT * FROM sys_user WHERE phone = ?", rowMapper, phone);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<UserEntity> findById(Long id) {
        List<UserEntity> list = jdbcTemplate.query(
                "SELECT * FROM sys_user WHERE id = ?", rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<UserEntity> findAll() {
        return jdbcTemplate.query(
                "SELECT * FROM sys_user ORDER BY create_time DESC", rowMapper);
    }

    public int updateEnabled(Long id, boolean enabled) {
        return jdbcTemplate.update("UPDATE sys_user SET enabled = ? WHERE id = ?", enabled, id);
    }

    public int updateUserType(Long id, String userType) {
        return jdbcTemplate.update("UPDATE sys_user SET user_type = ? WHERE id = ?", userType, id);
    }

    public int deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", id);
    }

    // ======================== 写入(用户注册) ========================

    /** 插入新用户，并回填数据库自增主键到实体 */
    public int insert(UserEntity user) {
        String sql = "INSERT INTO sys_user (username, password, nickname, email, phone, "
                + "user_type, tenant_id, dept_id, enabled, account_non_expired, account_non_locked, "
                + "credentials_non_expired, create_time) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int rows = jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[] {"id"});
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getNickname());
            ps.setString(4, user.getEmail());
            ps.setString(5, user.getPhone());
            ps.setString(6, user.getUserType());
            ps.setString(7, user.getTenantId());
            if (user.getDeptId() != null) {
                ps.setLong(8, user.getDeptId());
            } else {
                ps.setNull(8, java.sql.Types.BIGINT);
            }
            ps.setBoolean(9, user.getEnabled());
            ps.setBoolean(10, user.getAccountNonExpired());
            ps.setBoolean(11, user.getAccountNonLocked());
            ps.setBoolean(12, user.getCredentialsNonExpired());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) {
            user.setId(key.longValue());
        }
        return rows;
    }

    /** 更新用户信息 */
    public int update(UserEntity user) {
        return jdbcTemplate.update(
                "UPDATE sys_user SET username = ?, password = ?, nickname = ?, email = ?, " +
                "phone = ?, user_type = ?, dept_id = ?, enabled = ?, avatar = ? WHERE id = ?",
                user.getUsername(), user.getPassword(), user.getNickname(),
                user.getEmail(), user.getPhone(), user.getUserType(),
                user.getDeptId(), user.getEnabled(), user.getAvatar(), user.getId());
    }

    // ======================== 个人资料更新 ========================

    /** 更新最后登录信息 */
    public int updateLoginInfo(String username, String lastLoginIp) {
        return jdbcTemplate.update(
                "UPDATE sys_user SET last_login_time = NOW(), last_login_ip = ? WHERE username = ?",
                lastLoginIp, username);
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

    /** 统计用户总数 */
    public int count() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user", Long.class);
        return count != null ? count.intValue() : 0;
    }

    // ======================== 动态条件分页查询 ========================

    /**
     * 根据动态条件分页查询用户
     */
    public List<UserEntity> findPageByQuery(UserPageQuery query) {
        StringBuilder sql = new StringBuilder("SELECT * FROM sys_user");
        List<Object> params = new ArrayList<>();
        String where = buildWhereClause(query, params);
        sql.append(where).append(" ORDER BY create_time DESC LIMIT ? OFFSET ?");
        params.add(query.getPageSize());
        params.add((query.getPage() - 1) * query.getPageSize());
        return jdbcTemplate.query(sql.toString(), rowMapper, params.toArray());
    }

    /**
     * 根据动态条件统计用户数量
     */
    public long countByQuery(UserPageQuery query) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM sys_user");
        List<Object> params = new ArrayList<>();
        sql.append(buildWhereClause(query, params));
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0;
    }

    /**
     * 兼容旧方法：按关键词搜索并分页查询
     */
    public List<UserEntity> findPageByKeyword(String keyword, int offset, int limit) {
        UserPageQuery query = new UserPageQuery();
        query.setKeyword(keyword);
        query.setPage(offset / limit + 1);
        query.setPageSize(limit);
        return findPageByQuery(query);
    }

    /**
     * 兼容旧方法：按关键词统计数量
     */
    public long countByKeyword(String keyword) {
        UserPageQuery query = new UserPageQuery();
        query.setKeyword(keyword);
        return countByQuery(query);
    }

    // ======================== 辅助 ========================

    private String buildWhereClause(UserPageQuery query, List<Object> params) {
        StringBuilder where = new StringBuilder();
        if (query.getKeyword() != null && !query.getKeyword().trim().isEmpty()) {
            where.append(where.length() == 0 ? " WHERE " : " AND ");
            where.append("(username LIKE ? OR nickname LIKE ? OR email LIKE ?)");
            String like = "%" + query.getKeyword().trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (query.getEnabled() != null) {
            where.append(where.length() == 0 ? " WHERE " : " AND ");
            where.append("enabled = ?");
            params.add(query.getEnabled() ? 1 : 0);
        }
        if (query.getUserType() != null && !query.getUserType().trim().isEmpty()) {
            where.append(where.length() == 0 ? " WHERE " : " AND ");
            where.append("user_type = ?");
            params.add(query.getUserType().trim());
        }
        if (query.getDeptId() != null) {
            where.append(where.length() == 0 ? " WHERE " : " AND ");
            where.append("dept_id = ?");
            params.add(query.getDeptId());
        }
        if (query.getRoleId() != null) {
            where.append(where.length() == 0 ? " WHERE " : " AND ");
            where.append("id IN (SELECT user_id FROM sys_user_role WHERE role_id = ?)");
            params.add(query.getRoleId());
        }
        return where.toString();
    }
}
