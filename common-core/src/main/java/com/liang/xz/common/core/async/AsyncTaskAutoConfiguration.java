package com.liang.xz.common.core.async;

import com.alibaba.ttl.threadpool.TtlExecutors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * <p>异步任务线程池自动配置</p>
 *
 * <p>核心特性:</p>
 * <ul>
 *   <li>使用 TtlExecutors 包装，支持 TransmittableThreadLocal 在线程池中传递</li>
 *   <li>traceId 自动从父线程传递给子线程</li>
 *   <li>可配置核心线程数、最大线程数、队列容量、拒绝策略等</li>
 *   <li>统一的异常处理器</li>
 * </ul>
 *
 * <p>配置前缀: async.task.executor</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@AutoConfiguration
@EnableAsync
@EnableConfigurationProperties(AsyncTaskProperties.class)
public class AsyncTaskAutoConfiguration implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncTaskAutoConfiguration.class);

    private final AsyncTaskProperties properties;

    public AsyncTaskAutoConfiguration(AsyncTaskProperties properties) {
        this.properties = properties;
    }

    /**
     * 异步任务执行器（TTL 包装，支持 traceId 透传）
     */
    @Bean(name = "asyncTaskExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setKeepAliveSeconds(properties.getKeepAliveSeconds());
        executor.setThreadNamePrefix(properties.getThreadNamePrefix());
        executor.setRejectedExecutionHandler(createRejectedHandler());
        executor.setWaitForTasksToCompleteOnShutdown(properties.isWaitForTasksToCompleteOnShutdown());
        executor.setAwaitTerminationSeconds(properties.getAwaitTerminationSeconds());
        executor.initialize();

        log.info("Async task executor initialized: core={}, max={}, queue={}, prefix={}",
                properties.getCorePoolSize(), properties.getMaxPoolSize(),
                properties.getQueueCapacity(), properties.getThreadNamePrefix());

        // 使用 TTL 包装，确保 traceId 等 ThreadLocal 在线程池中透传
        return TtlExecutors.getTtlExecutor(executor);
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) ->
                log.error("Async task exception in method [{}]: {}", method.getName(), throwable.getMessage(), throwable);
    }

    private RejectedExecutionHandler createRejectedHandler() {
        return switch (properties.getRejectedExecutionHandler().toLowerCase()) {
            case "abort" -> new ThreadPoolExecutor.AbortPolicy();
            case "discard" -> new ThreadPoolExecutor.DiscardPolicy();
            case "discardoldest" -> new ThreadPoolExecutor.DiscardOldestPolicy();
            default -> new ThreadPoolExecutor.CallerRunsPolicy();
        };
    }
}
