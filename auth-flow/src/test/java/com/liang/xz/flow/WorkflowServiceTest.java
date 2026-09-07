package com.liang.xz.flow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.flow.dto.ApprovalRequest;
import com.liang.xz.flow.dto.WorkflowCreateRequest;
import com.liang.xz.flow.dto.WorkflowVO;
import com.liang.xz.flow.repository.WorkflowRepository;
import com.liang.xz.flow.service.WorkflowService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工作流审批测试用例
 *
 * <p>测试场景:</p>
 * <ol>
 *   <li>简单串行审批(通过+驳回)</li>
 *   <li>并行审批(多节点同时审批)</li>
 *   <li>条件分支(等级≥3才需要总监审批)</li>
 *   <li>会签模式(全部审批人通过才流转)</li>
 *   <li>大额申请(金额>10000 触发条件, 否则跳过)</li>
 *   <li>转交审批</li>
 *   <li>撤回申请</li>
 *   <li>条件不满足直接驳回</li>
 * </ol>
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("工作流审批测试")
class WorkflowServiceTest {

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private WorkflowRepository workflowRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ================================================================
    // 测试 1: 简单串行审批 (部门经理 → 总监 → 审批通过)
    // ================================================================

    @Test
    @Order(1)
    @DisplayName("场景1: 简单串行两节点审批通过")
    void testSimpleSerialApproval() throws JsonProcessingException {
        // 1. 创建流程定义: 部门经理审批 → 总监审批
        WorkflowCreateRequest req = new WorkflowCreateRequest();
        req.setDefinitionKey("TEST_SIMPLE_SERIAL");
        req.setDefinitionName("简单串行审批测试");
        req.setCategory("PERMISSION");
        req.setNodes(List.of(
                buildNodeReq("部门经理审批", "APPROVAL", null, "manager", 0),
                buildNodeReq("总监审批", "APPROVAL", null, "director", 1)
        ));
        Long defId = workflowService.createDefinition(req, "admin");
        assertNotNull(defId, "创建流程定义应成功");

        // 2. 发起申请
        String applyContent = MAPPER.writeValueAsString(Map.of("permission", "READ_WRITE", "level", 1));
        Long instanceId = workflowService.submitApplication("TEST_SIMPLE_SERIAL",
                "申请读写权限", applyContent, "user1");
        assertNotNull(instanceId);

        // 3. 验证第一个节点任务已创建
        List<WorkflowVO.TaskVO> tasks = workflowService.getPendingTasks(instanceId);
        assertEquals(1, tasks.size());
        assertEquals("manager", tasks.get(0).getApprover());

        // 4. 经理审批通过
        ApprovalRequest approve1 = new ApprovalRequest();
        approve1.setTaskId(tasks.get(0).getId());
        approve1.setAction("APPROVE");
        approve1.setComment("同意");
        workflowService.processApproval(approve1, "manager");

        // 5. 验证流转到总监
        tasks = workflowService.getPendingTasks(instanceId);
        assertFalse(tasks.isEmpty(), "应流转到总监节点");
        assertEquals("director", tasks.get(0).getApprover());

        // 6. 总监审批通过
        ApprovalRequest approve2 = new ApprovalRequest();
        approve2.setTaskId(tasks.get(0).getId());
        approve2.setAction("APPROVE");
        approve2.setComment("批准");
        workflowService.processApproval(approve2, "director");

        // 7. 验证流程完成
        WorkflowVO detail = workflowService.getWorkflowDetail(instanceId);
        assertEquals("APPROVED", detail.getInstance().getStatus());
        System.out.println("✓ 场景1通过: 简单串行两节点审批通过");
    }

    // ================================================================
    // 测试 2: 驳回流程 (部门经理驳回)
    // ================================================================

