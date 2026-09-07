package com.liang.xz.system.repository;

import com.liang.xz.system.entity.MenuEntity;
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
 * <p>菜单 Repository —— 操作 sys_menu 表</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Repository
public class MenuRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<MenuEntity> rowMapper = (rs, rowNum) -> {
        MenuEntity m = new MenuEntity();
        m.setId(rs.getLong("id"));
        m.setParentId(rs.getLong("parent_id"));
        m.setMenuName(rs.getString("menu_name"));
        m.setMenuType(rs.getInt("menu_type"));
        m.setPath(rs.getString("path"));
        m.setComponent(rs.getString("component"));
        m.setPermission(rs.getString("permission"));
        m.setIcon(rs.getString("icon"));
        m.setSortOrder(rs.getInt("sort_order"));
        m.setEnabled(rs.getBoolean("enabled"));
        m.setIsFrame(rs.getBoolean("is_frame"));
        Timestamp ct = rs.getTimestamp("create_time");
        if (ct != null) m.setCreateTime(ct.toLocalDateTime());
        Timestamp ut = rs.getTimestamp("update_time");
        if (ut != null) m.setUpdateTime(ut.toLocalDateTime());
        return m;
    };

    public MenuRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ======================== 查询 ========================

    /** 查询所有菜单(按排序号排序) */
    public List<MenuEntity> findAll() {
        return jdbcTemplate.query(
                "SELECT * FROM sys_menu ORDER BY parent_id ASC, sort_order ASC, id ASC", rowMapper);
    }

    /** 按父菜单ID查询子菜单 */
    public List<MenuEntity> findByParentId(Long parentId) {
        return jdbcTemplate.query(
                "SELECT * FROM sys_menu WHERE parent_id = ? ORDER BY sort_order ASC, id ASC",
                rowMapper, parentId);
    }

    public Optional<MenuEntity> findById(Long id) {
        List<MenuEntity> list = jdbcTemplate.query(
                "SELECT * FROM sys_menu WHERE id = ?", rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    // ======================== 写入 ========================

    public long insert(MenuEntity menu) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, " +
                    "permission, icon, sort_order, enabled, is_frame, create_time, update_time) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, menu.getParentId() != null ? menu.getParentId() : 0);
            ps.setString(2, menu.getMenuName());
            ps.setInt(3, menu.getMenuType() != null ? menu.getMenuType() : 1);
            ps.setString(4, menu.getPath());
            ps.setString(5, menu.getComponent());
            ps.setString(6, menu.getPermission());
            ps.setString(7, menu.getIcon());
            ps.setInt(8, menu.getSortOrder() != null ? menu.getSortOrder() : 0);
            ps.setBoolean(9, menu.getEnabled() != null ? menu.getEnabled() : true);
            ps.setBoolean(10, menu.getIsFrame() != null ? menu.getIsFrame() : false);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : 0;
    }

    public int update(MenuEntity menu) {
        return jdbcTemplate.update(
                "UPDATE sys_menu SET parent_id=?, menu_name=?, menu_type=?, path=?, component=?, " +
                "permission=?, icon=?, sort_order=?, is_frame=?, update_time=NOW() WHERE id=?",
                menu.getParentId(), menu.getMenuName(), menu.getMenuType(), menu.getPath(),
                menu.getComponent(), menu.getPermission(), menu.getIcon(), menu.getSortOrder(),
                menu.getIsFrame(), menu.getId());
    }

    public int updateEnabled(Long id, boolean enabled) {
        return jdbcTemplate.update(
                "UPDATE sys_menu SET enabled=?, update_time=NOW() WHERE id=?", enabled, id);
    }

    public int deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM sys_menu WHERE id = ?", id);
    }

    /** 删除指定菜单的所有角色关联 */
    public int deleteRoleMenuByMenuId(Long menuId) {
        return jdbcTemplate.update("DELETE FROM sys_role_menu WHERE menu_id = ?", menuId);
    }

    /** 插入角色-菜单关联(新菜单自动分配) */
    public void insertRoleMenu(Long roleId, Long menuId) {
        jdbcTemplate.update("INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (?, ?)", roleId, menuId);
    }
}
