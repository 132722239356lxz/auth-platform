package com.liang.xz.flow.repository;

import com.liang.xz.flow.entity.WorkflowForm;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * <p>审批表单 Repository</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Repository
public class WorkflowFormRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public WorkflowFormRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private final RowMapper<WorkflowForm> mapper = (rs, rn) -> WorkflowForm.builder()
            .id(rs.getLong("id"))
            .formKey(rs.getString("form_key"))
            .formName(rs.getString("form_name"))
            .definitionKey(rs.getString("definition_key"))
            .schemaJson(rs.getString("schema_json"))
            .icon(rs.getString("icon"))
            .sortOrder(rs.getObject("sort_order") != null ? rs.getInt("sort_order") : 0)
            .status(rs.getObject("status") != null ? rs.getInt("status") : 1)
            .applyType(rs.getString("apply_type"))
            .createdBy(rs.getString("created_by"))
            .createTime(toLdt(rs.getTimestamp("create_time")))
            .updateTime(toLdt(rs.getTimestamp("update_time")))
            .build();

    public Optional<WorkflowForm> findByKey(String formKey) {
        List<WorkflowForm> list = jdbcTemplate.query(
                "SELECT * FROM wf_form WHERE form_key = ? AND status = 1", mapper, formKey);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<WorkflowForm> findAllEnabled() {
        return jdbcTemplate.query(
                "SELECT * FROM wf_form WHERE status = 1 ORDER BY sort_order ASC, create_time DESC", mapper);
    }

    public List<WorkflowForm> findAll() {
        return jdbcTemplate.query(
                "SELECT * FROM wf_form ORDER BY sort_order ASC, create_time DESC", mapper);
    }

    /**
     * 维护页条件查询（含停用数据）。所有条件为空时等价于 {@link #findAll()}。
     *
     * @param formKey       表单Key 模糊匹配
     * @param formName      表单名称 模糊匹配
     * @param definitionKey 绑定流程Key 精确匹配
     * @param applyType     业务类型 模糊匹配
     * @param status        状态精确匹配，null 表示不限制
     */
    public List<WorkflowForm> findByCondition(String formKey, String formName,
            String definitionKey, String applyType, Integer status) {
        StringBuilder sql = new StringBuilder("SELECT * FROM wf_form WHERE 1 = 1");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (formKey != null && !formKey.isBlank()) {
            sql.append(" AND form_key LIKE :formKey");
            params.addValue("formKey", "%" + formKey + "%");
        }
        if (formName != null && !formName.isBlank()) {
            sql.append(" AND form_name LIKE :formName");
            params.addValue("formName", "%" + formName + "%");
        }
        if (definitionKey != null && !definitionKey.isBlank()) {
            sql.append(" AND definition_key = :definitionKey");
            params.addValue("definitionKey", definitionKey);
        }
        if (applyType != null && !applyType.isBlank()) {
            sql.append(" AND apply_type LIKE :applyType");
            params.addValue("applyType", "%" + applyType + "%");
        }
        if (status != null) {
            sql.append(" AND status = :status");
            params.addValue("status", status);
        }
        sql.append(" ORDER BY sort_order ASC, create_time DESC");
        return namedJdbcTemplate.query(sql.toString(), params, mapper);
    }

    public WorkflowForm save(WorkflowForm form) {
        jdbcTemplate.update(
                "INSERT INTO wf_form(form_key, form_name, definition_key, schema_json, icon, sort_order, status, apply_type, created_by, create_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                form.getFormKey(), form.getFormName(), form.getDefinitionKey(), form.getSchemaJson(),
                form.getIcon(), form.getSortOrder(), form.getStatus(), form.getApplyType(), form.getCreatedBy(),
                Timestamp.valueOf(LocalDateTime.now()));
        return findByKey(form.getFormKey()).orElse(form);
    }

    public WorkflowForm updateByKey(String formKey, WorkflowForm form) {
        jdbcTemplate.update(
                "UPDATE wf_form SET form_name = ?, definition_key = ?, schema_json = ?, icon = ?, sort_order = ?, "
                        + "apply_type = ?, update_time = ? WHERE form_key = ?",
                form.getFormName(), form.getDefinitionKey(), form.getSchemaJson(), form.getIcon(),
                form.getSortOrder(), form.getApplyType(), Timestamp.valueOf(LocalDateTime.now()), formKey);
        return findByKey(formKey).orElse(form);
    }

    public int updateStatus(String formKey, Integer status) {
        return jdbcTemplate.update(
                "UPDATE wf_form SET status = ?, update_time = ? WHERE form_key = ?",
                status, Timestamp.valueOf(LocalDateTime.now()), formKey);
    }

    public int deleteByKey(String formKey) {
        return jdbcTemplate.update("DELETE FROM wf_form WHERE form_key = ?", formKey);
    }

    private LocalDateTime toLdt(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }
}