    @Test
    @Order(2)
    @DisplayName("场景2: 发起后第一节点驳回")
    void testRejectAtFirstNode() throws JsonProcessingException {
        WorkflowCreateRequest req = new WorkflowCreateRequest();
        req.setDefinitionKey("TEST_REJECT_FLOW");
        req.setDefinitionName("驳回测试流程");
        req.setCategory("RESOURCE");
        req.setNodes(List.of(
                buildNodeReq("安全审核", "APPROVAL", "TO_START", "security_admin", 0),
                buildNodeReq("最终审批", "APPROVAL", null, "boss", 1)
        ));
        workflowService.createDefinition(req, "admin");

        String applyContent = MAPPER.writeValueAsString(Map.of("resource", "server-01"));
        Long instanceId = workflowService.submitApplication("TEST_REJECT_FLOW",
                "申请服务器权限", applyContent, "user2");

        // 安全管理员驳回
        List<WorkflowVO.TaskVO> tasks = workflowService.getPendingTasks(instanceId);
        assertEquals(1, tasks.size());

        ApprovalRequest reject = new ApprovalRequest();
        reject.setTaskId(tasks.get(0).getId());
        reject.setAction("REJECT");
        reject.setComment("安全风险过高, 驳回");
        workflowService.processApproval(reject, "security_admin");

        // 验证流程已驳回(驳回策略=TO_START, 直接结束)
        WorkflowVO detail = workflowService.getWorkflowDetail(instanceId);
        assertEquals("REJECTED", detail.getInstance().getStatus());
        System.out.println("✓ 场景2通过: 发起后第一节点驳回");
    }

    // ================================================================
    // 测试 3: 并行审批 (多个并行节点同时审批)
    // ================================================================

    @Test
    @Order(3)
    @DisplayName("场景3: 并行审批-法务+财务同时审批后才流转")
    void testParallelApproval() throws JsonProcessingException {
        WorkflowCreateRequest req = new WorkflowCreateRequest();
        req.setDefinitionKey("TEST_PARALLEL_FLOW");
        req.setDefinitionName("并行审批测试");
        req.setCategory("RESOURCE");
        req.setNodes(List.of(
                buildNodeReq("并行开始", "PARALLEL_START", null, null, 0),
                buildNodeReq("法务审批", "APPROVAL", null, "lawyer", 1),
                buildNodeReq("财务审批", "APPROVAL", null, "finance", 2),
                buildNodeReq("并行汇合", "PARALLEL_END", null, null, 3),
                buildNodeReq("总经理审批", "APPROVAL", null, "ceo", 4)
        ));
        workflowService.createDefinition(req, "admin");

        String applyContent = MAPPER.writeValueAsString(Map.of("contract", "采购合同", "amount", 50000));
        Long instanceId = workflowService.submitApplication("TEST_PARALLEL_FLOW",
                "采购合同审批", applyContent, "user3");

        // 并行阶段: 应创建法务+财务两个任务
        List<WorkflowVO.TaskVO> tasks = workflowService.getPendingTasks(instanceId);
        assertEquals(2, Float.parseFloat("并行阶段应有2个审批任务(法务+财务)"), tasks.size());
        List<String> approvers = tasks.stream().map(WorkflowVO.TaskVO::getApprover).toList();
        assertTrue(approvers.contains("lawyer"));
        assertTrue(approvers.contains("finance"));

        // 法务审批通过(此时不应流转, 还需等财务)
        ApprovalRequest lawyerApprove = new ApprovalRequest();
        lawyerApprove.setTaskId(tasks.stream().filter(t -> "lawyer".equals(t.getApprover())).findFirst().get().getId());
        lawyerApprove.setAction("APPROVE");
        lawyerApprove.setComment("法务审核通过");
        workflowService.processApproval(lawyerApprove, "lawyer");

        // 财务还未审批, 当前不应流转到CEO
        tasks = workflowService.getPendingTasks(instanceId);
        assertEquals(1, tasks.size(), "法务通过后还剩财务待审批");
        assertEquals("finance", tasks.get(0).getApprover());

        // 财务审批通过 → 现在应流转到CEO
        ApprovalRequest financeApprove = new ApprovalRequest();
        financeApprove.setTaskId(tasks.get(0).getId());
        financeApprove.setAction("APPROVE");
        financeApprove.setComment("财务审核通过");
        workflowService.processApproval(financeApprove, "finance");

        // 验证流转到CEO节点
        tasks = workflowService.getPendingTasks(instanceId);
        assertFalse(tasks.isEmpty(), "并行完成后应流转到CEO");
        assertEquals("ceo", tasks.get(0).getApprover());

        // CEO通过 → 流程结束
        ApprovalRequest ceoApprove = new ApprovalRequest();
        ceoApprove.setTaskId(tasks.get(0).getId());
        ceoApprove.setAction("APPROVE");
        ceoApprove.setComment("批准");
        workflowService.processApproval(ceoApprove, "ceo");

        WorkflowVO detail = workflowService.getWorkflowDetail(instanceId);
        assertEquals("APPROVED", detail.getInstance().getStatus());
        System.out.println("✓ 场景3通过: 并行审批-法务+财务→CEO");
    }

