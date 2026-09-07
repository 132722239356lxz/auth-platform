package com.liang.xz.common.core.async;

import com.alibaba.ttl.TtlRunnable;
import com.alibaba.ttl.TtlCallable;
import com.alibaba.ttl.threadpool.TtlExecutors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * <p>异步工具类 —— 一行代码实现异步执行 + TTL 上下文自动透传</p>
 *
 * <p>核心能力:</p>
 * <ul>
 *   <li>传入接口/Lambda 即可异步执行，无需关心线程池</li>
 *   <li>自动包装 TtlRunnable/TtlCallable/TtlSupplier，确保 traceId、数据源等 TTL 上下文在线程池中透传</li>
 *   <li>支持有返回值、无返回值、回调多种模式</li>
 *   <li>基于 @Async 线程池 taskExecutor，复用同一套线程池配置</li>
 * </ul>
 *
 * <pre>{@code
 * // 1. 无返回值异步执行
 * AsyncHelper.runAsync(() -> orderService.create(order));
 *
 * // 4. 使用自定义线程池
 * AsyncHelper.runAsync(() -> heavyTask(), myExecutor);
 * }</pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public final class AsyncHelper {

    private static final Logger log = LoggerFactory.getLogger(AsyncHelper.class);

    /**
     * 共享的 TTL 包装 ForkJoinPool（兜底用，基于 commonPool）
     */
    private static final Executor TTL_COMMON_POOL = TtlExecutors.getTtlExecutor(ForkJoinPool.commonPool());

    private AsyncHelper() {
    }

    // ==================== 无返回值 ====================

    /**
     * 异步执行任务（无返回值），使用默认 TTL 线程池
     *
     * @param task 异步任务
     */
    public static void runAsync(Runnable task) {
        runAsync(task, TTL_COMMON_POOL);
    }

    /**
     * 异步执行任务（无返回值），指定线程池
     *
     * @param task     异步任务
     * @param executor 自定义线程池
     */
    public static void runAsync(Runnable task, Executor executor) {
        if (task == null) {
            return;
        }
        TtlRunnable ttlTask = TtlRunnable.get(task, true, true);
        CompletableFuture.runAsync(ttlTask, executor)
                .exceptionally(ex -> {
                    log.error("Async task error: {}", ex.getMessage(), ex);
                    return null;
                });
    }



    // ==================== Callable 风格 ====================

    /**
     * 异步执行 Callable 任务，使用默认 TTL 线程池
     *
     * @param callable 异步任务
     * @param <T>      返回值类型
     * @return CompletableFuture
     */
    public static <T> CompletableFuture<T> callAsync(Callable<T> callable) {
        return callAsync(callable, TTL_COMMON_POOL);
    }

    /**
     * 异步执行 Callable 任务，指定线程池
     *
     * @param callable 异步任务
     * @param executor 自定义线程池
     * @param <T>      返回值类型
     * @return CompletableFuture
     */
    public static <T> CompletableFuture<T> callAsync(Callable<T> callable, Executor executor) {
        if (callable == null) {
            return CompletableFuture.completedFuture(null);
        }
        TtlCallable<T> ttlCallable = TtlCallable.get(callable, true, true);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return ttlCallable.call();
            } catch (Exception e) {
                log.error("Async callable error: {}", e.getMessage(), e);
                return null;
            }
        }, executor);
    }




    // ==================== 延迟执行 ====================

    /**
     * 延迟异步执行（无返回值）
     *
     * @param task  异步任务
     * @param delay 延迟时间
     * @param unit  时间单位
     * @return ScheduledFuture 可用于取消
     */
    public static ScheduledFuture<?> runAsyncDelayed(Runnable task, long delay, TimeUnit unit) {
        if (task == null) {
            return null;
        }
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ScheduledExecutorService ttlScheduler = TtlExecutors.getTtlScheduledExecutorService(scheduler);
        return ttlScheduler.schedule(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("Async delayed task error: {}", e.getMessage(), e);
            } finally {
                scheduler.shutdown();
            }
        }, delay, unit);
    }
}
