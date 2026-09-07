package com.liang.xz.message.service;

import com.liang.xz.common.core.async.AsyncHelper;
import com.liang.xz.message.channel.MessageChannelHandler;
import com.liang.xz.message.dto.MessageBroadcastRequest;
import com.liang.xz.message.entity.MessageRecord;
import com.liang.xz.message.entity.MessageTemplate;
import com.liang.xz.message.enums.MessageStatus;
import com.liang.xz.message.repository.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 消息广播服务 —— 按渠道策略分发(事件推送/子系统上行/广播)
 *
 * <p>实现原理(策略模式，渠道各自独立处理):</p>
 * <ol>
 *   <li>消息记录入库</li>
 *   <li>按 channels 配置分发到对应 ChannelHandler 独立处理</li>
 *   <li>SMS/EMAIL/INAPP/WS 直发，MQ 走 RabbitMQ+Redis 双写</li>
 *   <li>支持子系统上行通信、事件推送、跨系统解耦</li>
 * </ol>
 */
@Slf4j
@Service
public class MessageBroadcastService {

    private final MessageRepository messageRepository;
    private final Map<String, MessageChannelHandler> handlerMap;

    public MessageBroadcastService(MessageRepository messageRepository,
                                    List<MessageChannelHandler> handlers) {
        this.messageRepository = messageRepository;
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(
                        h -> h.getChannel().name(),
                        h -> h,
                        (existing, replacement) -> existing));
    }

    /**
     * 广播消息到多个子系统(通过 RabbitMQ Fanout + Direct)
     */
    @Transactional
    public String broadcastToSubsystems(MessageBroadcastRequest request) {
        String messageId = UUID.randomUUID().toString().replace("-", "");
        List<String> channelList = request.getChannels() != null && !request.getChannels().isEmpty()
                ? request.getChannels() : List.of("MQ", "WEBSOCKET");

        // 渲染模板
        String title = request.getTitle();
        String content = request.getContent();
        if (request.getTemplateCode() != null && !request.getTemplateCode().isEmpty()) {
            MessageTemplate template = messageRepository.findByTemplateCode(request.getTemplateCode())
                    .orElseThrow(() -> new IllegalArgumentException("消息模板不存在: " + request.getTemplateCode()));
            title = renderTemplate(template.getTitleTemplate(), request.getTemplateVars());
            content = renderTemplate(template.getContentTemplate(), request.getTemplateVars());
        }
        String channels = String.join(",", channelList);

        // 保存广播记录
        MessageRecord record = MessageRecord.builder()
                .messageId(messageId)
                .messageType(request.getMessageType())
                .title(title)
                .content(content)
                .channels(channels)
                .status(MessageStatus.SENDING.name())
                .sourceSystem(request.getSourceSystem())
                .sender("BROADCAST")
                .targetSubsystems(request.getTargetSubsystems() != null
                        ? String.join(",", request.getTargetSubsystems()) : null)
                .businessId(request.getBusinessId())
                .isRead(false)
                .retryCount(0)
                .build();
        messageRepository.saveRecord(record);

        // 异步按渠道分发广播
        AsyncHelper.runAsync(() -> dispatchByChannel(record, channelList, request));

        log.info("[Broadcast] 消息已广播: messageId={}, type={}, targets={}",
                messageId, request.getMessageType(), request.getTargetSubsystems());
        return messageId;
    }

    /**
     * 按渠道策略分发广播消息
     */
    private void dispatchByChannel(MessageRecord record, List<String> channelList,
                                    MessageBroadcastRequest request) {
        for (String ch : channelList) {
            String channelName = ch.trim().toUpperCase();
            MessageChannelHandler handler = handlerMap.get(channelName);
            if (handler == null) {
                log.warn("[Broadcast] 不支持的消息渠道: channel={}, messageId={}",
                        channelName, record.getMessageId());
                continue;
            }

            try {
                // 广播模式: receivers 传空, 由各 handler 根据 targetSubsystems 自行处理
                handler.handle(record, request.getTargetSubsystems());
            } catch (Exception e) {
                log.error("[Broadcast] 渠道分发异常: channel={}, msgId={}",
                        channelName, record.getMessageId(), e);
            }
        }
    }

    /**
     * 子系统上行通信
     */
    @Transactional
    public String subsystemUpward(String sourceSubsystem, String eventType,
                                   String payload, List<String> targetSubsystems) {
        MessageBroadcastRequest request = new MessageBroadcastRequest();
        request.setMessageType("SUBSYSTEM_COMM");
        request.setTitle("[" + sourceSubsystem + "] " + eventType);
        request.setContent(payload);
        request.setSourceSystem(sourceSubsystem);
        request.setTargetSubsystems(targetSubsystems);
        request.setEventType(eventType);
        request.setChannels(List.of("MQ"));
        return broadcastToSubsystems(request);
    }

    /**
     * 事件推送
     */
    @Transactional
    public String pushEvent(String eventType, String title, String content,
                            String sourceSystem, List<String> targetSubsystems) {
        MessageBroadcastRequest request = new MessageBroadcastRequest();
        request.setMessageType("EVENT_PUSH");
        request.setTitle(title);
        request.setContent(content);
        request.setSourceSystem(sourceSystem);
        request.setTargetSubsystems(targetSubsystems);
        request.setEventType(eventType);
        request.setChannels(List.of("MQ", "WEBSOCKET"));
        return broadcastToSubsystems(request);
    }

    /**
     * 查询某个子系统收到的事件
     */
    public List<com.liang.xz.message.dto.MessageVO> getSubsystemEvents(String subsystem, int limit) {
        return messageRepository.findBySubsystem(subsystem, limit).stream()
                .map(r -> com.liang.xz.message.dto.MessageVO.builder()
                        .messageId(r.getMessageId()).messageType(r.getMessageType())
                        .title(r.getTitle()).content(r.getContent())
                        .status(r.getStatus()).sourceSystem(r.getSourceSystem())
                        .targetSubsystems(r.getTargetSubsystems())
                        .businessId(r.getBusinessId())
                        .createTime(r.getCreateTime()).sendTime(r.getSendTime())
                        .build())
                .toList();
    }

    private String renderTemplate(String template, Map<String, String> vars) {
        if (template == null) return "";
        if (vars == null || vars.isEmpty()) return template;
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }
}
