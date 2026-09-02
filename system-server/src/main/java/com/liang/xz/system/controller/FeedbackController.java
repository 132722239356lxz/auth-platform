package com.liang.xz.system.controller;

import com.liang.xz.system.dto.ApiResponse;
import com.liang.xz.system.dto.FeedbackRequest;
import com.liang.xz.system.entity.FeedbackEntity;
import com.liang.xz.system.repository.FeedbackRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 意见反馈控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
@Tag(name = "意见反馈", description = "用户意见反馈提交与查询")
public class FeedbackController {

    private final FeedbackRepository feedbackRepository;

    @PostMapping
    @Operation(summary = "提交意见反馈")
    public ApiResponse<Void> submit(
            @Valid @RequestBody FeedbackRequest request,
            Principal principal) {
        FeedbackEntity entity = FeedbackEntity.builder()
                .username(principal.getName())
                .content(request.getContent())
                .contact(request.getContact())
                .type(request.getType() != null ? request.getType() : "suggestion")
                .status("pending")
                .build();
        feedbackRepository.insert(entity);
        log.info("[Feedback] 用户反馈提交: username={}, type={}",
                principal.getName(), request.getType());
        return ApiResponse.success("感谢您的反馈，我们会尽快处理！", null);
    }

    @GetMapping
    @Operation(summary = "查询我的反馈")
    public ApiResponse<List<Map<String, Object>>> myFeedback(Principal principal) {
        List<FeedbackEntity> list = feedbackRepository.findByUsername(principal.getName());
        List<Map<String, Object>> result = list.stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getId());
            m.put("content", f.getContent());
            m.put("contact", f.getContact());
            m.put("type", f.getType());
            m.put("status", f.getStatus());
            m.put("createTime", f.getCreateTime());
            return m;
        }).toList();
        return ApiResponse.success(result);
    }

    @GetMapping("/version")
    @Operation(summary = "获取版本信息")
    public ApiResponse<Map<String, Object>> version() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("version", "v1.1.0");
        info.put("buildTime", "2026-07-11");
        info.put("changelog", List.of(
                Map.of("version", "v1.1.0", "date", "2026-07-11", "changes", List.of(
                        "新增个人中心：头像设置、资料修改、密码修改",
                        "新增用户注册页面",
                        "新增设置页面：版本说明、意见反馈",
                        "优化 Token 自动刷新机制"
                )),
                Map.of("version", "v1.0.0", "date", "2026-06-01", "changes", List.of(
                        "统一授权中台初始版本",
                        "支持 OAuth2 授权码模式 & 密码模式",
                        "支持用户/角色/菜单管理 (RBAC)",
                        "支持工作流审批 & 消息中心",
                        "支持 AI 智能搜索 & 知识库管理",
                        "支持操作日志 & AI 日志分析"
                ))
        ));
        return ApiResponse.success(info);
    }
}
