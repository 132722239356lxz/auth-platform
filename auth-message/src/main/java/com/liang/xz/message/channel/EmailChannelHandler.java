package com.liang.xz.message.channel;

import com.liang.xz.message.entity.MessageRecord;
import com.liang.xz.message.enums.MessageChannel;
import com.liang.xz.message.repository.MessageRepository;
import com.liang.xz.message.repository.UserContactRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 邮件渠道处理器 —— 关联 sys_user 获取真实邮箱后直发，不经过 RabbitMQ
 *
 * <p>处理流程:</p>
 * <ol>
 *   <li>根据 sender(username) 查询 sys_user 获取发件人邮箱(作为 reply-to 参考)</li>
 *   <li>根据 receivers(username列表) 批量查询 sys_user 获取收件人邮箱</li>
 *   <li>如果 receiver 本身已经是邮箱格式(含@)，直接使用，不再查库</li>
 *   <li>设置 from=spring.mail.username(已认证的 SMTP 发件账号)</li>
 *   <li>调用 JavaMailSender 发送</li>
 * </ol>
 */
@Slf4j
@Component
public class EmailChannelHandler implements MessageChannelHandler {

    /**
     * SMTP 认证发件人邮箱，来自 spring.mail.username 配置
     */
    @Value("${spring.mail.username}")
    private String smtpFrom;

    private final JavaMailSender mailSender;
    private final MessageRepository messageRepository;
    private final UserContactRepository userContactRepository;

    public EmailChannelHandler(JavaMailSender mailSender,
                               MessageRepository messageRepository,
                               UserContactRepository userContactRepository) {
        this.mailSender = mailSender;
        this.messageRepository = messageRepository;
        this.userContactRepository = userContactRepository;
    }

    @Override
    public MessageChannel getChannel() {
        return MessageChannel.EMAIL;
    }

    @Override
    public boolean handle(MessageRecord record, List<String> receivers) {
        if (receivers == null || receivers.isEmpty()) {
            log.warn("[Email] 无收件人: messageId={}", record.getMessageId());
            messageRepository.updateStatus(record.getId(),
                    com.liang.xz.message.enums.MessageStatus.FAILED.name(), "收件人为空");
            return false;
        }

        // 1. 解析收件人 —— 区分"已经是邮箱"和"需要通过用户名查库"
        List<String> needLookup = new ArrayList<>();
        List<String> directEmails = new ArrayList<>();
        for (String r : receivers) {
            String trimmed = r.trim();
            if (UserContactRepository.looksLikeEmail(trimmed)) {
                directEmails.add(trimmed);
            } else {
                needLookup.add(trimmed);
            }
        }

        // 2. 批量查询 sys_user 获取收件人邮箱
        List<String> toEmails = new ArrayList<>(directEmails);
        if (!needLookup.isEmpty()) {
            Map<String, String> emailMap = userContactRepository.findEmailsByUsernames(needLookup);
            for (String username : needLookup) {
                String email = emailMap.get(username);
                if (email != null && !email.isEmpty()) {
                    toEmails.add(email);
                } else {
                    log.warn("[Email] 用户无邮箱: username={}, messageId={}", username, record.getMessageId());
                }
            }
        }

        if (toEmails.isEmpty()) {
            log.warn("[Email] 无有效收件人邮箱: messageId={}, receivers={}", record.getMessageId(), receivers);
            messageRepository.updateStatus(record.getId(),
                    com.liang.xz.message.enums.MessageStatus.FAILED.name(), "无有效收件人邮箱");
            return false;
        }

        try {
            log.info("[Email] 开始发送: messageId={}, from={}, to={}", record.getMessageId(), smtpFrom, toEmails);
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(smtpFrom);
            mail.setSubject(record.getTitle());
            mail.setText(record.getContent());
            mail.setTo(toEmails.toArray(new String[0]));

            // 3. 查找 sender 的真实邮箱作为 display-from (可选，仅日志记录)
            String senderUsername = record.getSender();
            if (senderUsername != null && !senderUsername.isEmpty()) {
                Map<String, String> senderEmailMap = userContactRepository.findEmailsByUsernames(List.of(senderUsername));
                String senderEmail = senderEmailMap.get(senderUsername);
                if (senderEmail != null && !senderEmail.isEmpty() && !senderEmail.equals(smtpFrom)) {
                    log.debug("[Email] sender({}) 的真实邮箱: {}", senderUsername, senderEmail);
                }
            }

            mailSender.send(mail);

            messageRepository.updateStatus(record.getId(),
                    com.liang.xz.message.enums.MessageStatus.SENT.name(), null);
            log.info("[Email] 发送成功: messageId={}, to={}", record.getMessageId(), toEmails);
            return true;
        } catch (Exception e) {
            log.error("[Email] 发送异常: messageId={}, error={}", record.getMessageId(), e.getMessage(), e);
            messageRepository.updateStatus(record.getId(),
                    com.liang.xz.message.enums.MessageStatus.FAILED.name(), "Email发送异常: " + e.getMessage());
            return false;
        }
    }
}
