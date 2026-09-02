package com.liang.xz.system.controller;

import com.liang.xz.resource.security.RequirePermission;
import com.liang.xz.system.dto.*;
import com.liang.xz.system.service.CurrentUserService;
import com.liang.xz.system.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统公告管理接口
 */
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
@Tag(name = "系统公告", description = "系统发布公告的增删改查管理")
public class NoticeController {

    private final NoticeService noticeService;
    private final CurrentUserService currentUserService;

    @GetMapping("/published")
    @Operation(summary = "获取已发布公告", description = "返回当前有效的前N条公告，供首页展示")
    public ApiResponse<List<NoticeResponse>> listPublished(
            @Parameter(description = "返回条数") @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(noticeService.listPublished(limit));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询公告")
    @RequirePermission("system:notice:list")
    public ApiResponse<PageResponse<NoticeResponse>> page(NoticePageQuery query) {
        return ApiResponse.success(noticeService.page(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "按ID查询公告")
    @RequirePermission("system:notice:list")
    public ApiResponse<NoticeResponse> getById(
            @Parameter(description = "公告ID") @PathVariable Long id) {
        NoticeResponse response = noticeService.getById(id);
        if (response == null) {
            return ApiResponse.fail(404, "公告不存在");
        }
        return ApiResponse.success(response);
    }

    @PostMapping
    @Operation(summary = "创建公告")
    @RequirePermission("system:notice:add")
    public ApiResponse<NoticeResponse> create(
            @Parameter(description = "公告信息") @Valid @RequestBody NoticeRequest request) {
        CurrentUserResponse currentUser = currentUserService.getCurrentUser();
        NoticeResponse response = noticeService.create(
                request, currentUser.getUserId(), currentUser.getNickname());
        return ApiResponse.success("公告创建成功", response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新公告")
    @RequirePermission("system:notice:edit")
    public ApiResponse<NoticeResponse> update(
            @Parameter(description = "公告ID") @PathVariable Long id,
            @Parameter(description = "公告信息") @Valid @RequestBody NoticeRequest request) {
        NoticeResponse response = noticeService.update(id, request);
        if (response == null) {
            return ApiResponse.fail(404, "公告不存在");
        }
        return ApiResponse.success("公告更新成功", response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除公告")
    @RequirePermission("system:notice:delete")
    public ApiResponse<Void> delete(
            @Parameter(description = "公告ID") @PathVariable Long id) {
        boolean ok = noticeService.delete(id);
        return ok ? ApiResponse.success("公告已删除", null)
                : ApiResponse.fail(404, "公告不存在");
    }
}
