package com.liang.xz.system.repository;

import com.liang.xz.system.entity.RoleEntity;
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
 * <p>角色 Repository —— 操作 sys_role 表</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Repository
public class RoleRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<RoleEntity> rowMapper = (rs, rowNum) -> {
        RoleEntity r = new RoleEntity();
        r.setId(rs.getLong("id"));
        r.setRoleCode(rs.getString("role_code"));
        r.setRoleName(rs.getString("role_name"));
        r.setDescription(rs.getString("description"));
        r.setSortOrder(rs.getInt("sort_order"));
        r.setEnabled(rs.getBoolean("enabled"));
        Timestamp ct = rs.getTimestamp("create_time");
        if (ct != null) r.setCreateTime(ct.toLocalDateTime());
        Timestamp ut = rs.getTimestamp("update_time");
        if (ut != null) r.setUpdateTime(ut.toLocalDateTime());
        return r;
    };

    public RoleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ======================== 查询 ========================

    public List<RoleEntity> findAll() {
        return jdbcTemplate.query(
                "SELECT * FROM sys_role ORDER BY sort_order ASC, id ASC", rowMapper);
    }

    /**
     * 分页查询角色，支持关键词和启用状态筛选
     */
    public List<RoleEntity> findPageByKeyword(String keyword, Boolean enabled, int offset, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM sys_role WHERE 1=1");
        List<Object> params = new java.util.ArrayList<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (role_code LIKE ? OR role_name LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
        }
        if (enabled != null) {
            sql.append(" AND enabled = ?");
            params.add(enabled);
        }

        sql.append(" ORDER BY sort_order ASC, id ASC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), rowMapper, params.toArray());
    }

    /**
     * 按关键词和启用状态统计角色总数
     */
    public long countByKeyword(String keyword, Boolean enabled) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM sys_role WHERE 1=1");
        List<Object> params = new java.util.ArrayList<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (role_code LIKE ? OR role_name LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
        }
        if (enabled != null) {
            sql.append(" AND enabled = ?");
            params.add(enabled);
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0;
    }

    public Optional<RoleEntity> findById(Long id) {
        List<RoleEntity> list = jdbcTemplate.query(
                "SELECT * FROM sys_role WHERE id = ?", rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<RoleEntity> findByRoleCode(String roleCode) {
        List<RoleEntity> list = jdbcTemplate.query(
                "SELECT * FROM sys_role WHERE role_code = ?", rowMapper, roleCode);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    // ======================== 写入 ========================

    public long insert(RoleEntity role) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO sys_role (role_code, role_name, description, sort_order, enabled, create_time, update_time) " +
                    "VALUES (?, ?, ?, ?, ?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, role.getRoleCode());
            ps.setString(2, role.getRoleName());
            ps.setString(3, role.getDescription());
            ps.setInt(4, role.getSortOrder() != null ? role.getSortOrder() : 0);
            ps.setBoolean(5, role.getEnabled() != null ? role.getEnabled() : true);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : 0;
    }

    public int update(RoleEntity role) {
        return jdbcTemplate.update(
                "UPDATE sys_role SET role_code=?, role_name=?, description=?, sort_order=?, update_time=NOW() WHERE id=?",
                role.getRoleCode(), role.getRoleName(), role.getDescription(),
                role.getSortOrder(), role.getId());
    }

    public int updateEnabled(Long id, boolean enabled) {
        return jdbcTemplate.update(
                "UPDATE sys_role SET enabled=?, update_time=NOW() WHERE id=?", enabled, id);
    }

    public int deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM sys_role WHERE id = ?", id);
    }

    // ======================== 角色-菜单关联 ========================

    public List<Long> findMenuIdsByRoleId(Long roleId) {
        return jdbcTemplate.queryForList(
                "SELECT menu_id FROM sys_role_menu WHERE role_id = ?", Long.class, roleId);
    }

    public void deleteRoleMenus(Long roleId) {
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id = ?", roleId);
    }

    public void insertRoleMenu(Long roleId, Long menuId) {
        jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?, ?)", roleId, menuId);
    }

    // ======================== 用户-角色关联 ========================

    public List<Long> findRoleIdsByUserId(Long userId) {
        return jdbcTemplate.queryForList(
                "SELECT role_id FROM sys_user_role WHERE user_id = ?", Long.class, userId);
    }

    /**
     * 查询拥有该角色的所有用户ID（缓存失效时定位受影响用户）
     */
    public List<Long> findUserIdsByRoleId(Long roleId) {
        return jdbcTemplate.queryForList(
                "SELECT user_id FROM sys_user_role WHERE role_id = ?", Long.class, roleId);
    }

    public List<String> findRoleCodesByUserId(Long userId) {
        return jdbcTemplate.queryForList(
                "SELECT r.role_code FROM sys_role r " +
                "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
                "WHERE ur.user_id = ? AND r.enabled = true",
                String.class, userId);
    }

    public List<String> findPermissionsByUserId(Long userId) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT m.permission FROM sys_menu m " +
                "INNER JOIN sys_role_menu rm ON m.id = rm.menu_id " +
                "INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
                "WHERE ur.user_id = ? AND m.enabled = true AND m.permission IS NOT NULL AND m.permission != ''",
                String.class, userId);
    }

    /**
     * 按用户名查询用户权限（当 JWT 未携带 user_id claim 时回退使用）
     */
    public List<String> findPermissionsByUsername(String username) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT m.permission FROM sys_menu m " +
                "INNER JOIN sys_role_menu rm ON m.id = rm.menu_id " +
                "INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
                "INNER JOIN sys_user u ON ur.user_id = u.id " +
                "WHERE u.username = ? AND m.enabled = true AND m.permission IS NOT NULL AND m.permission != ''",
                String.class, username);
    }

    public void deleteUserRoles(Long userId) {
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", userId);
    }

    public void insertUserRole(Long userId, Long roleId) {
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
    }
}
