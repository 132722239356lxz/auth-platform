package com.liang.xz.message.service;

import com.liang.xz.common.core.async.AsyncHelper;
import com.liang.xz.message.channel.MessageChannelHandler;
import com.liang.xz.message.dto.MessageSendRequest;
import com.liang.xz.message.dto.MessageVO;
import com.liang.xz.message.entity.MessageRecord;
import com.liang.xz.message.entity.MessageTemplate;
import com.liang.xz.message.entity.MessageTemplateStats;
import com.liang.xz.message.enums.MessageStatus;
import com.liang.xz.message.repository.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 消息核心服务 —— 按渠道策略分发(短信/邮件/站内信/实时推送/MQ广播)
 *
 * <p>渠道分发逻辑(策略模式，不再经过 RabbitMQ 中转):</p>
 * <ul>
 *   <li>SMS → SmsChannelHandler → SmsProvider 直发短信</li>
 *   <li>EMAIL → EmailChannelHandler → JavaMailSender 直发邮件</li>
 *   <li>IN_APP → InAppChannelHandler → Redis 缓存(前端轮询)</li>
 *   <li>WEBSOCKET → WebSocketChannelHandler → Redis 推送(跨实例)</li>
 *   <li>MQ → MqChannelHandler → RabbitMQ + Redis 双写(子系统通信)</li>
 * </ul>
 */
