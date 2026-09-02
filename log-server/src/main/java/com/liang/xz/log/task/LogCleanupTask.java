package com.liang.xz.log.task;

import com.liang.xz.log.service.LogQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 日志定时清理任务
 *
 * @author liang
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogCleanupTask {

    private final LogQueryService logQueryService;

    /** 日志保留天数(默认30天) */
    @Value("${log.cleanup.retention-days:30}")
    private int retentionDays;

    /**
     * 每天凌晨 3:00 清理过期日志
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanExpiredLogs() {
        log.info("[log-server] 开始定时清理过期日志, 保留 {} 天", retentionDays);
        try {
            int deleted = logQueryService.cleanExpired(retentionDays, 500);
            log.info("[log-server] 定时清理完成, 删除 {} 条日志", deleted);
        } catch (Exception e) {
            log.error("[log-server] 定时清理日志失败", e);
        }
    }
}
