package com.liang.xz.flow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.liang.xz.common.core.redis.RedisHelper;
import com.liang.xz.flow.dto.ApprovalRequest;
import com.liang.xz.flow.dto.WorkflowCreateRequest;
import com.liang.xz.flow.dto.WorkflowVO;
import com.liang.xz.flow.engine.WorkflowEngine;
import com.liang.xz.flow.entity.*;
import com.liang.xz.flow.enums.ApprovalAction;
import com.liang.xz.flow.enums.WorkflowStatus;
import com.liang.xz.flow.entity.WorkflowForm;
import com.liang.xz.flow.repository.WorkflowFormRepository;
import com.liang.xz.flow.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 工作流审批核心服务(集成 WorkflowEngine 支持串行/并行/条件分支)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowFormRepository workflowFormRepository;
    private final WorkflowEngine workflowEngine;
    private final RedisHelper redisHelper;
    private final PermissionGrantService permissionGrantService;
    private final ObjectMapper objectMapper;

    // ======================== 1. 工作流定义管理 ========================

    @Transactional
    public Long createDefinition(WorkflowCreateRequest request, String createdBy) {
        Optional<WorkflowDefinition> existDef = workflowRepository.findDefinitionByKey(request.getDefinitionKey());
        int version = existDef.map(d -> d.getVersion() + 1).orElse(1);

        WorkflowDefinition def = WorkflowDefinition.builder()
                .definitionKey(request.getDefinitionKey())
                .definitionName(request.getDefinitionName())
                .description(request.getDescription())
                .category(request.getCategory())
                .version(version)
                .status(1)
                .createdBy(createdBy)
                .build();
        Long defId = workflowRepository.saveDefinition(def);

        // 保存节点(支持新字段)
        int sort = 0;
        for (WorkflowCreateRequest.NodeRequest nodeReq : request.getNodes()) {
            WorkflowNode node = WorkflowNode.builder()
                    .definitionId(defId)
                    .nodeName(nodeReq.getNodeName())
                    .nodeType(nodeReq.getNodeType() != null ? nodeReq.getNodeType() : "APPROVAL")
                    .execMode(nodeReq.getExecMode() != null ? nodeReq.getExecMode() : "SERIAL")
                    .parallelGroup(nodeReq.getParallelGroup())
                    .parentNodeId(nodeReq.getParentNodeId())
                    .conditionExpression(nodeReq.getConditionExpression())
                    .onConditionFail(nodeReq.getOnConditionFail() != null ? nodeReq.getOnConditionFail() : "REJECT")
                    .approverStrategy(nodeReq.getApproverStrategy() != null ? nodeReq.getApproverStrategy() : "SPECIFIC")
                    .approvers(nodeReq.getApprovers())
                    .approverRole(nodeReq.getApproverRole())
                    .sortOrder(nodeReq.getSortOrder() != null ? nodeReq.getSortOrder() : sort++)
                    .timeoutHours(nodeReq.getTimeoutHours())
                    .countersign(nodeReq.getCountersign() != null ? nodeReq.getCountersign() : false)
                    .rejectStrategy(nodeReq.getRejectStrategy() != null ? nodeReq.getRejectStrategy() : "TO_PREV")
                    .build();
            workflowRepository.saveNode(node);
        }
        log.info("[Workflow] 创建工作流定义: key={}, version={}, nodes={}",
                request.getDefinitionKey(), version, request.getNodes().size());
        return defId;
    }

    public List<WorkflowVO.DefinitionVO> listDefinitions() {
        return workflowRepository.findAllDefinitions().stream()
                .map(d -> WorkflowVO.DefinitionVO.builder()
                        .id(d.getId()).definitionKey(d.getDefinitionKey())
                        .definitionName(d.getDefinitionName()).category(d.getCategory())
                        .status(d.getStatus()).createTime(d.getCreateTime()).build())
                .collect(Collectors.toList());
    }

    public WorkflowVO.DefinitionDetailVO getDefinitionDetail(Long id) {
        WorkflowDefinition def = workflowRepository.findDefinitionById(id)
                .orElseThrow(() -> new IllegalArgumentException("工作流定义不存在: id=" + id));
        List<WorkflowNode> nodes = workflowRepository.findNodesByDefinitionId(id);

        return WorkflowVO.DefinitionDetailVO.builder()
                .id(def.getId())
                .definitionKey(def.getDefinitionKey())
                .definitionName(def.getDefinitionName())
                .description(def.getDescription())
                .category(def.getCategory())
                .version(def.getVersion())
                .status(def.getStatus())
                .createTime(def.getCreateTime())
                .nodes(nodes.stream().map(n -> WorkflowVO.NodeVO.builder()
                        .id(n.getId()).nodeName(n.getNodeName()).nodeType(n.getNodeType())
                        .execMode(n.getExecMode()).parallelGroup(n.getParallelGroup())
                        .conditionExpression(n.getConditionExpression())
                        .onConditionFail(n.getOnConditionFail())
                        .approvers(n.getApprovers()).sortOrder(n.getSortOrder()).build())
                        .collect(Collectors.toList()))
                .build();
    }

    @Transactional
    public void updateDefinition(Long id, WorkflowCreateRequest request) {
        WorkflowDefinition def = workflowRepository.findDefinitionById(id)
                .orElseThrow(() -> new IllegalArgumentException("工作流定义不存在: id=" + id));

        workflowRepository.updateDefinition(id, request.getDefinitionName(),
                request.getDescription(), request.getCategory());

        // 删除旧节点并重新创建
        workflowRepository.deleteNodesByDefinitionId(id);

        int sort = 0;
        for (WorkflowCreateRequest.NodeRequest nodeReq : request.getNodes()) {
            WorkflowNode node = WorkflowNode.builder()
                    .definitionId(id)
                    .nodeName(nodeReq.getNodeName())
                    .nodeType(nodeReq.getNodeType() != null ? nodeReq.getNodeType() : "APPROVAL")
                    .execMode(nodeReq.getExecMode() != null ? nodeReq.getExecMode() : "SERIAL")
                    .parallelGroup(nodeReq.getParallelGroup())
                    .parentNodeId(nodeReq.getParentNodeId())
                    .conditionExpression(nodeReq.getConditionExpression())
                    .onConditionFail(nodeReq.getOnConditionFail() != null ? nodeReq.getOnConditionFail() : "REJECT")
                    .approverStrategy(nodeReq.getApproverStrategy() != null ? nodeReq.getApproverStrategy() : "SPECIFIC")
                    .approvers(nodeReq.getApprovers())
                    .approverRole(nodeReq.getApproverRole())
                    .sortOrder(nodeReq.getSortOrder() != null ? nodeReq.getSortOrder() : sort++)
                    .timeoutHours(nodeReq.getTimeoutHours())
                    .countersign(nodeReq.getCountersign() != null ? nodeReq.getCountersign() : false)
                    .rejectStrategy(nodeReq.getRejectStrategy() != null ? nodeReq.getRejectStrategy() : "TO_PREV")
                    .build();
            workflowRepository.saveNode(node);
        }

        log.info("[Workflow] 更新工作流定义: id={}, name={}, nodes={}",
                id, request.getDefinitionName(), request.getNodes().size());
    }

    @Transactional
    public void deleteDefinition(Long id) {
        WorkflowDefinition def = workflowRepository.findDefinitionById(id)
                .orElseThrow(() -> new IllegalArgumentException("工作流定义不存在: id=" + id));
        workflowRepository.deleteDefinition(id);
        log.info("[Workflow] 删除工作流定义: id={}, key={}", id, def.getDefinitionKey());
    }

    @Transactional
    public void toggleDefinitionStatus(Long id, boolean enabled) {
        WorkflowDefinition def = workflowRepository.findDefinitionById(id)
                .orElseThrow(() -> new IllegalArgumentException("工作流定义不存在: id=" + id));
        int status = enabled ? 1 : 2; // 1-启用 2-停用
        workflowRepository.updateDefinitionStatus(id, status);
        log.info("[Workflow] 切换工作流状态: id={}, status={}", id, status);
    }

    // ======================== 2. 发起审批申请 ========================

    @Transactional
    public Long submitApplication(String definitionKey, String title, String applyContent, String applicant) {
        WorkflowDefinition definition = workflowRepository.findDefinitionByKey(definitionKey)
                .orElseThrow(() -> new IllegalArgumentException("流程定义不存在: " + definitionKey));

        List<WorkflowNode> nodes = workflowRepository.findNodesByDefinitionId(definition.getId());
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("流程定义缺少审批节点");
        }

        // 构建审批链
        String approvalChain = nodes.stream()
                .map(n -> String.valueOf(n.getId())).collect(Collectors.joining(","));

        // 创建流程实例
        WorkflowInstance instance = WorkflowInstance.builder()
                .definitionId(definition.getId())
                .title(title)
                .applicant(applicant)
                .applyContent(applyContent)
                .status(WorkflowStatus.PENDING.name())
                .currentNodeId(null) // 由引擎流转设置
                .approvalChain(approvalChain)
                .approvalRecords("[]")
                .createdBy(applicant)
                .build();
        Long instanceId = workflowRepository.saveInstance(instance);

        // 更新实例后, 通过引擎开始流转(从第一个节点开始)
        instance.setId(instanceId);
        Map<String, String> emptyHistory = new LinkedHashMap<>();

        // 找到第一个节点
        WorkflowNode firstNode = nodes.get(0);
        if ("START".equalsIgnoreCase(firstNode.getNodeType())) {
            // 开始节点直接跳过
            workflowEngine.advanceToNext(instance, firstNode.getId(), emptyHistory);
        } else {
            // 直接是审批节点
            startFromFirstApproval(instance, nodes, firstNode);
        }

        redisHelper.set("workflow:pending:instance:" + instanceId, applicant, 86400, TimeUnit.SECONDS);
        log.info("[Workflow] 发起审批: instanceId={}, applicant={}, currentNode={}",
                instanceId, applicant, firstNode.getNodeName());
        return instanceId;
    }

    /**
     * 按表单提交审批申请：根据 formKey 找到绑定的流程 definitionKey，再复用 submitApplication。
     * 若表单配置了 applyType，会自动注入到 applyContent 中，供审批通过后处理器路由。
     */
    @Transactional
    public Long submitApplicationByForm(String formKey, String title, String applyContent, String applicant) {
        WorkflowForm form = workflowFormRepository.findByKey(formKey)
                .orElseThrow(() -> new IllegalArgumentException("表单不存在或已停用: " + formKey));
        String contentWithType = injectApplyType(applyContent, form.getApplyType(), applicant);
        return submitApplication(form.getDefinitionKey(), title, contentWithType, applicant);
    }

    /**
     * 将表单绑定的 applyType 注入到 applyContent JSON 中（不存在时覆盖）。
     */
    private String injectApplyType(String applyContent, String applyType, String applicant) {
        if (applyType == null || applyType.isBlank()) {
            return applyContent;
        }
        try {
            ObjectNode node = applyContent != null && !applyContent.isBlank()
                    ? (ObjectNode) objectMapper.readTree(applyContent)
                    : objectMapper.createObjectNode();
            node.put("type", applyType);
            // 子系统可见场景需要目标用户即申请人，若前端未传，则默认填入申请人 username
            if ("SUBSYSTEM_VISIBILITY".equals(applyType) && !node.has("targetUsername")) {
                node.put("targetUsername", applicant);
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            log.warn("[WorkflowService] applyContent 不是 JSON，无法注入 applyType: {}", applyContent);
            return applyContent;
        }
    }

    private void startFromFirstApproval(WorkflowInstance instance, List<WorkflowNode> allNodes, WorkflowNode firstNode) {
        if (firstNode.isParallelStart()) {
            // 启动并行: 使用引擎从PARALLEL_START索引开始处理
            workflowEngine.startFrom(instance, 0, new LinkedHashMap<>());
            return;
        }
        if (firstNode.isCondition()) {
            // 第一个节点就是条件节点, 从索引0开始
            workflowEngine.startFrom(instance, 0, new LinkedHashMap<>());
            return;
        }
        // 普通审批节点(或 END)
        if (firstNode.isApproval()) {
            List<String> approvers = resolveApprovers(firstNode, instance.getApplicant());
            for (String approver : approvers) {
                WorkflowTask task = WorkflowTask.builder()
                        .instanceId(instance.getId()).nodeId(firstNode.getId())
                        .nodeName(firstNode.getNodeName()).approver(approver.trim()).status("PENDING")
                        .build();
                workflowRepository.saveTask(task);
            }
            workflowRepository.updateInstanceStatus(instance.getId(), "PENDING", firstNode.getId(),
                    String.join(",", approvers), "[]");
        } else if ("END".equalsIgnoreCase(firstNode.getNodeType())) {
            workflowRepository.updateInstanceStatus(instance.getId(), WorkflowStatus.APPROVED.name(),
                    null, null, "[]");
        }
    }

    // ======================== 3. 审批操作 ========================

    @Transactional(rollbackFor = Exception.class)
    public void processApproval(ApprovalRequest request, String operator) {
        WorkflowTask task = workflowRepository.findTaskById(request.getTaskId())
                .orElseThrow(() -> new IllegalArgumentException("审批任务不存在"));
        if (!"PENDING".equals(task.getStatus())) {
            throw new IllegalStateException("该任务已处理");
        }

        WorkflowInstance instance = workflowRepository.findInstanceById(task.getInstanceId())
                .orElseThrow(() -> new IllegalArgumentException("流程实例不存在"));

        ApprovalAction action = ApprovalAction.valueOf(request.getAction().toUpperCase());

        switch (action) {
            case APPROVE -> handleApprove(task, instance, request.getComment());
            case REJECT -> handleReject(task, instance, request.getComment());
            case TRANSFER -> handleTransfer(task, instance, request.getComment(), request.getTransferTo());
            case WITHDRAW -> handleWithdraw(instance, operator);
        }

        // 写入审批记录
        appendApprovalRecord(instance, action, operator, request.getComment());
        // 重新加载最新实例并持久化审批记录（避免引擎更新状态后记录被覆盖或丢失）
        WorkflowInstance latest = workflowRepository.findInstanceById(instance.getId()).orElse(instance);
        if (latest.getApprovalRecords() == null || !latest.getApprovalRecords().equals(instance.getApprovalRecords())) {
            workflowRepository.updateInstanceStatus(latest.getId(), latest.getStatus(),
                    latest.getCurrentNodeId(), latest.getCurrentApprover(), instance.getApprovalRecords());
        }
    }

    /**
     * 通过：更新任务 → 检查会签 → 调用引擎流转
     */
    private void handleApprove(WorkflowTask task, WorkflowInstance instance, String comment) {
        workflowRepository.updateTaskStatus(task.getId(), "APPROVED", comment);

        WorkflowNode currentNode = workflowRepository.findNodeById(task.getNodeId()).orElse(null);
        boolean countersign = currentNode != null && Boolean.TRUE.equals(currentNode.getCountersign());

        if (countersign) {
            List<WorkflowTask> nodeTasks = workflowRepository.findTasksByInstanceId(instance.getId())
                    .stream().filter(t -> t.getNodeId().equals(task.getNodeId())).toList();
            boolean allApproved = nodeTasks.stream().allMatch(t -> "APPROVED".equals(t.getStatus()));
            if (!allApproved) {
                log.info("[Workflow] 会签等待: instanceId={}, nodeId={}", instance.getId(), task.getNodeId());
                return;
            }
        }

        // 重新加载最新实例
        WorkflowInstance freshInstance = workflowRepository.findInstanceById(instance.getId())
                .orElse(instance);

        // 构建审批历史
        Map<String, String> approvalHistory = buildApprovalHistory(freshInstance);

        // 调用引擎流转到下一节点
        workflowEngine.advanceToNext(freshInstance, task.getNodeId(), approvalHistory);

        // 流程最终通过 → 触发权限赋予(审批通过即为目标用户赋权)
        WorkflowInstance latest = workflowRepository.findInstanceById(instance.getId()).orElse(instance);
        if (WorkflowStatus.APPROVED.name().equals(latest.getStatus())) {
            permissionGrantService.grantOnApproved(latest);
        }
    }

    /**
     * 驳回：根据节点策略处理
     */
    private void handleReject(WorkflowTask task, WorkflowInstance instance, String comment) {
        workflowRepository.updateTaskStatus(task.getId(), "REJECTED", comment);

        WorkflowNode currentNode = workflowRepository.findNodeById(task.getNodeId()).orElse(null);
        String rejectStrategy = currentNode != null ? currentNode.getRejectStrategy() : "TO_START";

        if ("TO_PREV".equals(rejectStrategy)) {
            List<Long> chainIds = parseChain(instance.getApprovalChain());
            int idx = chainIds.indexOf(task.getNodeId());
            if (idx > 0) {
                Long prevNodeId = chainIds.get(idx - 1);
                WorkflowNode prevNode = workflowRepository.findNodeById(prevNodeId).orElse(null);
                if (prevNode != null && prevNode.isApproval()) {
                    List<String> approvers = resolveApprovers(prevNode, instance.getApplicant());
                    for (String approver : approvers) {
                        WorkflowTask t = WorkflowTask.builder()
                                .instanceId(instance.getId()).nodeId(prevNode.getId())
                                .nodeName(prevNode.getNodeName()).approver(approver.trim())
                                .status("PENDING").build();
                        workflowRepository.saveTask(t);
                    }
                    workflowRepository.updateInstanceStatus(instance.getId(), "PENDING", prevNodeId,
                            String.join(",", approvers), buildApprovalRecords(instance));
                    log.info("[Workflow] 驳回到上一节点: instanceId={}, node={}",
                            instance.getId(), prevNode.getNodeName());
                    return;
                }
            }
        }
        // 驳回结束
        workflowRepository.updateInstanceStatus(instance.getId(), WorkflowStatus.REJECTED.name(),
                null, null, buildApprovalRecords(instance));
        log.info("[Workflow] 流程被驳回: instanceId={}, reason={}", instance.getId(), comment);
    }

    /**
     * 转交
     */
    private void handleTransfer(WorkflowTask task, WorkflowInstance instance, String comment, String transferTo) {
        if (transferTo == null || transferTo.isBlank()) {
            throw new IllegalArgumentException("转交目标人不能为空");
        }
        workflowRepository.updateTaskStatus(task.getId(), "TRANSFERRED",
                "转交给: " + transferTo + (comment != null ? "; " + comment : ""));
        WorkflowTask newTask = WorkflowTask.builder()
                .instanceId(instance.getId()).nodeId(task.getNodeId())
                .nodeName(task.getNodeName()).approver(transferTo).status("PENDING")
                .transferredFrom(task.getApprover()).build();
        workflowRepository.saveTask(newTask);
        workflowRepository.updateInstanceStatus(instance.getId(), "PENDING", task.getNodeId(),
                transferTo, buildApprovalRecords(instance));
        log.info("[Workflow] 审批转交: instanceId={}, from={}, to={}",
                instance.getId(), task.getApprover(), transferTo);
    }

    /**
     * 撤回
     */
    private void handleWithdraw(WorkflowInstance instance, String operator) {
        if (!operator.equals(instance.getApplicant())) {
            throw new IllegalArgumentException("仅申请人可撤回");
        }
        if (!"PENDING".equals(instance.getStatus())) {
            throw new IllegalStateException("审批已完成, 无法撤回");
        }
        workflowRepository.updateInstanceStatus(instance.getId(), WorkflowStatus.WITHDRAWN.name(),
                null, null, buildApprovalRecords(instance));
        log.info("[Workflow] 申请人撤回: instanceId={}", instance.getId());
    }

    // ======================== 4. 查询接口 ========================

    public WorkflowVO getWorkflowDetail(Long instanceId) {
        WorkflowInstance instance = workflowRepository.findInstanceById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("流程实例不存在"));
        WorkflowDefinition definition = workflowRepository.findDefinitionById(instance.getDefinitionId()).orElse(null);
        List<WorkflowNode> nodes = workflowRepository.findNodesByDefinitionId(instance.getDefinitionId());
        List<WorkflowTask> tasks = workflowRepository.findTasksByInstanceId(instanceId);

        WorkflowNode currentNode = instance.getCurrentNodeId() != null
                ? workflowRepository.findNodeById(instance.getCurrentNodeId()).orElse(null) : null;

        return WorkflowVO.builder()
                .definition(definition != null ? WorkflowVO.DefinitionVO.builder()
                        .id(definition.getId()).definitionKey(definition.getDefinitionKey())
                        .definitionName(definition.getDefinitionName()).category(definition.getCategory())
                        .status(definition.getStatus()).createTime(definition.getCreateTime()).build() : null)
                .instance(WorkflowVO.InstanceVO.builder()
                        .id(instance.getId()).title(instance.getTitle())
                        .applicant(instance.getApplicant()).applyContent(instance.getApplyContent())
                        .status(instance.getStatus()).currentApprover(instance.getCurrentApprover())
                        .currentNodeName(currentNode != null ? currentNode.getNodeName() : null)
                        .createTime(instance.getCreateTime()).finishTime(instance.getFinishTime()).build())
                .nodes(nodes.stream().map(n -> WorkflowVO.NodeVO.builder()
                        .id(n.getId()).nodeName(n.getNodeName()).nodeType(n.getNodeType())
                        .approvers(n.getApprovers()).sortOrder(n.getSortOrder()).build()).toList())
                .tasks(tasks.stream().map(t -> WorkflowVO.TaskVO.builder()
                        .id(t.getId()).nodeName(t.getNodeName()).approver(t.getApprover())
                        .status(t.getStatus()).comment(t.getComment())
                        .createTime(t.getCreateTime()).approveTime(t.getApproveTime()).build()).toList())
                .build();
    }

    public List<WorkflowVO.InstanceVO> myApplications(String applicant) {
        return workflowRepository.findInstancesByApplicant(applicant).stream()
                .map(this::toInstanceVO).collect(Collectors.toList());
    }

    public List<WorkflowVO.InstanceVO> myPendingApprovals(String approver) {
        return workflowRepository.findPendingInstancesByApprover(approver).stream()
                .map(this::toInstanceVO).collect(Collectors.toList());
    }

    public List<WorkflowVO.ApprovalRecordVO> myApprovalRecords(String approver) {
        return workflowRepository.findApprovalRecordsByApprover(approver).stream()
                .map(p -> WorkflowVO.ApprovalRecordVO.builder()
                        .taskId(p.getTaskId())
                        .instanceId(p.getInstanceId())
                        .title(p.getTitle())
                        .applicant(p.getApplicant())
                        .nodeName(p.getNodeName())
                        .action(mapTaskStatusToAction(p.getStatus()))
                        .comment(p.getComment())
                        .approveTime(p.getApproveTime())
                        .instanceStatus(p.getInstanceStatus())
                        .build())
                .collect(Collectors.toList());
    }

    private String mapTaskStatusToAction(String status) {
        return switch (status != null ? status.toUpperCase() : "") {
            case "APPROVED" -> "APPROVE";
            case "REJECTED" -> "REJECT";
            case "TRANSFERRED" -> "TRANSFER";
            default -> status;
        };
    }

    public long countPendingApprovals() {
        return workflowRepository.countPendingInstances();
    }

    public List<WorkflowVO.TaskVO> getPendingTasks(Long instanceId) {
        return workflowRepository.findTasksByInstanceId(instanceId).stream()
                .filter(t -> "PENDING".equals(t.getStatus()))
                .map(t -> WorkflowVO.TaskVO.builder()
                        .id(t.getId()).nodeName(t.getNodeName()).approver(t.getApprover())
                        .status(t.getStatus()).createTime(t.getCreateTime()).build())
                .collect(Collectors.toList());
    }

    // ======================== 内部工具方法 ========================

    private Map<String, String> buildApprovalHistory(WorkflowInstance instance) {
        Map<String, String> history = new LinkedHashMap<>();
        List<WorkflowTask> tasks = workflowRepository.findTasksByInstanceId(instance.getId());
        for (WorkflowTask t : tasks) {
            if (!"PENDING".equals(t.getStatus())) {
                history.put(t.getNodeName(), t.getStatus());
            }
        }
        return history;
    }

    private List<String> resolveApprovers(WorkflowNode node, String applicant) {
        return switch (node.getApproverStrategy()) {
            case "SPECIFIC" -> node.getApprovers() != null
                    ? Arrays.asList(node.getApprovers().split(",")) : List.of();
            case "ROLE_BASED" -> List.of("admin");
            case "DEPARTMENT_LEADER" -> List.of("dept_leader");
            default -> List.of("admin");
        };
    }

    private void appendApprovalRecord(WorkflowInstance instance, ApprovalAction action,
                                       String operator, String comment) {
        String record = String.format("{\"time\":\"%s\",\"action\":\"%s\",\"operator\":\"%s\",\"comment\":\"%s\"}",
                LocalDateTime.now(), action.name(), operator,
                comment != null ? comment.replace("\"", "'") : "");
        String existing = instance.getApprovalRecords();
        if (existing == null || "[]".equals(existing) || existing.length() <= 2) {
            existing = "[" + record + "]";
        } else {
            existing = existing.substring(0, existing.length() - 1) + "," + record + "]";
        }
        instance.setApprovalRecords(existing);
    }

    private String buildApprovalRecords(WorkflowInstance instance) {
        return instance.getApprovalRecords();
    }

    private List<Long> parseChain(String chain) {
        if (chain == null || chain.isBlank()) return List.of();
        return Arrays.stream(chain.split(",")).map(Long::parseLong).collect(Collectors.toList());
    }

    private WorkflowVO.InstanceVO toInstanceVO(WorkflowInstance instance) {
        WorkflowNode currentNode = instance.getCurrentNodeId() != null
                ? workflowRepository.findNodeById(instance.getCurrentNodeId()).orElse(null) : null;
        return WorkflowVO.InstanceVO.builder()
                .id(instance.getId()).title(instance.getTitle())
                .applicant(instance.getApplicant()).applyContent(instance.getApplyContent())
                .status(instance.getStatus()).currentApprover(instance.getCurrentApprover())
                .currentNodeName(currentNode != null ? currentNode.getNodeName() : null)
                .createTime(instance.getCreateTime()).finishTime(instance.getFinishTime()).build();
    }
}
