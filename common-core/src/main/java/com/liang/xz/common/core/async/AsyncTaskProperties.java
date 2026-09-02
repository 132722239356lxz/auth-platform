package com.liang.xz.common.core.async;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * <p>异步任务线程池配置属性</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "async.task.executor")
public class AsyncTaskProperties {

    /** 核心线程数 */
    private int corePoolSize = Runtime.getRuntime().availableProcessors();
    /** 最大线程数 */
    private int maxPoolSize = Runtime.getRuntime().availableProcessors() * 2;
    /** 队列容量 */
    private int queueCapacity = 200;
    /** 空闲线程存活时间(秒) */
    private int keepAliveSeconds = 60;
    /** 线程名前缀 */
    private String threadNamePrefix = "async-task-";
    /** 拒绝策略: callerRuns / abort / discard / discardOldest */
    private String rejectedExecutionHandler = "callerRuns";
    /** 是否等待任务完成后关闭 */
    private boolean waitForTasksToCompleteOnShutdown = true;
    /** 关闭时最大等待时间(秒) */
    private int awaitTerminationSeconds = 30;
}
