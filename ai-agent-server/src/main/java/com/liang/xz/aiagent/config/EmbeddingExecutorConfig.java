package com.liang.xz.aiagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 文件索引(解析 + 向量化)专用线程池配置。
 *
 * <p>与业务共用线程池隔离，避免大文件索引长时间阻塞业务请求线程；
 * 单文件索引超时由 {@code ai-agent.file-upload.per-file-index-timeout-seconds} 控制，
 * 线程池仅负责任务调度，超时熔断在 {@code KnowledgeBaseService.asyncIndexDocument} 中处理。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Configuration
public class EmbeddingExecutorConfig {

    /** 线程名前缀，便于排查日志 */
    public static final String THREAD_NAME_PREFIX = "embedding-index-";

    /**
     * 文件索引专用线程池。
     *
     * @return 自定义 {@link Executor}
     */
    @Bean("embeddingIndexExecutor")
    public Executor embeddingIndexExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
