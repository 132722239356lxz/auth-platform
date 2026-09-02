package com.liang.xz.system.controller;

import com.liang.xz.resource.security.RequirePermission;
import com.liang.xz.system.dto.*;
import com.liang.xz.system.service.DictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>数据字典管理接口</p>
 *
 * @author auth-platform
 * @since 1.2.0
 */
@RestController
@RequestMapping("/api/dicts")
@RequiredArgsConstructor
@Tag(name = "数据字典", description = "系统字典类型与字典数据的管理，供前端下拉框等场景使用")
public class DictController {

    private final DictService dictService;

    // ======================== 字典类型 ========================

    @GetMapping("/types")
    @Operation(summary = "获取所有字典类型")
    @RequirePermission("system:dict:list")
    public ApiResponse<List<DictTypeResponse>> listTypes() {
        return ApiResponse.success(dictService.listTypes());
    }

    @GetMapping("/types/{id}")
    @Operation(summary = "按ID查询字典类型")
    @RequirePermission("system:dict:list")
    public ApiResponse<DictTypeResponse> getTypeById(
            @Parameter(description = "字典类型ID") @PathVariable Long id) {
        return dictService.getTypeById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.fail(404, "字典类型不存在"));
    }

    @PostMapping("/types")
    @Operation(summary = "创建字典类型")
    @RequirePermission("system:dict:add")
    public ApiResponse<DictTypeResponse> createType(
            @Parameter(description = "字典类型信息") @Valid @RequestBody DictTypeRequest request) {
        try {
            return ApiResponse.success("字典类型创建成功", dictService.createType(request));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @PutMapping("/types/{id}")
    @Operation(summary = "更新字典类型")
    @RequirePermission("system:dict:edit")
    public ApiResponse<DictTypeResponse> updateType(
            @Parameter(description = "字典类型ID") @PathVariable Long id,
            @Parameter(description = "字典类型信息") @Valid @RequestBody DictTypeRequest request) {
        return dictService.updateType(id, request)
                .map(r -> ApiResponse.success("字典类型更新成功", r))
                .orElse(ApiResponse.fail(404, "字典类型不存在"));
    }

    @DeleteMapping("/types/{id}")
    @Operation(summary = "删除字典类型", description = "删除字典类型同时删除关联的字典数据")
    @RequirePermission("system:dict:delete")
    public ApiResponse<Void> deleteType(
            @Parameter(description = "字典类型ID") @PathVariable Long id) {
        boolean ok = dictService.deleteType(id);
        return ok ? ApiResponse.success("字典类型已删除", null)
                : ApiResponse.fail(404, "字典类型不存在");
    }

    // ======================== 字典数据 ========================

    @GetMapping("/data/type/{typeId}")
    @Operation(summary = "按类型ID获取字典数据", description = "返回指定字典类型下的所有数据项")
    @RequirePermission("system:dict:list")
    public ApiResponse<List<DictDataResponse>> listDataByTypeId(
            @Parameter(description = "字典类型ID") @PathVariable Long typeId) {
        return ApiResponse.success(dictService.listDataByTypeId(typeId));
    }

    @GetMapping("/data/key/{dictType}")
    @Operation(summary = "按字典标识获取数据", description = "供前端下拉框使用，无需额外权限(已认证即可)")
    public ApiResponse<List<DictDataResponse>> listDataByKey(
            @Parameter(description = "字典类型标识", example = "sys_user_status") @PathVariable String dictType) {
        return ApiResponse.success(dictService.listDataByDictType(dictType));
    }

    @GetMapping("/data/{id}")
    @Operation(summary = "按ID查询字典数据")
    @RequirePermission("system:dict:list")
    public ApiResponse<DictDataResponse> getDataById(
            @Parameter(description = "字典数据ID") @PathVariable Long id) {
        return dictService.getDataById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.fail(404, "字典数据不存在"));
    }

    @PostMapping("/data")
    @Operation(summary = "创建字典数据")
    @RequirePermission("system:dict:add")
    public ApiResponse<DictDataResponse> createData(
            @Parameter(description = "字典数据信息") @Valid @RequestBody DictDataRequest request) {
        return ApiResponse.success("字典数据创建成功", dictService.createData(request));
    }

    @PutMapping("/data/{id}")
    @Operation(summary = "更新字典数据")
    @RequirePermission("system:dict:edit")
    public ApiResponse<DictDataResponse> updateData(
            @Parameter(description = "字典数据ID") @PathVariable Long id,
            @Parameter(description = "字典数据信息") @Valid @RequestBody DictDataRequest request) {
        return dictService.updateData(id, request)
                .map(r -> ApiResponse.success("字典数据更新成功", r))
                .orElse(ApiResponse.fail(404, "字典数据不存在"));
    }

    @DeleteMapping("/data/{id}")
    @Operation(summary = "删除字典数据")
    @RequirePermission("system:dict:delete")
    public ApiResponse<Void> deleteData(
            @Parameter(description = "字典数据ID") @PathVariable Long id) {
        boolean ok = dictService.deleteData(id);
        return ok ? ApiResponse.success("字典数据已删除", null)
                : ApiResponse.fail(404, "字典数据不存在");
    }
}
