package com.liang.xz.message.channel;

import com.liang.xz.message.entity.MessageRecord;
import com.liang.xz.message.enums.MessageChannel;
import com.liang.xz.message.repository.MessageRepository;
import com.liang.xz.message.repository.UserContactRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 短信渠道处理器 —— 关联 sys_user 获取手机号后直发，不经过 RabbitMQ
 *
 * <p>处理流程:</p>
 * <ol>
 *   <li>根据 receivers(username列表) 批量查询 sys_user 获取手机号</li>
 *   <li>如果 receiver 本身已是手机号格式(纯数字7-15位)，直接使用</li>
 *   <li>调用 SmsProvider 发送</li>
 * </ol>
 */
@Slf4j
@Component
public class SmsChannelHandler implements MessageChannelHandler {

    private final SmsProvider smsProvider;
    private final MessageRepository messageRepository;
    private final UserContactRepository userContactRepository;

    public SmsChannelHandler(SmsProvider smsProvider,
                              MessageRepository messageRepository,
                              UserContactRepository userContactRepository) {
        this.smsProvider = smsProvider;
        this.messageRepository = messageRepository;
        this.userContactRepository = userContactRepository;
    }

    @Override
    public MessageChannel getChannel() {
        return MessageChannel.SMS;
    }

    @Override
    public boolean handle(MessageRecord record, List<String> receivers) {
        if (receivers == null || receivers.isEmpty()) {
            log.warn("[SMS] 无收件人: messageId={}", record.getMessageId());
            messageRepository.updateStatus(record.getId(),
                    com.liang.xz.message.enums.MessageStatus.FAILED.name(), "收件人为空");
            return false;
        }

        // 1. 区分"已经是手机号"和"需要通过用户名查库"
        List<String> needLookup = new ArrayList<>();
        List<String> directPhones = new ArrayList<>();
        for (String r : receivers) {
            String trimmed = r.trim();
            if (UserContactRepository.looksLikePhone(trimmed)) {
                directPhones.add(trimmed);
            } else {
                needLookup.add(trimmed);
            }
        }

        // 2. 批量查询 sys_user 获取手机号
        List<String> phones = new ArrayList<>(directPhones);
        if (!needLookup.isEmpty()) {
            Map<String, String> phoneMap = userContactRepository.findPhonesByUsernames(needLookup);
            for (String username : needLookup) {
                String phone = phoneMap.get(username);
                if (phone != null && !phone.isEmpty()) {
                    phones.add(phone);
                } else {
                    log.warn("[SMS] 用户无手机号: username={}, messageId={}", username, record.getMessageId());
                }
            }
        }

        if (phones.isEmpty()) {
            log.warn("[SMS] 无有效收件人手机号: messageId={}, receivers={}", record.getMessageId(), receivers);
            messageRepository.updateStatus(record.getId(),
                    com.liang.xz.message.enums.MessageStatus.FAILED.name(), "无有效收件人手机号");
            return false;
        }

        try {
            log.info("[SMS] 开始发送: messageId={}, phones={}", record.getMessageId(), phones);
            boolean ok = smsProvider.send(phones, "AUTH_NOTIFY", List.of(record.getContent()));
            if (ok) {
                messageRepository.updateStatus(record.getId(),
                        com.liang.xz.message.enums.MessageStatus.SENT.name(), null);
                log.info("[SMS] 发送成功: messageId={}", record.getMessageId());
            } else {
                messageRepository.updateStatus(record.getId(),
                        com.liang.xz.message.enums.MessageStatus.FAILED.name(), "SMS厂商返回失败");
                log.warn("[SMS] 发送失败: messageId={}", record.getMessageId());
            }
            return ok;
        } catch (Exception e) {
            log.error("[SMS] 发送异常: messageId={}, error={}", record.getMessageId(), e.getMessage(), e);
            messageRepository.updateStatus(record.getId(),
                    com.liang.xz.message.enums.MessageStatus.FAILED.name(), "SMS发送异常: " + e.getMessage());
            return false;
        }
    }
}
