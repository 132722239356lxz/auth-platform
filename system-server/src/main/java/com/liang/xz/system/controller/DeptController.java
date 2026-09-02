package com.liang.xz.system.controller;

import com.liang.xz.system.dto.ApiResponse;
import com.liang.xz.system.dto.DeptRequest;
import com.liang.xz.system.dto.DeptResponse;
import com.liang.xz.system.dto.DeptTreeQuery;
import com.liang.xz.system.service.DeptService;
import com.liang.xz.resource.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>部门管理接口</p>
 *
 * @author auth-platform
 * @since 1.2.0
 */
@RestController
@RequestMapping("/api/depts")
@RequiredArgsConstructor
@Tag(name = "部门管理", description = "部门树形查询、新增、编辑、删除，支持启用/禁用及前端多条件筛选")
public class DeptController {

    private final DeptService deptService;

    @GetMapping("/tree")
    @Operation(summary = "查询部门树", description = "支持 keyword（部门名称/编码/负责人）和 enabled 过滤，不传则返回全部")
    @RequirePermission("system:dept:list")
    public ApiResponse<List<DeptResponse>> tree(@Valid DeptTreeQuery query) {
        return ApiResponse.success(deptService.getDeptTree(query));
    }

    @GetMapping
    @Operation(summary = "查询所有部门(扁平列表)")
    @RequirePermission("system:dept:list")
    public ApiResponse<List<DeptResponse>> list() {
        return ApiResponse.success(deptService.listAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "按ID查询部门")
    @RequirePermission("system:dept:list")
    public ApiResponse<DeptResponse> getById(@PathVariable Long id) {
        DeptResponse dept = deptService.getById(id);
        return dept != null ? ApiResponse.success(dept) : ApiResponse.fail(404, "部门不存在");
    }

    @PostMapping
    @Operation(summary = "新增部门")
    @RequirePermission("system:dept:add")
    public ApiResponse<DeptResponse> create(@Valid @RequestBody DeptRequest request) {
        return ApiResponse.success(deptService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑部门")
    @RequirePermission("system:dept:edit")
    public ApiResponse<DeptResponse> update(@PathVariable Long id, @Valid @RequestBody DeptRequest request) {
        return ApiResponse.success(deptService.update(id, request));
    }

    @PutMapping("/{id}/enable")
    @Operation(summary = "启用部门")
    @RequirePermission("system:dept:edit")
    public ApiResponse<Void> enable(@PathVariable Long id) {
        deptService.enable(id);
        return ApiResponse.success("部门已启用", null);
    }

    @PutMapping("/{id}/disable")
    @Operation(summary = "禁用部门")
    @RequirePermission("system:dept:edit")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        deptService.disable(id);
        return ApiResponse.success("部门已禁用", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除部门")
    @RequirePermission("system:dept:delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
