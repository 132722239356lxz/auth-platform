package com.liang.xz.system.repository;

import com.liang.xz.system.entity.DictDataEntity;
import com.liang.xz.system.entity.DictTypeEntity;
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
 * <p>数据字典 Repository —— 操作 sys_dict_type 和 sys_dict_data 表</p>
 *
 * @author auth-platform
 * @since 1.2.0
 */
@Repository
public class DictRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<DictTypeEntity> typeRowMapper = (rs, rowNum) -> {
        DictTypeEntity t = new DictTypeEntity();
        t.setId(rs.getLong("id"));
        t.setDictName(rs.getString("dict_name"));
        t.setDictType(rs.getString("dict_type"));
        t.setDescription(rs.getString("description"));
        t.setEnabled(rs.getBoolean("enabled"));
        Timestamp ct = rs.getTimestamp("create_time");
        if (ct != null) t.setCreateTime(ct.toLocalDateTime());
        Timestamp ut = rs.getTimestamp("update_time");
        if (ut != null) t.setUpdateTime(ut.toLocalDateTime());
        return t;
    };

    private final RowMapper<DictDataEntity> dataRowMapper = (rs, rowNum) -> {
        DictDataEntity d = new DictDataEntity();
        d.setId(rs.getLong("id"));
        d.setTypeId(rs.getLong("type_id"));
        d.setDictLabel(rs.getString("dict_label"));
        d.setDictValue(rs.getString("dict_value"));
        d.setSortOrder(rs.getInt("sort_order"));
        d.setCssClass(rs.getString("css_class"));
        d.setListClass(rs.getString("list_class"));
        d.setEnabled(rs.getBoolean("enabled"));
        d.setRemark(rs.getString("remark"));
        Timestamp ct = rs.getTimestamp("create_time");
        if (ct != null) d.setCreateTime(ct.toLocalDateTime());
        Timestamp ut = rs.getTimestamp("update_time");
        if (ut != null) d.setUpdateTime(ut.toLocalDateTime());
        return d;
    };

    public DictRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ======================== 字典类型 ========================

    public List<DictTypeEntity> findAllTypes() {
        return jdbcTemplate.query(
                "SELECT * FROM sys_dict_type ORDER BY id ASC", typeRowMapper);
    }

    public Optional<DictTypeEntity> findTypeById(Long id) {
        List<DictTypeEntity> list = jdbcTemplate.query(
                "SELECT * FROM sys_dict_type WHERE id = ?", typeRowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<DictTypeEntity> findTypeByDictType(String dictType) {
        List<DictTypeEntity> list = jdbcTemplate.query(
                "SELECT * FROM sys_dict_type WHERE dict_type = ?", typeRowMapper, dictType);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public long insertType(DictTypeEntity entity) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO sys_dict_type (dict_name, dict_type, description, enabled, create_time, update_time) " +
                    "VALUES (?, ?, ?, ?, NOW(), NOW())", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, entity.getDictName());
            ps.setString(2, entity.getDictType());
            ps.setString(3, entity.getDescription());
            ps.setBoolean(4, entity.getEnabled() != null ? entity.getEnabled() : true);
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key != null ? key.longValue() : 0;
    }

    public int updateType(DictTypeEntity entity) {
        return jdbcTemplate.update(
                "UPDATE sys_dict_type SET dict_name=?, dict_type=?, description=?, update_time=NOW() WHERE id=?",
                entity.getDictName(), entity.getDictType(), entity.getDescription(), entity.getId());
    }

    public int deleteTypeById(Long id) {
        return jdbcTemplate.update("DELETE FROM sys_dict_type WHERE id = ?", id);
    }

    // ======================== 字典数据 ========================

    public List<DictDataEntity> findDataByTypeId(Long typeId) {
        return jdbcTemplate.query(
                "SELECT * FROM sys_dict_data WHERE type_id = ? ORDER BY sort_order ASC, id ASC",
                dataRowMapper, typeId);
    }

    public List<DictDataEntity> findDataByDictType(String dictType) {
        return jdbcTemplate.query(
                "SELECT d.* FROM sys_dict_data d " +
                "INNER JOIN sys_dict_type t ON d.type_id = t.id " +
                "WHERE t.dict_type = ? ORDER BY d.sort_order ASC, d.id ASC",
                dataRowMapper, dictType);
    }

    public Optional<DictDataEntity> findDataById(Long id) {
        List<DictDataEntity> list = jdbcTemplate.query(
                "SELECT * FROM sys_dict_data WHERE id = ?", dataRowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public long insertData(DictDataEntity entity) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO sys_dict_data (type_id, dict_label, dict_value, sort_order, " +
                    "css_class, list_class, enabled, remark, create_time, update_time) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, entity.getTypeId());
            ps.setString(2, entity.getDictLabel());
            ps.setString(3, entity.getDictValue());
            ps.setInt(4, entity.getSortOrder() != null ? entity.getSortOrder() : 0);
            ps.setString(5, entity.getCssClass());
            ps.setString(6, entity.getListClass());
            ps.setBoolean(7, entity.getEnabled() != null ? entity.getEnabled() : true);
            ps.setString(8, entity.getRemark());
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key != null ? key.longValue() : 0;
    }

    public int updateData(DictDataEntity entity) {
        return jdbcTemplate.update(
                "UPDATE sys_dict_data SET type_id=?, dict_label=?, dict_value=?, sort_order=?, " +
                "css_class=?, list_class=?, remark=?, update_time=NOW() WHERE id=?",
                entity.getTypeId(), entity.getDictLabel(), entity.getDictValue(),
                entity.getSortOrder(), entity.getCssClass(), entity.getListClass(),
                entity.getRemark(), entity.getId());
    }

    public int deleteDataById(Long id) {
        return jdbcTemplate.update("DELETE FROM sys_dict_data WHERE id = ?", id);
    }

    public int deleteDataByTypeId(Long typeId) {
        return jdbcTemplate.update("DELETE FROM sys_dict_data WHERE type_id = ?", typeId);
    }
}