    // ================================================================
    // 测试 4: 条件分支 (等级≥3 才需要总监审批, 否则直接通过)
    // ================================================================

    @Test
    @Order(4)
    @DisplayName("场景4a: 条件分支-低等级申请跳过总监审批")
    void testConditionalLowLevel() throws JsonProcessingException {
        WorkflowCreateRequest req = new WorkflowCreateRequest();
        req.setDefinitionKey("TEST_CONDITION_FLOW");
        req.setDefinitionName("条件分支审批测试");
        req.setCategory("PERMISSION");
        req.setNodes(List.of(
                buildNodeReq("部门经理审批", "APPROVAL", null, "manager2", 0),
                // 条件: 等级>=3时才需要总监审批, 否则SKIP跳过
                buildConditionNodeReq("等级检查", "#applyContent['level'] >= 3", "SKIP", 1),
                buildNodeReq("总监审批", "APPROVAL", null, "director2", 2)
        ));
        workflowService.createDefinition(req, "admin");

        // 等级=1 的申请 → 条件不满足 → SKIP总监节点
        String applyContent = MAPPER.writeValueAsString(Map.of("permission", "READ_ONLY", "level", 1));
        Long instanceId = workflowService.submitApplication("TEST_CONDITION_FLOW",
                "申请只读权限(低等级)", applyContent, "user4");

        // 经理审批通过
        List<WorkflowVO.TaskVO> tasks = workflowService.getPendingTasks(instanceId);
        assertEquals(1, tasks.size());
        ApprovalRequest approve = new ApprovalRequest();
        approve.setTaskId(tasks.get(0).getId());
        approve.setAction("APPROVE");
        approve.setComment("同意");
        workflowService.processApproval(approve, "manager2");

        // 条件不满足, 应跳过总监节点, 直接审批通过
        WorkflowVO detail = workflowService.getWorkflowDetail(instanceId);
        assertEquals("APPROVED", detail.getInstance().getStatus(),
                "低等级申请应跳过总监审批直接通过");
        System.out.println("✓ 场景4a通过: 条件分支-低等级SKIP总监");
    }

    @Test
    @Order(5)
    @DisplayName("场景4b: 条件分支-高等级申请需要总监审批")
    void testConditionalHighLevel() throws JsonProcessingException {
        // 等级=5 → 条件满足 → 进入总监审批
        String applyContent = MAPPER.writeValueAsString(Map.of("permission", "ADMIN", "level", 5));
        Long instanceId = workflowService.submitApplication("TEST_CONDITION_FLOW",
                "申请管理员权限(高等级)", applyContent, "user5");

        // 经理审批通过
        List<WorkflowVO.TaskVO> tasks = workflowService.getPendingTasks(instanceId);
        assertEquals(1, tasks.size());
        ApprovalRequest approve = new ApprovalRequest();
        approve.setTaskId(tasks.get(0).getId());
        approve.setAction("APPROVE");
        approve.setComment("同意, 转总监审批");
        workflowService.processApproval(approve, "manager2");

        // 条件满足, 应流转到总监
        tasks = workflowService.getPendingTasks(instanceId);
        assertFalse(tasks.isEmpty(), "高等级申请应流转到总监审批");
        assertEquals("director2", tasks.get(0).getApprover());

        // 总监通过
        approve = new ApprovalRequest();
        approve.setTaskId(tasks.get(0).getId());
        approve.setAction("APPROVE");
        approve.setComment("批准");
        workflowService.processApproval(approve, "director2");

        WorkflowVO detail = workflowService.getWorkflowDetail(instanceId);
        assertEquals("APPROVED", detail.getInstance().getStatus());
        System.out.println("✓ 场景4b通过: 条件分支-高等级需要总监");
    }

