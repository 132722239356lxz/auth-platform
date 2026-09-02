package com.liang.xz.flow.controller;

import com.liang.xz.common.core.annotation.PublicApi;
import com.liang.xz.common.core.model.R;
import com.liang.xz.flow.entity.WorkflowForm;
import com.liang.xz.flow.repository.WorkflowFormRepository;
import com.liang.xz.resource.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * <p>审批表单定义管理接口</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Tag(name = "审批表单定义", description = "表单与审批流程绑定，前端按表单schema动态渲染")
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowFormController {

    private final WorkflowFormRepository workflowFormRepository;

    @Operation(summary = "列表查询审批表单(维护页，按条件，含停用)")
    @GetMapping("/forms")
    @RequirePermission("workflow:form:list")
    public R<List<WorkflowForm>> listForms(
            @RequestParam(required = false) String formKey,
            @RequestParam(required = false) String formName,
            @RequestParam(required = false) String definitionKey,
            @RequestParam(required = false) String applyType,
            @RequestParam(required = false) Integer status) {
        return R.ok(workflowFormRepository.findByCondition(
                formKey, formName, definitionKey, applyType, status));
    }

    @Operation(summary = "列出所有启用的审批表单")
    @GetMapping("/forms/enabled")
    @RequirePermission("workflow:form:list")
    public R<List<WorkflowForm>> listEnabledForms() {
        return R.ok(workflowFormRepository.findAllEnabled());
    }

    @Operation(summary = "根据表单Key获取表单定义(含schema)")
    @GetMapping("/forms/{formKey}")
    @RequirePermission("workflow:form:list")
    public R<WorkflowForm> getForm(@PathVariable String formKey) {
        return workflowFormRepository.findByKey(formKey)
                .map(R::ok)
                .orElseGet(() -> R.fail("表单不存在或已停用: " + formKey));
    }

    @Operation(summary = "新增审批表单定义")
    @PostMapping("/forms")
    @RequirePermission("workflow:form:add")
    @ResponseStatus(HttpStatus.CREATED)
    public R<WorkflowForm> addForm(@RequestBody WorkflowForm form) {
        return R.ok(workflowFormRepository.save(form));
    }

    @Operation(summary = "更新审批表单定义(按formKey)")
    @PutMapping("/forms/{formKey}")
    @RequirePermission("workflow:form:edit")
    public R<WorkflowForm> updateForm(@PathVariable String formKey, @RequestBody WorkflowForm form) {
        Optional<WorkflowForm> existing = workflowFormRepository.findByKey(formKey);
        if (existing.isEmpty()) {
            return R.fail("表单不存在: " + formKey);
        }
        return R.ok(workflowFormRepository.updateByKey(formKey, form));
    }

    @Operation(summary = "启用/停用审批表单")
    @PutMapping("/forms/{formKey}/status")
    @RequirePermission("workflow:form:edit")
    public R<Boolean> updateFormStatus(@PathVariable String formKey, @RequestBody WorkflowForm form) {
        workflowFormRepository.updateStatus(formKey, form.getStatus());
        return R.ok(Boolean.TRUE);
    }

    @Operation(summary = "删除审批表单定义")
    @DeleteMapping("/forms/{formKey}")
    @RequirePermission("workflow:form:delete")
    public R<Boolean> removeForm(@PathVariable String formKey) {
        workflowFormRepository.deleteByKey(formKey);
        return R.ok(Boolean.TRUE);
    }

    /**
     * 门户发起申请时拉取可选表单，属于公开只读场景，不校验菜单权限。
     */
    @Operation(summary = "门户可选表单(公开)")
    @GetMapping("/forms/public/options")
    @PublicApi
    public R<List<WorkflowForm>> publicFormOptions() {
        return R.ok(workflowFormRepository.findAllEnabled());
    }
}
