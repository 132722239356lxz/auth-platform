package com.liang.xz.message.channel;

import com.liang.xz.common.core.redis.RedisHelper;
import com.liang.xz.message.entity.MessageRecord;
import com.liang.xz.message.enums.MessageChannel;
import com.liang.xz.message.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket 实时推送渠道处理器 —— 直接写 Redis Pub/Sub，不经过 RabbitMQ
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketChannelHandler implements MessageChannelHandler {

    private final RedisHelper redisHelper;
    private final MessageRepository messageRepository;

    /** WebSocket 推送消息 TTL: 5分钟 */
    private static final long PUSH_TTL_SECONDS = 300;

    @Override
    public MessageChannel getChannel() {
        return MessageChannel.WEBSOCKET;
    }

    @Override
    public boolean handle(MessageRecord record, List<String> receivers) {
        try {
            log.info("[WebSocket] 开始推送: messageId={}, receivers={}", record.getMessageId(), receivers);
            for (String receiver : receivers) {
                String pushMsg = String.format(
                        "{\"messageId\":\"%s\",\"title\":\"%s\",\"type\":\"%s\",\"timestamp\":%d}",
                        record.getMessageId(), record.getTitle(),
                        record.getMessageType(), System.currentTimeMillis());
                redisHelper.set("ws:push:" + receiver.trim() + ":" + record.getMessageId(),
                        pushMsg, PUSH_TTL_SECONDS, TimeUnit.SECONDS);
            }
            messageRepository.updateStatus(record.getId(),
                    com.liang.xz.message.enums.MessageStatus.SENT.name(), null);
            log.info("[WebSocket] 推送已发布: messageId={}, count={}", record.getMessageId(), receivers.size());
            return true;
        } catch (Exception e) {
            log.error("[WebSocket] 推送异常: messageId={}, error={}", record.getMessageId(), e.getMessage(), e);
            messageRepository.updateStatus(record.getId(),
                    com.liang.xz.message.enums.MessageStatus.FAILED.name(), "WebSocket推送异常: " + e.getMessage());
            return false;
        }
    }
}