    // ================================================================
    // 测试 5: 大额申请条件(金额>10000才触发, 否则REJECT驳回)
    // ================================================================

    @Test
    @Order(6)
    @DisplayName("场景5: 条件不满足直接驳回(金额不足)")
    void testConditionalRejectOnFail() throws JsonProcessingException {
        WorkflowCreateRequest req = new WorkflowCreateRequest();
        req.setDefinitionKey("TEST_AMOUNT_FLOW");
        req.setDefinitionName("金额条件审批");
        req.setCategory("RESOURCE");
        req.setNodes(List.of(
                buildNodeReq("部门审批", "APPROVAL", null, "dept_admin", 0),
                // 条件: 金额>10000 → REJECT: 不满足直接驳回
                buildConditionNodeReq("金额检查", "#applyContent['amount'] > 10000", "REJECT", 1),
                buildNodeReq("财务审批", "APPROVAL", null, "finance2", 2)
        ));
        workflowService.createDefinition(req, "admin");

        // 金额不足(2000 → 不满足条件 → 直接驳回)
        String applyContent = MAPPER.writeValueAsString(Map.of("item", "办公用品", "amount", 2000));
        Long instanceId = workflowService.submitApplication("TEST_AMOUNT_FLOW",
                "申请购买办公用品", applyContent, "user6");

        // 部门审批通过
        List<WorkflowVO.TaskVO> tasks = workflowService.getPendingTasks(instanceId);
        ApprovalRequest approve = new ApprovalRequest();
        approve.setTaskId(tasks.get(0).getId());
        approve.setAction("APPROVE");
        approve.setComment("同意");
        workflowService.processApproval(approve, "dept_admin");

        // 金额不满足条件 → 直接驳回
        WorkflowVO detail = workflowService.getWorkflowDetail(instanceId);
        assertEquals("REJECTED", detail.getInstance().getStatus(),
                "金额不满足条件应直接驳回");
        System.out.println("✓ 场景5通过: 条件不满足直接驳回");
    }

    // ================================================================
    // 测试 6: 会签模式 (3个审批人全部通过才流转)
    // ================================================================

    @Test
    @Order(7)
    @DisplayName("场景6: 会签-三人全部通过才流转")
    void testCountersignAllApproved() throws JsonProcessingException {
        WorkflowCreateRequest req = new WorkflowCreateRequest();
        req.setDefinitionKey("TEST_COUNTERSIGN_FLOW");
        req.setDefinitionName("会签测试流程");
        req.setCategory("PERMISSION");
        req.setNodes(List.of(
                buildNodeReq("安全会签", "APPROVAL", null, "sec1,sec2,sec3", 0),
                buildNodeReq("管理员确认", "APPROVAL", null, "admin", 1)
        ));
        // 手动设置会签模式(通过修改节点)
        req.getNodes().get(0).setCountersign(true);

        workflowService.createDefinition(req, "admin");

        String applyContent = MAPPER.writeValueAsString(Map.of("access", "生产环境", "level", 5));
        Long instanceId = workflowService.submitApplication("TEST_COUNTERSIGN_FLOW",
                "申请生产环境访问权限", applyContent, "user7");

        // 应创建3个任务(sec1, sec2, sec3)
        List<WorkflowVO.TaskVO> tasks = workflowService.getPendingTasks(instanceId);
        assertEquals(3, Float.parseFloat("会签应创建3个审批任务"), tasks.size());

        // sec1 通过
        ApprovalRequest a1 = new ApprovalRequest();
        a1.setTaskId(tasks.stream().filter(t -> "sec1".equals(t.getApprover())).findFirst().get().getId());
        a1.setAction("APPROVE");
        a1.setComment("通过");
        workflowService.processApproval(a1, "sec1");

        // 还未全部通过, 不应流转
        WorkflowVO detail = workflowService.getWorkflowDetail(instanceId);
        assertEquals("PENDING", detail.getInstance().getStatus(), "会签未全部通过不应流转");

        // sec2, sec3 通过
        tasks = workflowService.getPendingTasks(instanceId);
        for (WorkflowVO.TaskVO t : tasks) {
            ApprovalRequest a = new ApprovalRequest();
            a.setTaskId(t.getId());
            a.setAction("APPROVE");
            a.setComment("通过");
            workflowService.processApproval(a, t.getApprover());
        }

        // 全部通过 → 流转到管理员确认
        tasks = workflowService.getPendingTasks(instanceId);
        assertEquals(1, tasks.size(), "会签完成后应流转到下一节点");
        assertEquals("admin", tasks.get(0).getApprover());
        System.out.println("✓ 场景6通过: 会签-三人全部通过流转");
    }

