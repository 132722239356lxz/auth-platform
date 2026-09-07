package com.liang.xz.flow.repository;

import com.liang.xz.flow.entity.*;
import lombok.Builder;
import lombok.Data;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 工作流 Repository —— 六字段节点支持
 */
@Repository
public class WorkflowRepository {

    private final JdbcTemplate jdbcTemplate;

    public WorkflowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ======================== WorkflowDefinition ========================

    private final RowMapper<WorkflowDefinition> defMapper = (rs, rn) -> WorkflowDefinition.builder()
            .id(rs.getLong("id"))
            .definitionKey(rs.getString("definition_key"))
            .definitionName(rs.getString("definition_name"))
            .description(rs.getString("description"))
            .category(rs.getString("category"))
            .version(rs.getInt("version"))
            .status(rs.getInt("status"))
            .createdBy(rs.getString("created_by"))
            .createTime(toLdt(rs.getTimestamp("create_time")))
            .updateTime(toLdt(rs.getTimestamp("update_time")))
            .build();

    public Long saveDefinition(WorkflowDefinition def) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO wf_definition(definition_key,definition_name,description,category,version,status,created_by,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, def.getDefinitionKey());
            ps.setString(2, def.getDefinitionName());
            ps.setString(3, def.getDescription());
            ps.setString(4, def.getCategory());
            ps.setInt(5, def.getVersion() != null ? def.getVersion() : 1);
            ps.setInt(6, def.getStatus() != null ? def.getStatus() : 1);
            ps.setString(7, def.getCreatedBy());
            ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            ps.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public Optional<WorkflowDefinition> findDefinitionByKey(String key) {
        List<WorkflowDefinition> list = jdbcTemplate.query(
                "SELECT * FROM wf_definition WHERE definition_key = ? AND status = 1 ORDER BY version DESC LIMIT 1",
                defMapper, key);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<WorkflowDefinition> findDefinitionById(Long id) {
        List<WorkflowDefinition> list = jdbcTemplate.query(
                "SELECT * FROM wf_definition WHERE id = ?", defMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<WorkflowDefinition> findAllDefinitions() {
        return jdbcTemplate.query("SELECT * FROM wf_definition ORDER BY create_time DESC", defMapper);
    }

    public void updateDefinition(Long id, String definitionName, String description, String category) {
        jdbcTemplate.update(
                "UPDATE wf_definition SET definition_name=?, description=?, category=?, update_time=? WHERE id=?",
                definitionName, description, category, Timestamp.valueOf(LocalDateTime.now()), id);
    }

    public void deleteDefinition(Long id) {
        jdbcTemplate.update("DELETE FROM wf_node WHERE definition_id = ?", id);
        jdbcTemplate.update("DELETE FROM wf_definition WHERE id = ?", id);
    }

    public void updateDefinitionStatus(Long id, int status) {
        jdbcTemplate.update(
                "UPDATE wf_definition SET status=?, update_time=? WHERE id=?",
                status, Timestamp.valueOf(LocalDateTime.now()), id);
    }

    // ======================== WorkflowNode (支持 execMode/parallelGroup/parentNodeId/conditionExpression/onConditionFail) ========================

    private final RowMapper<WorkflowNode> nodeMapper = (rs, rn) -> WorkflowNode.builder()
            .id(rs.getLong("id"))
            .definitionId(rs.getLong("definition_id"))
            .nodeName(rs.getString("node_name"))
            .nodeType(rs.getString("node_type"))
            .execMode(rs.getString("exec_mode"))
            .parallelGroup(rs.getString("parallel_group"))
            .parentNodeId(rs.getObject("parent_node_id") != null ? rs.getLong("parent_node_id") : null)
            .conditionExpression(rs.getString("condition_expression"))
            .onConditionFail(rs.getString("on_condition_fail"))
            .approverStrategy(rs.getString("approver_strategy"))
            .approvers(rs.getString("approvers"))
            .approverRole(rs.getString("approver_role"))
            .sortOrder(rs.getInt("sort_order"))
            .timeoutHours(rs.getObject("timeout_hours") != null ? rs.getInt("timeout_hours") : null)
            .countersign(rs.getBoolean("countersign"))
            .rejectStrategy(rs.getString("reject_strategy"))
            .createTime(toLdt(rs.getTimestamp("create_time")))
            .build();

    public Long saveNode(WorkflowNode node) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO wf_node(definition_id,node_name,node_type,exec_mode,parallel_group,parent_node_id,condition_expression,on_condition_fail,approver_strategy,approvers,approver_role,sort_order,timeout_hours,countersign,reject_strategy,create_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, node.getDefinitionId());
            ps.setString(2, node.getNodeName());
            ps.setString(3, node.getNodeType());
            ps.setString(4, node.getExecMode() != null ? node.getExecMode() : "SERIAL");
            ps.setString(5, node.getParallelGroup());
            ps.setObject(6, node.getParentNodeId());
            ps.setString(7, node.getConditionExpression());
            ps.setString(8, node.getOnConditionFail() != null ? node.getOnConditionFail() : "REJECT");
            ps.setString(9, node.getApproverStrategy());
            ps.setString(10, node.getApprovers());
            ps.setString(11, node.getApproverRole());
            ps.setInt(12, node.getSortOrder() != null ? node.getSortOrder() : 0);
            ps.setObject(13, node.getTimeoutHours());
            ps.setBoolean(14, node.getCountersign() != null ? node.getCountersign() : false);
            ps.setString(15, node.getRejectStrategy());
            ps.setTimestamp(16, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public List<WorkflowNode> findNodesByDefinitionId(Long definitionId) {
        return jdbcTemplate.query(
                "SELECT * FROM wf_node WHERE definition_id = ? ORDER BY sort_order ASC",
                nodeMapper, definitionId);
    }

    public Optional<WorkflowNode> findNodeById(Long nodeId) {
        List<WorkflowNode> list = jdbcTemplate.query(
                "SELECT * FROM wf_node WHERE id = ?", nodeMapper, nodeId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void deleteNodesByDefinitionId(Long definitionId) {
        jdbcTemplate.update("DELETE FROM wf_node WHERE definition_id = ?", definitionId);
    }

    // ======================== WorkflowInstance ========================

    private final RowMapper<WorkflowInstance> instMapper = (rs, rn) -> WorkflowInstance.builder()
            .id(rs.getLong("id"))
            .definitionId(rs.getLong("definition_id"))
            .title(rs.getString("title"))
            .applicant(rs.getString("applicant"))
            .applyContent(rs.getString("apply_content"))
            .status(rs.getString("status"))
            .currentNodeId(rs.getObject("current_node_id") != null ? rs.getLong("current_node_id") : null)
            .currentApprover(rs.getString("current_approver"))
            .approvalChain(rs.getString("approval_chain"))
            .approvalRecords(rs.getString("approval_records"))
            .createdBy(rs.getString("created_by"))
            .createTime(toLdt(rs.getTimestamp("create_time")))
            .updateTime(toLdt(rs.getTimestamp("update_time")))
            .finishTime(toLdt(rs.getTimestamp("finish_time")))
            .build();

    public Long saveInstance(WorkflowInstance instance) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO wf_instance(definition_id,title,applicant,apply_content,status,current_node_id,current_approver,approval_chain,approval_records,created_by,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, instance.getDefinitionId());
            ps.setString(2, instance.getTitle());
            ps.setString(3, instance.getApplicant());
            ps.setString(4, instance.getApplyContent());
            ps.setString(5, instance.getStatus());
            ps.setObject(6, instance.getCurrentNodeId());
            ps.setString(7, instance.getCurrentApprover());
            ps.setString(8, instance.getApprovalChain());
            ps.setString(9, instance.getApprovalRecords());
            ps.setString(10, instance.getCreatedBy());
            ps.setTimestamp(11, Timestamp.valueOf(LocalDateTime.now()));
            ps.setTimestamp(12, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void updateInstanceStatus(Long instanceId, String status, Long currentNodeId,
                                      String currentApprover, String approvalRecords) {
        jdbcTemplate.update(
                "UPDATE wf_instance SET status=?, current_node_id=?, current_approver=?, " +
                "approval_records=?, update_time=?, finish_time=? WHERE id=?",
                status, currentNodeId, currentApprover,
                approvalRecords,
                Timestamp.valueOf(LocalDateTime.now()),
                isFinalStatus(status) ? Timestamp.valueOf(LocalDateTime.now()) : null,
                instanceId);
    }

    public Optional<WorkflowInstance> findInstanceById(Long id) {
        List<WorkflowInstance> list = jdbcTemplate.query(
                "SELECT * FROM wf_instance WHERE id = ?", instMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<WorkflowInstance> findInstancesByApplicant(String applicant) {
        return jdbcTemplate.query(
                "SELECT * FROM wf_instance WHERE applicant = ? ORDER BY create_time DESC",
                instMapper, applicant);
    }

    public List<WorkflowInstance> findPendingInstancesByApprover(String approver) {
        return jdbcTemplate.query(
                "SELECT i.* FROM wf_instance i INNER JOIN wf_task t ON i.id = t.instance_id " +
                "WHERE t.approver = ? AND t.status = 'PENDING' AND i.status = 'PENDING' " +
                "ORDER BY i.create_time DESC",
                instMapper, approver);
    }

    public long countPendingInstances() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT i.id) FROM wf_instance i " +
                "INNER JOIN wf_task t ON i.id = t.instance_id " +
                "WHERE t.status = 'PENDING' AND i.status = 'PENDING'",
                Long.class);
        return count != null ? count : 0;
    }

    // ======================== WorkflowTask ========================

    private final RowMapper<WorkflowTask> taskMapper = (rs, rn) -> WorkflowTask.builder()
            .id(rs.getLong("id"))
            .instanceId(rs.getLong("instance_id"))
            .nodeId(rs.getLong("node_id"))
            .nodeName(rs.getString("node_name"))
            .approver(rs.getString("approver"))
            .status(rs.getString("status"))
            .comment(rs.getString("comment"))
            .approveTime(toLdt(rs.getTimestamp("approve_time")))
            .transferredFrom(rs.getString("transferred_from"))
            .createTime(toLdt(rs.getTimestamp("create_time")))
            .updateTime(toLdt(rs.getTimestamp("update_time")))
            .build();

    public Long saveTask(WorkflowTask task) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO wf_task(instance_id,node_id,node_name,approver,status,comment,approve_time,transferred_from,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, task.getInstanceId());
            ps.setLong(2, task.getNodeId());
            ps.setString(3, task.getNodeName());
            ps.setString(4, task.getApprover());
            ps.setString(5, task.getStatus() != null ? task.getStatus() : "PENDING");
            ps.setString(6, task.getComment());
            ps.setTimestamp(7, task.getApproveTime() != null ? Timestamp.valueOf(task.getApproveTime()) : null);
            ps.setString(8, task.getTransferredFrom());
            ps.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
            ps.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void updateTaskStatus(Long taskId, String status, String comment) {
        jdbcTemplate.update(
                "UPDATE wf_task SET status=?, comment=?, approve_time=?, update_time=? WHERE id=?",
                status, comment, Timestamp.valueOf(LocalDateTime.now()), Timestamp.valueOf(LocalDateTime.now()), taskId);
    }

    public Optional<WorkflowTask> findTaskById(Long taskId) {
        List<WorkflowTask> list = jdbcTemplate.query(
                "SELECT * FROM wf_task WHERE id = ?", taskMapper, taskId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<WorkflowTask> findTasksByInstanceId(Long instanceId) {
        return jdbcTemplate.query(
                "SELECT * FROM wf_task WHERE instance_id = ? ORDER BY create_time ASC",
                taskMapper, instanceId);
    }

    public List<WorkflowTask> findPendingTasksByApprover(String approver) {
        return jdbcTemplate.query(
                "SELECT * FROM wf_task WHERE approver = ? AND status = 'PENDING' ORDER BY create_time DESC",
                taskMapper, approver);
    }

    public List<ApprovalRecordProjection> findApprovalRecordsByApprover(String approver) {
        return jdbcTemplate.query(
                "SELECT t.id as task_id, t.instance_id, t.node_name, t.approver, t.status, t.comment, " +
                "t.approve_time, i.title, i.applicant, i.status as instance_status " +
                "FROM wf_task t INNER JOIN wf_instance i ON t.instance_id = i.id " +
                "WHERE t.approver = ? AND t.status != 'PENDING' " +
                "ORDER BY t.approve_time DESC, t.create_time DESC",
                (rs, rn) -> ApprovalRecordProjection.builder()
                        .taskId(rs.getLong("task_id"))
                        .instanceId(rs.getLong("instance_id"))
                        .nodeName(rs.getString("node_name"))
                        .approver(rs.getString("approver"))
                        .status(rs.getString("status"))
                        .comment(rs.getString("comment"))
                        .approveTime(toLdt(rs.getTimestamp("approve_time")))
                        .title(rs.getString("title"))
                        .applicant(rs.getString("applicant"))
                        .instanceStatus(rs.getString("instance_status"))
                        .build(), approver);
    }

    @Data
    @Builder
    public static class ApprovalRecordProjection {
        private Long taskId;
        private Long instanceId;
        private String nodeName;
        private String approver;
        private String status;
        private String comment;
        private LocalDateTime approveTime;
        private String title;
        private String applicant;
        private String instanceStatus;
    }

    // ======================== 工具方法 ========================

    private LocalDateTime toLdt(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }

    private boolean isFinalStatus(String status) {
        return "APPROVED".equals(status) || "REJECTED".equals(status) || "WITHDRAWN".equals(status);
    }
}
