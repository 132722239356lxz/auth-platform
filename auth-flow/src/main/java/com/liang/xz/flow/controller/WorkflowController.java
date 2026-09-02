package com.liang.xz.flow.controller;

import com.liang.xz.flow.dto.ApprovalRequest;
import com.liang.xz.flow.dto.R;
import com.liang.xz.flow.dto.WorkflowCreateRequest;
import com.liang.xz.flow.dto.WorkflowSubmitByFormRequest;
import com.liang.xz.flow.dto.WorkflowVO;
import com.liang.xz.flow.service.WorkflowService;
import com.liang.xz.resource.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工作流审批 API
 */
@Tag(name = "工作流审批", description = "流程定义、发起申请、审批流转、查询")
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @Operation(summary = "创建工作流定义")
    @RequirePermission("workflow:definition:add")
    @PostMapping("/definition")
    public R<Long> createDefinition(@Valid @RequestBody WorkflowCreateRequest request,
                                    @RequestHeader(value = "X-User", defaultValue = "admin") String createdBy) {
        return R.ok(workflowService.createDefinition(request, createdBy));
    }

    @Operation(summary = "查询所有工作流定义")
    @RequirePermission("workflow:definition:list")
    @GetMapping("/definition/list")
    public R<List<WorkflowVO.DefinitionVO>> listDefinitions() {
        return R.ok(workflowService.listDefinitions());
    }

    @Operation(summary = "查询工作流定义详情(含节点)")
    @RequirePermission("workflow:definition:list")
    @GetMapping("/definition/{id}")
    public R<WorkflowVO.DefinitionDetailVO> getDefinition(@PathVariable Long id) {
        return R.ok(workflowService.getDefinitionDetail(id));
    }

    @Operation(summary = "更新工作流定义")
    @RequirePermission("workflow:definition:edit")
    @PutMapping("/definition/{id}")
    public R<Void> updateDefinition(@PathVariable Long id, @Valid @RequestBody WorkflowCreateRequest request) {
        workflowService.updateDefinition(id, request);
        return R.ok();
    }

    @Operation(summary = "删除工作流定义")
    @RequirePermission("workflow:definition:delete")
    @DeleteMapping("/definition/{id}")
    public R<Void> deleteDefinition(@PathVariable Long id) {
        workflowService.deleteDefinition(id);
        return R.ok();
    }

    @Operation(summary = "启用/停用工作流定义")
    @RequirePermission("workflow:definition:edit")
    @PutMapping("/definition/{id}/status")
    public R<Void> toggleDefinitionStatus(
            @PathVariable Long id,
            @Parameter(description = "是否启用") @RequestParam boolean enabled) {
        workflowService.toggleDefinitionStatus(id, enabled);
        return R.ok();
    }

    @Operation(summary = "发起审批申请")
    @RequirePermission("workflow:instance:add")
    @PostMapping("/submit")
    public R<Long> submitApplication(
            @Parameter(description = "流程定义Key") @RequestParam String definitionKey,
            @Parameter(description = "申请标题") @RequestParam String title,
            @Parameter(description = "申请内容JSON") @RequestParam String applyContent,
            @Parameter(description = "申请人") @RequestParam String applicant) {
        return R.ok(workflowService.submitApplication(definitionKey, title, applyContent, applicant));
    }

    @Operation(summary = "按表单发起审批申请（表单与流程已绑定）")
    @RequirePermission("workflow:instance:add")
    @PostMapping("/submit-by-form")
    public R<Long> submitByForm(
            @Valid @RequestBody WorkflowSubmitByFormRequest request,
            @RequestHeader(value = "X-User", defaultValue = "admin") String headerUser) {
        String applicant = request.getApplicant() != null && !request.getApplicant().isBlank()
                ? request.getApplicant() : headerUser;
        return R.ok(workflowService.submitApplicationByForm(request.getFormKey(), request.getTitle(),
                request.getApplyContent(), applicant));
    }

    @Operation(summary = "审批操作(通过/驳回/转交)")
    @RequirePermission("workflow:instance:approve")
    @PostMapping("/approve")
    public R<Void> approve(@Valid @RequestBody ApprovalRequest request,
                           @RequestHeader(value = "X-User", defaultValue = "admin") String operator) {
        workflowService.processApproval(request, operator);
        return R.ok();
    }

    @Operation(summary = "流程详情")
    @RequirePermission("workflow:instance:list")
    @GetMapping("/{instanceId}")
    public R<WorkflowVO> getDetail(@PathVariable Long instanceId) {
        return R.ok(workflowService.getWorkflowDetail(instanceId));
    }

    @Operation(summary = "我的申请")
    @RequirePermission("workflow:instance:list")
    @GetMapping("/my-applications")
    public R<List<WorkflowVO.InstanceVO>> myApplications(
            @Parameter(description = "申请人") @RequestParam String applicant) {
        return R.ok(workflowService.myApplications(applicant));
    }

    @Operation(summary = "待我审批")
    @RequirePermission("workflow:instance:list")
    @GetMapping("/my-pending")
    public R<List<WorkflowVO.InstanceVO>> myPending(@RequestParam String approver) {
        return R.ok(workflowService.myPendingApprovals(approver));
    }

    @Operation(summary = "待审批总数")
    @RequirePermission("workflow:instance:list")
    @GetMapping("/pending-count")
    public R<Long> pendingCount() {
        return R.ok(workflowService.countPendingApprovals());
    }

    @Operation(summary = "我的审批记录")
    @RequirePermission("workflow:instance:list")
    @GetMapping("/my-records")
    public R<List<WorkflowVO.ApprovalRecordVO>> myApprovalRecords(
            @Parameter(description = "审批人") @RequestParam String approver) {
        return R.ok(workflowService.myApprovalRecords(approver));
    }
}
