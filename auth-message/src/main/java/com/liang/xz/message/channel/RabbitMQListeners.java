package com.liang.xz.message.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.common.core.redis.RedisHelper;
import com.liang.xz.message.config.RabbitMQConfig;
import com.liang.xz.message.entity.MessageRecord;
import com.liang.xz.message.enums.MessageStatus;
import com.liang.xz.message.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * RabbitMQ 消息监听器 —— 消费各渠道队列并执行真正的发送逻辑
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMQListeners {

    private final MessageRepository messageRepository;
    private final RedisHelper redisHelper;
    private final JavaMailSender mailSender;
    private final SmsProvider smsProvider;
    private final ObjectMapper objectMapper;

    /**
     * 监听短信队列
     */
    @RabbitListener(queues = RabbitMQConfig.SMS_QUEUE, concurrency = "3-5")
    public void onSmsMessage(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(message, Map.class);
            String messageId = (String) payload.get("messageId");
            @SuppressWarnings("unchecked")
            List<String> receivers = (List<String>) payload.get("receivers");
            String content = (String) payload.get("content");

            boolean ok = smsProvider.send(receivers, "AUTH_NOTIFY", List.of(content));
            updateRecordStatus(messageId, ok);
        } catch (Exception e) {
            log.error("[SMS Listener] 处理失败: {}", e.getMessage());
            throw new RuntimeException("SMS处理失败", e); // 抛出异常触发重试/死信
        }
    }

    /**
     * 监听邮件队列
     */
    @RabbitListener(queues = RabbitMQConfig.EMAIL_QUEUE, concurrency = "3-5")
    public void onEmailMessage(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(message, Map.class);
            String messageId = (String) payload.get("messageId");
            String title = (String) payload.get("title");
            String content = (String) payload.get("content");
            @SuppressWarnings("unchecked")
            List<String> receivers = (List<String>) payload.get("receivers");

            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setSubject(title);
            mail.setText(content);
            mail.setTo(receivers.toArray(new String[0]));
            // mail.setFrom("noreply@example.com"); // 需配置 spring.mail.username
            mailSender.send(mail);

            updateRecordStatus(messageId, true);
            log.info("[Email] 邮件已发送: messageId={}, to={}", messageId, receivers);
        } catch (Exception e) {
            log.error("[Email Listener] 处理失败: {}", e.getMessage());
            throw new RuntimeException("Email处理失败", e);
        }
    }

    /**
     * 监听站内信队列
     */
    @RabbitListener(queues = RabbitMQConfig.IN_APP_QUEUE, concurrency = "5-10")
    public void onInAppMessage(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(message, Map.class);
            String messageId = (String) payload.get("messageId");
            String title = (String) payload.get("title");
            String content = (String) payload.get("content");
            @SuppressWarnings("unchecked")
            List<String> receivers = (List<String>) payload.get("receivers");

            // 站内信: 写入Redis缓存, 前端轮询获取
            for (String receiver : receivers) {
                String cacheKey = "message:inbox:" + receiver.trim();
                redisHelper.set(cacheKey + ":" + messageId,
                        title + "|" + content, 604800, TimeUnit.SECONDS);
            }
            log.info("[InApp] 站内信已投递: messageId={}, receivers={}", messageId, receivers);
        } catch (Exception e) {
            log.error("[InApp Listener] 处理失败: {}", e.getMessage());
        }
    }

    /**
     * 监听 WebSocket 推送队列
     */
    @RabbitListener(queues = RabbitMQConfig.WEBSOCKET_QUEUE, concurrency = "5-10")
    public void onWebSocketMessage(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(message, Map.class);
            String messageId = (String) payload.get("messageId");
            String title = (String) payload.get("title");
            @SuppressWarnings("unchecked")
            List<String> receivers = (List<String>) payload.get("receivers");

            // WebSocket推送: 通过Redis Pub/Sub实现跨实例推送
            for (String receiver : receivers) {
                String pushMsg = String.format("{\"messageId\":\"%s\",\"title\":\"%s\",\"type\":\"%s\"}",
                        messageId, title, payload.get("messageType"));
                redisHelper.set("ws:push:" + receiver.trim() + ":" + messageId,
                        pushMsg, 300, TimeUnit.SECONDS);
            }
            log.info("[WebSocket] 推送已发布: messageId={}, receivers={}", messageId, receivers);
        } catch (Exception e) {
            log.error("[WebSocket Listener] 处理失败: {}", e.getMessage());
        }
    }

    /**
     * 监听子系统广播队列
     */
    @RabbitListener(queues = RabbitMQConfig.SUBSYSTEM_QUEUE, concurrency = "3-5")
    public void onSubsystemMessage(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(message, Map.class);
            String messageId = (String) payload.get("messageId");
            // 子系统间消息: 写入Redis供各子系统轮询/订阅
            String cacheKey = "subsystem:events:" + messageId;
            redisHelper.set(cacheKey, message, 86400, TimeUnit.SECONDS);
            log.info("[Subsystem] 子系统消息已缓存: messageId={}", messageId);
        } catch (Exception e) {
            log.error("[Subsystem Listener] 处理失败: {}", e.getMessage());
        }
    }

    /**
     * 监听死信队列(失败超过重试次数的消息)
     */
    @RabbitListener(queues = RabbitMQConfig.DLQ_QUEUE)
    public void onDeadLetter(String message) {
        log.warn("[DLQ] 收到死信消息: {}", message);
        // 可持久化存储、告警通知等
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(message, Map.class);
            String messageId = (String) payload.get("messageId");
            messageRepository.findById(getRecordIdByMessageId(messageId)).ifPresent(record -> {
                messageRepository.updateStatus(record.getId(),
                        MessageStatus.FAILED.name(), "超过最大重试次数, 已进入死信队列");
            });
        } catch (Exception ignored) {
        }
    }

    private void updateRecordStatus(String messageId, boolean success) {
        messageRepository.findByMessageId(messageId).ifPresent(record -> {
            if (success) {
                messageRepository.updateStatus(record.getId(), MessageStatus.SENT.name(), null);
            } else {
                messageRepository.updateStatus(record.getId(), MessageStatus.FAILED.name(), "渠道发送失败");
            }
        });
    }

    private Long getRecordIdByMessageId(String messageId) {
        return messageRepository.findByMessageId(messageId)
                .map(MessageRecord::getId).orElse(null);
    }
}
