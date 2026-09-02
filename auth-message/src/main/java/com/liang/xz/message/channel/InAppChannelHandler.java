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
 * 站内信渠道处理器 —— 直接写入 Redis 缓存，前端轮询获取，不经过 RabbitMQ
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InAppChannelHandler implements MessageChannelHandler {

    private final RedisHelper redisHelper;
    private final MessageRepository messageRepository;

    /** 站内信缓存过期时间: 7天 */
    private static final long INBOX_TTL_SECONDS = 604800;

    @Override
    public MessageChannel getChannel() {
        return MessageChannel.IN_APP;
    }

    @Override
    public boolean handle(MessageRecord record, List<String> receivers) {
        try {
            log.info("[InApp] 写入站内信: messageId={}, receivers={}", record.getMessageId(), receivers);
            for (String receiver : receivers) {
                String cacheKey = "message:inbox:" + receiver.trim() + ":" + record.getMessageId();
                String value = record.getTitle() + "|" + record.getContent();
                redisHelper.set(cacheKey, value, INBOX_TTL_SECONDS, TimeUnit.SECONDS);
            }
            messageRepository.updateStatus(record.getId(),
                    com.liang.xz.message.enums.MessageStatus.SENT.name(), null);
            log.info("[InApp] 站内信已投递: messageId={}, count={}", record.getMessageId(), receivers.size());
            return true;
        } catch (Exception e) {
            log.error("[InApp] 写入异常: messageId={}, error={}", record.getMessageId(), e.getMessage(), e);
            messageRepository.updateStatus(record.getId(),
                    com.liang.xz.message.enums.MessageStatus.FAILED.name(), "InApp写入异常: " + e.getMessage());
            return false;
        }
    }
}
