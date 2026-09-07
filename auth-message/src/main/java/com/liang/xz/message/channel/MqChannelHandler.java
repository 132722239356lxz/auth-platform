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
 * MQ 子系统广播渠道处理器 —— 通过 RabbitMQ 投递 + Redis 缓存双写，供各子系统消费
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqChannelHandler implements MessageChannelHandler {

    private final RabbitMQSender rabbitMQSender;
    private final RedisHelper redisHelper;
    private final MessageRepository messageRepository;

    /** 子系统事件缓存 TTL: 24小时 */
    private static final long CACHE_TTL_SECONDS = 86400;

    @Override
    public MessageChannel getChannel() {
        return MessageChannel.MQ;
    }

    @Override
    public boolean handle(MessageRecord record, List<String> receivers) {
        try {
            log.info("[MQ] 开始投递: messageId={}, targets={}", record.getMessageId(),
                    record.getTargetSubsystems());

            // 投递到 RabbitMQ 子系统队列
            rabbitMQSender.sendToChannel(record, "MQ", receivers);

            // 同时写 Redis 缓存(供无法直连 RabbitMQ 的子系统轮询)
            String cacheKey = "subsystem:events:" + record.getMessageId();
            String payload = String.format(
                    "{\"messageId\":\"%s\",\"type\":\"%s\",\"title\":\"%s\",\"content\":\"%s\"," +
                    "\"sourceSystem\":\"%s\",\"targetSubsystems\":\"%s\",\"timestamp\":%d}",
                    record.getMessageId(), record.getMessageType(), record.getTitle(),
                    record.getContent(), record.getSourceSystem(),
                    record.getTargetSubsystems(), System.currentTimeMillis());
            redisHelper.set(cacheKey, payload, CACHE_TTL_SECONDS, TimeUnit.SECONDS);

            messageRepository.updateStatus(record.getId(),
                    com.liang.xz.message.enums.MessageStatus.SENDING.name(), null);
            log.info("[MQ] 投递成功: messageId={}", record.getMessageId());
            return true;
        } catch (Exception e) {
            log.error("[MQ] 投递异常: messageId={}, error={}", record.getMessageId(), e.getMessage(), e);
            messageRepository.updateStatus(record.getId(),
                    com.liang.xz.message.enums.MessageStatus.FAILED.name(), "MQ投递异常: " + e.getMessage());
            return false;
        }
    }
}
