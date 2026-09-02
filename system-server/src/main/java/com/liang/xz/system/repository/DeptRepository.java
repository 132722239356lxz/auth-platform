package com.liang.xz.system.repository;

import com.liang.xz.system.entity.DeptEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * <p>部门 Repository —— 操作 sys_dept 表</p>
 *
 * @author auth-platform
 * @since 1.2.0
 */
@Repository
public class DeptRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<DeptEntity> rowMapper = (rs, rowNum) -> {
        DeptEntity d = new DeptEntity();
        d.setId(rs.getLong("id"));
        d.setParentId(rs.getLong("parent_id"));
        d.setDeptName(rs.getString("dept_name"));
        d.setDeptCode(rs.getString("dept_code"));
        d.setLeader(rs.getString("leader"));
        d.setPhone(rs.getString("phone"));
        d.setEmail(rs.getString("email"));
        d.setSortOrder(rs.getInt("sort_order"));
        d.setEnabled(rs.getBoolean("enabled"));
        Timestamp createTime = rs.getTimestamp("create_time");
        if (createTime != null) d.setCreateTime(createTime.toLocalDateTime());
        Timestamp updateTime = rs.getTimestamp("update_time");
        if (updateTime != null) d.setUpdateTime(updateTime.toLocalDateTime());
        return d;
    };

    public DeptRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DeptEntity> findAll() {
        return jdbcTemplate.query("SELECT * FROM sys_dept ORDER BY sort_order, id", rowMapper);
    }

    public Optional<DeptEntity> findById(Long id) {
        List<DeptEntity> list = jdbcTemplate.query("SELECT * FROM sys_dept WHERE id = ?", rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<DeptEntity> findByCode(String deptCode) {
        List<DeptEntity> list = jdbcTemplate.query("SELECT * FROM sys_dept WHERE dept_code = ?", rowMapper, deptCode);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public boolean existsByCode(String deptCode) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_dept WHERE dept_code = ?", Long.class, deptCode);
        return count != null && count > 0;
    }

    public boolean existsById(Long id) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_dept WHERE id = ?", Long.class, id);
        return count != null && count > 0;
    }

    public int insert(DeptEntity dept) {
        return jdbcTemplate.update(
                "INSERT INTO sys_dept (parent_id, dept_name, dept_code, leader, phone, email, sort_order, enabled, create_time, update_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                dept.getParentId(), dept.getDeptName(), dept.getDeptCode(), dept.getLeader(),
                dept.getPhone(), dept.getEmail(), dept.getSortOrder(), dept.getEnabled());
    }

    public int update(DeptEntity dept) {
        return jdbcTemplate.update(
                "UPDATE sys_dept SET parent_id = ?, dept_name = ?, dept_code = ?, leader = ?, phone = ?, email = ?, sort_order = ?, enabled = ? " +
                "WHERE id = ?",
                dept.getParentId(), dept.getDeptName(), dept.getDeptCode(), dept.getLeader(),
                dept.getPhone(), dept.getEmail(), dept.getSortOrder(), dept.getEnabled(), dept.getId());
    }

    public int deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM sys_dept WHERE id = ?", id);
    }

    public int countChildren(Long id) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_dept WHERE parent_id = ?", Long.class, id);
        return count != null ? count.intValue() : 0;
    }

    public int countUsers(Long deptId) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user WHERE dept_id = ?", Long.class, deptId);
        return count != null ? count.intValue() : 0;
    }
}