@Slf4j
@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final Map<String, MessageChannelHandler> handlerMap;

    public MessageService(MessageRepository messageRepository,
                          List<MessageChannelHandler> handlers) {
        this.messageRepository = messageRepository;
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(
                        h -> h.getChannel().name(),
                        h -> h,
                        (existing, replacement) -> existing));
    }

    // ======================== 1. 消息模板管理 ========================

    @Transactional
    public Long saveTemplate(MessageTemplate template, String createdBy) {
        template.setCreatedBy(createdBy);
        return messageRepository.saveTemplate(template);
    }

    @Transactional
    public void updateTemplate(MessageTemplate template) {
        messageRepository.updateTemplate(template);
    }

    @Transactional
    public void deleteTemplate(String templateCode) {
        messageRepository.deleteTemplate(templateCode);
    }

    public List<MessageTemplate> listTemplates() {
        return messageRepository.findAllTemplates();
    }

    public MessageRepository.TemplateQueryResult queryTemplates(MessageRepository.TemplateQueryParams params) {
        return messageRepository.queryTemplates(params);
    }

    public Optional<MessageTemplate> getTemplate(String templateCode) {
        return messageRepository.findByTemplateCode(templateCode);
    }

    // ======================== 2. 消息发送(按渠道策略分发) ========================

    /**
     * 发送消息 —— 记录入库 → 按渠道分发到对应 ChannelHandler 直发
     */
    @Transactional
    public String sendMessage(MessageSendRequest request) {
        // 渲染模板
        String title = request.getTitle();
        String content = request.getContent();
        if (request.getTemplateCode() != null) {
            MessageTemplate template = messageRepository.findByTemplateCode(request.getTemplateCode())
                    .orElseThrow(() -> new IllegalArgumentException("消息模板不存在: " + request.getTemplateCode()));
            title = renderTemplate(template.getTitleTemplate(), request.getTemplateVars());
            content = renderTemplate(template.getContentTemplate(), request.getTemplateVars());
            if (request.getChannels() == null) {
                request.setChannels(List.of(template.getChannel()));
            }
        }

        String messageId = UUID.randomUUID().toString().replace("-", "");
        List<String> channelList = request.getChannels() != null && !request.getChannels().isEmpty()
                ? request.getChannels() : List.of("IN_APP");
        String channels = String.join(",", channelList);

        // 保存消息记录
        MessageRecord record = MessageRecord.builder()
                .messageId(messageId)
                .messageType(request.getMessageType())
                .title(title)
                .content(content)
                .channels(channels)
                .status(MessageStatus.PENDING.name())
                .sourceSystem(request.getSourceSystem())
                .sender(request.getSender())
                .receivers(request.getReceivers() != null ? String.join(",", request.getReceivers()) : null)
                .businessId(request.getBusinessId())
                .templateCode(request.getTemplateCode())
                .isRead(false)
                .retryCount(0)
                .build();
        messageRepository.saveRecord(record);

        // 按渠道分发(异步, 避免阻塞接口响应)
        final List<String> receivers = request.getReceivers();
        AsyncHelper.runAsync(() -> dispatchByChannel(record, channelList, receivers));

        log.info("[Message] 消息已提交: messageId={}, type={}, channels={}, receivers={}",
                messageId, request.getMessageType(), channels, request.getReceivers());
        return messageId;
    }

    /**
     * 按渠道策略分发 —— 每个渠道由对应的 ChannelHandler 独立处理
     */
    private void dispatchByChannel(MessageRecord record, List<String> channelList,
                                    List<String> receivers) {
        boolean allSuccess = true;
        StringBuilder failReasons = new StringBuilder();
        List<String> validReceivers = receivers != null ? receivers : List.of();

        for (String ch : channelList) {
            String channelName = ch.trim().toUpperCase();
            MessageChannelHandler handler = handlerMap.get(channelName);
            if (handler == null) {
                log.warn("[Message] 不支持的消息渠道: channel={}, messageId={}", channelName, record.getMessageId());
                allSuccess = false;
                failReasons.append(channelName).append(":不支持的渠道; ");
                continue;
            }

            try {
                boolean ok = handler.handle(record, validReceivers);
                if (!ok) {
                    allSuccess = false;
                    failReasons.append(channelName).append(":发送失败; ");
                }
            } catch (Exception e) {
                allSuccess = false;
                failReasons.append(channelName).append(":").append(e.getMessage()).append("; ");
                log.error("[Message] 渠道分发异常: channel={}, msgId={}", channelName, record.getMessageId(), e);
            }
        }

        // 汇总更新最终状态(各 handler 内部可能已更新过，此处做兜底)
        if (!allSuccess) {
            String reason = failReasons.toString();
            messageRepository.updateStatus(record.getId(),
                    channelList.size() > 1 ? MessageStatus.PARTIAL.name() : MessageStatus.FAILED.name(), reason);
        }
    }

    // ======================== 3. 消息重试 ========================

    /**
     * 重试失败的消息(由定时任务调用)
     */
    public int retryFailedMessages() {
        int maxRetry = 3;
        List<MessageRecord> failedMessages = messageRepository.findFailedMessages(maxRetry);
        for (MessageRecord msg : failedMessages) {
            messageRepository.incrementRetryCount(msg.getId());
            messageRepository.updateStatus(msg.getId(), MessageStatus.PENDING.name(), null);
            // 重新按渠道分发
            List<String> receiverList = msg.getReceivers() != null
                    ? Arrays.asList(msg.getReceivers().split(",")) : List.of();
            List<String> retryChannelList = msg.getChannels() != null
                    ? Arrays.asList(msg.getChannels().split(",")) : List.of("IN_APP");
            AsyncHelper.runAsync(() -> dispatchByChannel(msg, retryChannelList, receiverList));
        }
        int count = failedMessages.size();
        if (count > 0) {
            log.info("[Message] 重试失败消息: count={}", count);
        }
        return count;
    }

    // ======================== 4. 查询接口 ========================

    public MessageVO getMessage(String messageId) {
        return messageRepository.findByMessageId(messageId)
                .map(this::toVO).orElse(null);
    }

    public List<MessageVO> getInboxMessages(String receiver, int limit) {
        return messageRepository.findByReceiver(receiver, limit).stream()
                .map(this::toVO).collect(Collectors.toList());
    }

    public List<MessageVO> getMessagesByBusinessId(String businessId) {
        return messageRepository.findByBusinessId(businessId).stream()
                .map(this::toVO).collect(Collectors.toList());
    }

    public void markAsRead(String messageId) {
        MessageRecord record = messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new IllegalArgumentException("消息不存在"));
        messageRepository.updateReadStatus(record.getId(), true);
    }

    public long getUnreadCount(String receiver) {
        List<MessageRecord> list = messageRepository.findByReceiver(receiver, 500);
        return list.stream().filter(r -> !Boolean.TRUE.equals(r.getIsRead())).count();
    }

    // ======================== 5. 分页条件查询 ========================

    public MessageRepository.MessageQueryResult queryMessages(MessageRepository.MessageQueryParams params) {
        return messageRepository.queryMessages(params);
    }

    @Transactional
    public void deleteMessage(String messageId) {
        messageRepository.deleteByMessageId(messageId);
    }

    // ======================== 工具方法 ========================

    private String renderTemplate(String template, Map<String, String> vars) {
        if (template == null) return "";
        if (vars == null || vars.isEmpty()) return template;
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private MessageVO toVO(MessageRecord r) {
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
