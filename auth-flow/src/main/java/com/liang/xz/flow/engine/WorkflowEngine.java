package com.liang.xz.flow.engine;

import com.liang.xz.flow.entity.*;
import com.liang.xz.flow.enums.WorkflowStatus;
import com.liang.xz.flow.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工作流核心引擎 —— 负责复杂的串行/并行节点流转 + 条件分支判断
 *
 * <p>核心算法:</p>
 * <pre>
 * advanceAfterApprove(currentNodeId):
 *   1. 判断当前节点是否是并行组内节点
 *      a. 若是 → 检查该并行组所有节点是否都已通过
 *         - 未全部通过 → 等待(return)
 *         - 全部通过 → 进入并行组的 PARALLEL_END
 *   2. 查找下一个节点(按 sortOrder)
 *      a. 若为 CONDITION → 执行条件评估
 *         - 通过 → 继续向后
 *         - 不通过 → 根据 onConditionFail: REJECT(驳回结束) / SKIP(跳过继续)
 *      b. 若为 PARALLEL_START → 创建所有并行子节点的审批任务
 *      c. 若为 APPROVAL → 创建该节点审批任务
 *      d. 若为 END → 审批通过结束
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngine {

    private final WorkflowRepository workflowRepository;
    private final ConditionEvaluator conditionEvaluator;

    /**
     * 审批通过后流转到下一节点(支持串行/并行/条件分支)
     *
     * @param instance 当前流程实例(需已刷新最新状态)
     * @param currentNodeId 当前已通过的节点ID
     * @param approvalHistory 审批历史 Map{nodeName: action}
     */
    public void advanceToNext(WorkflowInstance instance, Long currentNodeId,
                              Map<String, String> approvalHistory) {
        log.info("[Engine] advanceToNext 开始: instanceId={}, currentNodeId={}", instance.getId(), currentNodeId);
        WorkflowNode currentNode = workflowRepository.findNodeById(currentNodeId).orElse(null);
        if (currentNode == null) {
            log.error("[Engine] 节点不存在: nodeId={}", currentNodeId);
            return;
        }

        // 1. 检查当前节点是否属于并行组(有 parentNodeId)
        if (currentNode.getParentNodeId() != null) {
            if (!checkParallelGroupComplete(instance, currentNode)) {
                return; // 并行组未全部完成, 等待
            }
            // 并行组完成 → 找到 PARALLEL_END 节点并继续
            WorkflowNode parallelEnd = findParallelEnd(currentNode);
            if (parallelEnd == null) {
                // 没有 PARALLEL_END, 找下一个序号节点
                Long nextId = findNextNodeAfterParallel(instance, currentNode);
                if (nextId != null) {
                    advanceFrom(instance, nextId, approvalHistory);
                } else {
                    finishWithApprove(instance);
                }
                return;
            }
            // 继续从 PARALLEL_END 向后流转
            currentNodeId = parallelEnd.getId();
        }

        // 2. 从当前节点开始向后遍历处理
        advanceFrom(instance, currentNodeId, approvalHistory);
    }

    /**
     * 启动流程时从指定索引开始处理(含该索引的节点本身)
     * <p>用于流程首次发起或节点非审批类型(PARALLEL_START/CONDITION/START)</p>
     */
    public void startFrom(WorkflowInstance instance, int startIndex,
                          Map<String, String> approvalHistory) {
        List<WorkflowNode> allNodes = workflowRepository.findNodesByDefinitionId(instance.getDefinitionId());
        if (startIndex >= allNodes.size()) {
            finishWithApprove(instance);
            return;
        }
        advanceFromIndex(instance, allNodes, startIndex, approvalHistory);
    }

    /**
     * 从一个节点开始向后遍历处理(从 currentNodeId 的下一节点开始)
     */
    private void advanceFrom(WorkflowInstance instance, Long currentNodeId,
                             Map<String, String> approvalHistory) {
        List<WorkflowNode> allNodes = workflowRepository.findNodesByDefinitionId(instance.getDefinitionId());
        int currentIndex = findNodeIndex(allNodes, currentNodeId);
        log.info("[Engine] advanceFrom: instanceId={}, currentNodeId={}, currentIndex={}, totalNodes={}",
                instance.getId(), currentNodeId, currentIndex, allNodes.size());
        advanceFromIndex(instance, allNodes, currentIndex + 1, approvalHistory);
    }

    /**
     * 从指定索引开始遍历处理节点(含该索引)
     */
    private void advanceFromIndex(WorkflowInstance instance, List<WorkflowNode> allNodes, int startIndex,
                                   Map<String, String> approvalHistory) {
        int nextIndex = startIndex;

        while (nextIndex < allNodes.size()) {
            WorkflowNode nextNode = allNodes.get(nextIndex);
            log.info("[Engine] 遍历节点: instanceId={}, index={}, nodeType={}, nodeName={}",
                    instance.getId(), nextIndex, nextNode.getNodeType(), nextNode.getNodeName());

            // --- 条件节点 ---
            if (nextNode.isCondition()) {
                List<WorkflowNode> conditionGroup = collectAdjacentConditionNodes(allNodes, nextIndex);
                boolean allPassed = evaluateConditionGroup(conditionGroup, instance, approvalHistory);

                if (!allPassed) {
                    // 条件不满足 → 根据第一个失败节点的 onConditionFail 处理
                    WorkflowNode failedNode = findFirstFailedNode(conditionGroup, instance, approvalHistory);
                    String failAction = failedNode != null && failedNode.getOnConditionFail() != null
                            ? failedNode.getOnConditionFail() : "REJECT";

                    if ("SKIP".equalsIgnoreCase(failAction)) {
                        log.info("[Engine] 条件节点 {} 不满足但跳过: instanceId={}",
                                failedNode != null ? failedNode.getNodeName() : "?", instance.getId());
                        nextIndex = findNodeIndex(allNodes, conditionGroup.get(conditionGroup.size() - 1).getId()) + 1;
                        continue;
                    }
                    // 严格模式: REJECT → 驳回结束
                    finishWithReject(instance,
                            failedNode != null ? "条件不满足: " + failedNode.getNodeName()
                                    + "(" + failedNode.getConditionExpression() + ")" : "条件不满足");
                    return;
                }
                // 条件通过 → 跳过条件节点组, 继续
                nextIndex = findNodeIndex(allNodes, conditionGroup.get(conditionGroup.size() - 1).getId()) + 1;
                continue;
            }

            // --- 并行开始节点 ---
            if (nextNode.isParallelStart()) {
                List<WorkflowNode> parallelChildren = findParallelChildren(allNodes, nextNode);
                if (parallelChildren.isEmpty()) {
                    nextIndex++;
                    continue;
                }
                // 创建所有并行节点的审批任务
                for (WorkflowNode child : parallelChildren) {
                    if (child.isApproval()) {
                        List<String> approvers = resolveApprovers(child, instance.getApplicant());
                        createTasksForNode(instance.getId(), child, approvers);
                    }
                }
                // 更新实例状态(并行审批中)
                workflowRepository.updateInstanceStatus(instance.getId(), "PENDING",
                        nextNode.getId(),
                        "PARALLEL_GROUP:" + parallelChildren.stream()
                                .map(WorkflowNode::getNodeName).collect(Collectors.joining(",")),
                        buildApprovalRecords(instance));
                log.info("[Engine] 并行审批启动: instanceId={}, group={}, children={}",
                        instance.getId(),
                        nextNode.getParallelGroup() != null ? nextNode.getParallelGroup() : nextNode.getNodeName(),
                        parallelChildren.stream().map(WorkflowNode::getNodeName).toList());
                return; // 等待所有并行节点完成
            }

            // --- 普通审批节点 ---
            if (nextNode.isApproval()) {
                List<String> approvers = resolveApprovers(nextNode, instance.getApplicant());
                createTasksForNode(instance.getId(), nextNode, approvers);
                workflowRepository.updateInstanceStatus(instance.getId(), "PENDING",
                        nextNode.getId(), String.join(",", approvers),
                        buildApprovalRecords(instance));
                log.info("[Engine] 流转到: instanceId={}, node={}, approvers={}",
                        instance.getId(), nextNode.getNodeName(), approvers);
                return;
            }

            // --- PARALLEL_END / END / CALLBACK ---
            if ("END".equalsIgnoreCase(nextNode.getNodeType())
                    || "CALLBACK".equalsIgnoreCase(nextNode.getNodeType())) {
                log.info("[Engine] 遇到结束节点: instanceId={}, nodeType={}", instance.getId(), nextNode.getNodeType());
                finishWithApprove(instance);
                return;
            }

            if (nextNode.isParallelEnd()) {
                nextIndex++;
                continue;
            }

            // 其他类型(SKIP等)继续
            nextIndex++;
        }

        // 所有节点遍历完毕 → 审批通过
        log.info("[Engine] 所有节点遍历完毕, 完成审批: instanceId={}", instance.getId());
        finishWithApprove(instance);
    }

    /**
     * 检查并行组内是否所有节点都已审批通过
     */
    private boolean checkParallelGroupComplete(WorkflowInstance instance, WorkflowNode currentNode) {
        List<WorkflowNode> allNodes = workflowRepository.findNodesByDefinitionId(instance.getDefinitionId());
        // 找到同一 parentNodeId 的所有并行节点
        List<WorkflowNode> siblings = allNodes.stream()
                .filter(n -> currentNode.getParentNodeId().equals(n.getParentNodeId()))
                .filter(WorkflowNode::isApproval)
                .toList();

        if (siblings.isEmpty()) return true;

        // 检查每个兄弟节点的审批任务是否都已完成
        List<WorkflowTask> allTasks = workflowRepository.findTasksByInstanceId(instance.getId());
        for (WorkflowNode sibling : siblings) {
            boolean approved = allTasks.stream()
                    .anyMatch(t -> t.getNodeId().equals(sibling.getId()) && "APPROVED".equals(t.getStatus()));
            if (!approved) {
                log.info("[Engine] 并行组等待: instanceId={}, node={}, group={}",
                        instance.getId(), sibling.getNodeName(), currentNode.getParallelGroup());
                return false;
            }
        }
        log.info("[Engine] 并行组全部完成: instanceId={}, parentNodeId={}",
                instance.getId(), currentNode.getParentNodeId());
        return true;
    }

    /**
     * 查找并行组的 PARALLEL_END 节点
     */
    private WorkflowNode findParallelEnd(WorkflowNode parallelChild) {
        List<WorkflowNode> allNodes = workflowRepository.findNodesByDefinitionId(
                workflowRepository.findNodeById(parallelChild.getParentNodeId())
                        .map(WorkflowNode::getDefinitionId).orElse(null));
        if (allNodes == null) return null;

        // 从 PARALLEL_START 之后找 PARALLEL_END
        int startIdx = findNodeIndex(allNodes, parallelChild.getParentNodeId());
        for (int i = startIdx + 1; i < allNodes.size(); i++) {
            if (allNodes.get(i).isParallelEnd()) {
                return allNodes.get(i);
            }
        }
        return null;
    }

    /**
     * 并行组完成后, 找 PARALLEL_END 之后的下一个节点
     */
    private Long findNextNodeAfterParallel(WorkflowInstance instance, WorkflowNode currentNode) {
        List<WorkflowNode> allNodes = workflowRepository.findNodesByDefinitionId(instance.getDefinitionId());
        int idx = findNodeIndex(allNodes,
                currentNode.getParentNodeId() != null ? currentNode.getParentNodeId() : currentNode.getId());
        // 跳过 PARALLEL_END 找下一个APPROVAL或END
        for (int i = idx + 1; i < allNodes.size(); i++) {
            WorkflowNode n = allNodes.get(i);
            if (n.isApproval() || n.isCondition() || n.isParallelStart()
                    || "END".equalsIgnoreCase(n.getNodeType())) {
                return n.getId();
            }
        }
        return null;
    }

    /**
     * 收集连续的条件节点(支持 AND 组合)
     */
    private List<WorkflowNode> collectAdjacentConditionNodes(List<WorkflowNode> nodes, int startIndex) {
        List<WorkflowNode> result = new ArrayList<>();
        for (int i = startIndex; i < nodes.size(); i++) {
            if (nodes.get(i).isCondition()) {
                result.add(nodes.get(i));
            } else {
                break;
            }
        }
        return result;
    }

    /**
     * 评估条件节点组(全部通过才返回true)
     */
    private boolean evaluateConditionGroup(List<WorkflowNode> conditionNodes, WorkflowInstance instance,
                                           Map<String, String> approvalHistory) {
        for (WorkflowNode node : conditionNodes) {
            if (!conditionEvaluator.evaluate(node, instance.getApplyContent(),
                    instance.getApplicant(), instance.getApplicant(), approvalHistory)) {
                return false;
            }
        }
        return true;
    }

    private WorkflowNode findFirstFailedNode(List<WorkflowNode> conditionNodes, WorkflowInstance instance,
                                             Map<String, String> approvalHistory) {
        for (WorkflowNode node : conditionNodes) {
            if (!conditionEvaluator.evaluate(node, instance.getApplyContent(),
                    instance.getApplicant(), instance.getApplicant(), approvalHistory)) {
                return node;
            }
        }
        return null;
    }

    /**
     * 查找 PARALLEL_START 的所有子节点(属于此并行组的节点)
     */
    private List<WorkflowNode> findParallelChildren(List<WorkflowNode> allNodes, WorkflowNode parallelStart) {
        // 从 PARALLEL_START 之后到 PARALLEL_END 之前, 取所有 APPROVAL 节点
        List<WorkflowNode> children = new ArrayList<>();
        boolean inParallel = false;
        for (WorkflowNode node : allNodes) {
            if (node.getId().equals(parallelStart.getId())) {
                inParallel = true;
                continue;
            }
            if (inParallel && node.isParallelEnd()) break;
            if (inParallel && node.isApproval()) {
                children.add(node);
            }
        }
        return children;
    }

    /**
     * 审批通过结束
     */
    private void finishWithApprove(WorkflowInstance instance) {
        log.info("[Engine] finishWithApprove 开始: instanceId={}, currentStatus={}",
                instance.getId(), instance.getStatus());
        workflowRepository.updateInstanceStatus(instance.getId(), WorkflowStatus.APPROVED.name(),
                null, null, buildApprovalRecords(instance));
        log.info("[Engine] 流程审批通过: instanceId={}", instance.getId());
    }

    /**
     * 条件不满足, 驳回结束
     */
    private void finishWithReject(WorkflowInstance instance, String reason) {
        workflowRepository.updateInstanceStatus(instance.getId(), WorkflowStatus.REJECTED.name(),
                null, null, buildApprovalRecords(instance));
        log.info("[Engine] 条件不满足, 流程驳回: instanceId={}, reason={}", instance.getId(), reason);
    }

    // ======================== 工具方法 ========================

    private int findNodeIndex(List<WorkflowNode> nodes, Long nodeId) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).getId().equals(nodeId)) return i;
        }
        return -1;
    }

    private void createTasksForNode(Long instanceId, WorkflowNode node, List<String> approvers) {
        for (String approver : approvers) {
            WorkflowTask task = WorkflowTask.builder()
                    .instanceId(instanceId).nodeId(node.getId())
                    .nodeName(node.getNodeName()).approver(approver.trim()).status("PENDING")
                    .build();
            workflowRepository.saveTask(task);
        }
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

    private String buildApprovalRecords(WorkflowInstance instance) {
        return instance.getApprovalRecords();
    }
}
