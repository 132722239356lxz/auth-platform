package com.liang.xz.message.controller;

import com.liang.xz.common.core.annotation.PublicApi;
import com.liang.xz.message.dto.*;
import com.liang.xz.message.entity.MessageTemplate;
import com.liang.xz.message.entity.MessageTemplateStats;
import com.liang.xz.message.service.MessageBroadcastService;
import com.liang.xz.message.service.MessageService;
import com.liang.xz.resource.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 消息服务 API
 */
@Tag(name = "消息服务", description = "消息发送/广播/模板管理/站内信")
@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final MessageBroadcastService broadcastService;

    // ======================== 消息发送 ========================

    @Operation(summary = "发送消息(单发/群发)")
    @RequirePermission("message:send")
    @PostMapping("/send")
    public R<String> sendMessage(@Valid @RequestBody MessageSendRequest request) {
        return R.ok(messageService.sendMessage(request));
    }

    @Operation(summary = "广播消息到多个子系统")
    @RequirePermission("message:broadcast")
    @PostMapping("/broadcast")
    public R<String> broadcast(@Valid @RequestBody MessageBroadcastRequest request) {
        return R.ok(broadcastService.broadcastToSubsystems(request));
    }

    @Operation(summary = "子系统上行通信")
    @PublicApi
    @PostMapping("/subsystem/upward")
    public R<String> subsystemUpward(
            @Parameter(description = "来源子系统") @RequestParam String sourceSubsystem,
            @Parameter(description = "事件类型") @RequestParam String eventType,
            @Parameter(description = "消息内容(JSON)") @RequestParam String payload,
            @Parameter(description = "目标子系统列表") @RequestParam List<String> targetSubsystems) {
        return R.ok(broadcastService.subsystemUpward(sourceSubsystem, eventType, payload, targetSubsystems));
    }

    @Operation(summary = "事件推送")
    @PublicApi
    @PostMapping("/event/push")
    public R<String> pushEvent(
            @Parameter(description = "事件类型") @RequestParam String eventType,
            @Parameter(description = "标题") @RequestParam String title,
            @Parameter(description = "内容") @RequestParam String content,
            @Parameter(description = "来源系统") @RequestParam String sourceSystem,
            @Parameter(description = "目标子系统") @RequestParam List<String> targetSubsystems) {
        return R.ok(broadcastService.pushEvent(eventType, title, content, sourceSystem, targetSubsystems));
    }

    // ======================== 查询 ========================

    @Operation(summary = "查询消息详情")
    @RequirePermission("message:inbox")
    @GetMapping("/{messageId}")
    public R<MessageVO> getMessage(@PathVariable String messageId) {
        return R.ok(messageService.getMessage(messageId));
    }

    @Operation(summary = "收件箱(站内信列表)")
    @RequirePermission("message:inbox")
    @GetMapping("/inbox/{receiver}")
    public R<List<MessageVO>> inbox(@PathVariable String receiver,
                                     @RequestParam(defaultValue = "50") int limit) {
        return R.ok(messageService.getInboxMessages(receiver, limit));
    }

    @Operation(summary = "按业务ID查询消息")
    @RequirePermission("message:detail")
    @GetMapping("/business/{businessId}")
    public R<List<MessageVO>> byBusinessId(@PathVariable String businessId) {
        return R.ok(messageService.getMessagesByBusinessId(businessId));
    }

    @Operation(summary = "查询子系统事件")
    @RequirePermission("message:event:list")
    @GetMapping("/subsystem/{subsystem}/events")
    public R<List<MessageVO>> subsystemEvents(@PathVariable String subsystem,
                                               @RequestParam(defaultValue = "50") int limit) {
        return R.ok(broadcastService.getSubsystemEvents(subsystem, limit));
    }

    @Operation(summary = "未读消息数")
    @RequirePermission("message:inbox")
    @GetMapping("/unread-count/{receiver}")
    public R<Long> unreadCount(@PathVariable String receiver) {
        return R.ok(messageService.getUnreadCount(receiver));
    }

    @Operation(summary = "标记已读")
    @RequirePermission("message:inbox")
    @PutMapping("/{messageId}/read")
    public R<Void> markAsRead(@PathVariable String messageId) {
        messageService.markAsRead(messageId);
        return R.ok();
    }

    // ======================== 消息模板管理 ========================

    @Operation(summary = "创建消息模板")
    @RequirePermission("message:template:add")
    @PostMapping("/template")
    public R<Long> createTemplate(@RequestBody MessageTemplate template,
                                   @RequestHeader(value = "X-User", defaultValue = "admin") String createdBy) {
        return R.ok(messageService.saveTemplate(template, createdBy));
    }

    @Operation(summary = "分页条件查询模板列表（联表 msg_record 统计使用次数）")
    @RequirePermission("message:template:list")
    @GetMapping("/template/list")
    public R<java.util.Map<String, Object>> listTemplates(
            @Parameter(description = "关键词（匹配模板编码/名称）") @RequestParam(required = false) String keyword,
            @Parameter(description = "模板编码") @RequestParam(required = false) String templateCode,
            @Parameter(description = "渠道") @RequestParam(required = false) String channel,
            @Parameter(description = "状态：0-停用 1-启用") @RequestParam(required = false) Integer status,
            @Parameter(description = "创建人") @RequestParam(required = false) String createdBy,
            @Parameter(description = "开始时间") @RequestParam(required = false) String startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) String endTime,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {
        com.liang.xz.message.repository.MessageRepository.TemplateQueryParams params =
                new com.liang.xz.message.repository.MessageRepository.TemplateQueryParams();
        params.setKeyword(keyword);
        params.setTemplateCode(templateCode);
        params.setChannel(channel);
        params.setStatus(status);
        params.setCreatedBy(createdBy);
        if (startTime != null && !startTime.isEmpty()) {
            params.setStartTime(java.time.LocalDateTime.parse(startTime));
        }
        if (endTime != null && !endTime.isEmpty()) {
            params.setEndTime(java.time.LocalDateTime.parse(endTime));
        }
        params.setPage(page);
        params.setSize(size);

        var result = messageService.queryTemplates(params);
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("total", result.getTotal());
        map.put("list", result.getList().stream().map(this::convertToTemplateVO).toList());
        return R.ok(map);
    }

    @Operation(summary = "更新消息模板")
    @RequirePermission("message:template:edit")
    @PutMapping("/template/{templateCode}")
    public R<Void> updateTemplate(@PathVariable String templateCode,
                                   @RequestBody MessageTemplate template) {
        template.setTemplateCode(templateCode);
        messageService.updateTemplate(template);
        return R.ok();
    }

    @Operation(summary = "删除消息模板")
    @RequirePermission("message:template:delete")
    @DeleteMapping("/template/{templateCode}")
    public R<Void> deleteTemplate(@PathVariable String templateCode) {
        messageService.deleteTemplate(templateCode);
        return R.ok();
    }

    // ======================== 分页条件查询 ========================

    @Operation(summary = "分页条件查询消息列表(发送记录)")
    @RequirePermission("message:record:list")
    @GetMapping("/list")
    public R<java.util.Map<String, Object>> queryMessages(
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "消息类型") @RequestParam(required = false) String messageType,
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "渠道") @RequestParam(required = false) String channels,
            @Parameter(description = "接收人") @RequestParam(required = false) String receiver,
            @Parameter(description = "开始时间") @RequestParam(required = false) String startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) String endTime,
            @Parameter(description = "是否已读") @RequestParam(required = false) Boolean isRead,
            @Parameter(description = "排除渠道，逗号分隔") @RequestParam(required = false) String excludeChannels,
            @Parameter(description = "发送人") @RequestParam(required = false) String sender,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {
        com.liang.xz.message.repository.MessageRepository.MessageQueryParams params =
                new com.liang.xz.message.repository.MessageRepository.MessageQueryParams();
        params.setKeyword(keyword);
        params.setMessageType(messageType);
        params.setStatus(status);
        params.setChannels(channels);
        params.setReceiver(receiver);
        params.setIsRead(isRead);
        if (excludeChannels != null && !excludeChannels.isEmpty()) {
            params.setExcludeChannels(java.util.List.of(excludeChannels.split(",")));
        }
        params.setSender(sender);
        if (startTime != null && !startTime.isEmpty()) {
            params.setStartTime(java.time.LocalDateTime.parse(startTime));
        }
        if (endTime != null && !endTime.isEmpty()) {
            params.setEndTime(java.time.LocalDateTime.parse(endTime));
        }
        params.setPage(page);
        params.setSize(size);

        var result = messageService.queryMessages(params);
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("total", result.getTotal());
        map.put("list", result.getList().stream().map(r -> convertToVO(r)).toList());
        return R.ok(map);
    }

    @Operation(summary = "删除消息")
    @RequirePermission("message:delete")
    @DeleteMapping("/{messageId}")
    public R<Void> deleteMessage(@PathVariable String messageId) {
        messageService.deleteMessage(messageId);
        return R.ok();
    }

    private MessageTemplateStats convertToTemplateVO(MessageTemplateStats t) {
        // 保留原始扩展字段
        return t;
    }

    private MessageVO convertToVO(com.liang.xz.message.entity.MessageRecord r) {
        return MessageVO.builder()
                .messageId(r.getMessageId()).messageType(r.getMessageType())
                .title(r.getTitle()).content(r.getContent()).channels(r.getChannels())
                .status(r.getStatus()).sourceSystem(r.getSourceSystem())
                .sender(r.getSender()).receivers(r.getReceivers())
                .targetSubsystems(r.getTargetSubsystems()).businessId(r.getBusinessId())
                .isRead(r.getIsRead()).failReason(r.getFailReason()).retryCount(r.getRetryCount())
                .createTime(r.getCreateTime()).sendTime(r.getSendTime()).readTime(r.getReadTime())
                .build();
    }
}
