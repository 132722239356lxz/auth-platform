package com.liang.xz.message.service;

import com.liang.xz.common.core.redis.RedisHelper;
import com.liang.xz.message.entity.MessageRecord;
import com.liang.xz.message.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 消息重试定时任务 —— 定期重试失败的消息
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class MessageRetryScheduler {

    private final MessageService messageService;

    /**
     * 每2分钟检查并重试失败消息
     */
    @Scheduled(fixedDelay = 120_000)
    public void retryMessages() {
        int count = messageService.retryFailedMessages();
        if (count > 0) {
            log.info("[MessageRetry] 本次重试 {} 条消息", count);
        }
    }
}