    // ================================================================
    // 测试 7: 转交审批
    // ================================================================

    @Test
    @Order(8)
    @DisplayName("场景7: 转交审批")
    void testTransferApproval() throws JsonProcessingException {
        WorkflowCreateRequest req = new WorkflowCreateRequest();
        req.setDefinitionKey("TEST_TRANSFER_FLOW");
        req.setDefinitionName("转交测试");
        req.setCategory("RESOURCE");
        req.setNodes(List.of(
                buildNodeReq("主管审批", "APPROVAL", null, "supervisor1", 0),
                buildNodeReq("最终审批", "APPROVAL", null, "final_boss", 1)
        ));
        workflowService.createDefinition(req, "admin");

        String applyContent = MAPPER.writeValueAsString(Map.of("resource", "云服务器"));
        Long instanceId = workflowService.submitApplication("TEST_TRANSFER_FLOW",
                "申请云服务器", applyContent, "user8");

        List<WorkflowVO.TaskVO> tasks = workflowService.getPendingTasks(instanceId);
        assertEquals(1, tasks.size());
        assertEquals("supervisor1", tasks.get(0).getApprover());

        // supervisor1 转交给 supervisor2
        ApprovalRequest transfer = new ApprovalRequest();
        transfer.setTaskId(tasks.get(0).getId());
        transfer.setAction("TRANSFER");
        transfer.setComment("我不在, 转交处理");
        transfer.setTransferTo("supervisor2");
        workflowService.processApproval(transfer, "supervisor1");

        // 验证新任务给 supervisor2
        tasks = workflowService.getPendingTasks(instanceId);
        assertEquals(1, tasks.size());
        assertEquals("supervisor2", tasks.get(0).getApprover());

        System.out.println("✓ 场景7通过: 转交审批");
    }

    // ================================================================
    // 测试 8: 撤回申请
    // ================================================================

    @Test
    @Order(9)
    @DisplayName("场景8: 申请人撤回")
    void testWithdrawApplication() throws JsonProcessingException {
        WorkflowCreateRequest req = new WorkflowCreateRequest();
        req.setDefinitionKey("TEST_WITHDRAW_FLOW");
        req.setDefinitionName("撤回测试");
        req.setCategory("PERMISSION");
        req.setNodes(List.of(
                buildNodeReq("IT审批", "APPROVAL", null, "it_admin", 0),
                buildNodeReq("经理审批", "APPROVAL", null, "manager3", 1)
        ));
        workflowService.createDefinition(req, "admin");

        String applyContent = MAPPER.writeValueAsString(Map.of("permission", "VPN"));
        Long instanceId = workflowService.submitApplication("TEST_WITHDRAW_FLOW",
                "申请VPN权限", applyContent, "user9");

        // user9 本人撤回
        List<WorkflowVO.TaskVO> tasks = workflowService.getPendingTasks(instanceId);
        ApprovalRequest withdraw = new ApprovalRequest();
        withdraw.setTaskId(tasks.get(0).getId());
        withdraw.setAction("WITHDRAW");
        withdraw.setComment("不需要了");
        workflowService.processApproval(withdraw, "user9");

        WorkflowVO detail = workflowService.getWorkflowDetail(instanceId);
        assertEquals("WITHDRAWN", detail.getInstance().getStatus());
        System.out.println("✓ 场景8通过: 申请人撤回");
    }

