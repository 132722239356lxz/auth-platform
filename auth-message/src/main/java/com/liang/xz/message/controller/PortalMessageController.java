package com.liang.xz.message.controller;

import com.liang.xz.common.core.annotation.PublicApi;
import com.liang.xz.message.dto.MessageSendRequest;
import com.liang.xz.message.dto.MessageVO;
import com.liang.xz.message.dto.R;
import com.liang.xz.message.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户门户消息接口 —— 普通用户自服务站内信
 *
 * <p>与 {@link MessageController} 管理后台接口分离，不依赖 message:send/message:inbox 等权限注解，
 * 仅要求用户已登录(JWT 认证)，自动从 SecurityContext 解析发送者。</p>
 */
@Tag(name = "用户门户消息", description = "普通用户发送站内信与查询收件箱")
@RestController
@RequestMapping("/api/portal/message")
@PublicApi
@RequiredArgsConstructor
public class PortalMessageController {

    private final MessageService messageService;

    @Operation(summary = "发送站内信")
    @PostMapping("/send")
    public R<String> sendMessage(@Valid @RequestBody MessageSendRequest request) {
        String sender = getCurrentUsername();
        request.setSender(sender);
        request.setSourceSystem("portal");
        if (request.getChannels() == null || request.getChannels().isEmpty()) {
            request.setChannels(List.of("IN_APP"));
        }
        return R.ok(messageService.sendMessage(request));
    }

    @Operation(summary = "收件箱")
    @GetMapping("/inbox")
    public R<List<MessageVO>> inbox(@RequestParam(defaultValue = "50") int limit) {
        String receiver = getCurrentUsername();
        return R.ok(messageService.getInboxMessages(receiver, limit));
    }

    @Operation(summary = "未读消息数")
    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        String receiver = getCurrentUsername();
        return R.ok(messageService.getUnreadCount(receiver));
    }

    @Operation(summary = "标记已读")
    @PutMapping("/{messageId}/read")
    public R<Void> markAsRead(
            @Parameter(description = "消息ID") @PathVariable String messageId) {
        messageService.markAsRead(messageId);
        return R.ok();
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("无法获取当前用户信息");
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return authentication.getName();
    }
}