    // ================================================================
    // 测试 9: 复杂场景-串行+并行+条件组合
    // ================================================================

    @Test
    @Order(10)
    @DisplayName("场景9: 复杂流程-部门审批→并行(法务+技术)→条件(金额)→CEO")
    void testComplexWorkflow() throws JsonProcessingException {
        WorkflowCreateRequest req = new WorkflowCreateRequest();
        req.setDefinitionKey("TEST_COMPLEX_FLOW");
        req.setDefinitionName("复杂审批流程");
        req.setCategory("RESOURCE");
        req.setNodes(List.of(
                buildNodeReq("部门经理审批", "APPROVAL", null, "dept_mgr", 0),
                buildNodeReq("并行开始", "PARALLEL_START", null, null, 1),
                buildNodeReq("法务审核", "APPROVAL", null, "legal_team", 2),
                buildNodeReq("技术评估", "APPROVAL", null, "tech_team", 3),
                buildNodeReq("并行汇合", "PARALLEL_END", null, null, 4),
                buildConditionNodeReq("金额校验", "#applyContent['amount'] > 50000", "SKIP", 5),
                buildNodeReq("CEO审批", "APPROVAL", null, "ceo_boss", 6)
        ));
        workflowService.createDefinition(req, "admin");

        // 大额申请(80000) → 满足金额条件 → 需要CEO审批
        String applyContent = MAPPER.writeValueAsString(Map.of(
                "project", "数据平台建设", "amount", 80000, "department", "IT"));
        Long instanceId = workflowService.submitApplication("TEST_COMPLEX_FLOW",
                "数据平台项目立项", applyContent, "user10");

        // 步骤1: 部门经理审批
        List<WorkflowVO.TaskVO> tasks = workflowService.getPendingTasks(instanceId);
        assertEquals(1, tasks.size());
        assertEquals("dept_mgr", tasks.get(0).getApprover());
        approveTask(tasks.get(0).getId(), "dept_mgr");

        // 步骤2: 并行-法务+技术
        tasks = workflowService.getPendingTasks(instanceId);
        assertEquals(2, "并行阶段应有2个任务");
        approveTask(tasks.get(0).getId(), tasks.get(0).getApprover());
        approveTask(tasks.get(1).getId(), tasks.get(1).getApprover());

        // 步骤3: 金额>50000 → CEO审批
        tasks = workflowService.getPendingTasks(instanceId);
        assertFalse(tasks.isEmpty(), "大额申请应流转到CEO");
        assertEquals("ceo_boss", tasks.get(0).getApprover());
        approveTask(tasks.get(0).getId(), "ceo_boss");

        // 验证完成
        WorkflowVO detail = workflowService.getWorkflowDetail(instanceId);
        assertEquals("APPROVED", detail.getInstance().getStatus());
        System.out.println("✓ 场景9通过: 复杂流程-部门→并行(法务+技术)→CEO");
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    private WorkflowCreateRequest.NodeRequest buildNodeReq(String name, String type,
                                                            String rejectStrategy, String approvers, int sort) {
        WorkflowCreateRequest.NodeRequest n = new WorkflowCreateRequest.NodeRequest();
        n.setNodeName(name);
        n.setNodeType(type);
        n.setApproverStrategy("SPECIFIC");
        n.setApprovers(approvers);
        n.setSortOrder(sort);
        n.setRejectStrategy(rejectStrategy != null ? rejectStrategy : "TO_START");
        n.setCountersign(false);
        return n;
    }

    private WorkflowCreateRequest.NodeRequest buildConditionNodeReq(String name, String expression,
                                                                     String onFail, int sort) {
        WorkflowCreateRequest.NodeRequest n = new WorkflowCreateRequest.NodeRequest();
        n.setNodeName(name);
        n.setNodeType("CONDITION");
        n.setConditionExpression(expression);
        n.setOnConditionFail(onFail);
        n.setSortOrder(sort);
        return n;
    }

    private void approveTask(Long taskId, String operator) {
        ApprovalRequest req = new ApprovalRequest();
        req.setTaskId(taskId);
        req.setAction("APPROVE");
        req.setComment(operator + "审批通过");
        workflowService.processApproval(req, operator);
    }
}
