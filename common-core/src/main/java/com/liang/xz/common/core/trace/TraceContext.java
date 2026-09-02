package com.liang.xz.common.core.trace;

import org.slf4j.MDC;
import com.alibaba.ttl.TransmittableThreadLocal;

import java.util.UUID;

/**
 * <p>分布式链路追踪上下文 —— 基于 TransmittableThreadLocal + MDC</p>
 *
 * <p>核心能力:</p>
 * <ul>
 *   <li>生成全局唯一 traceId，贯穿整个请求链路</li>
 *   <li>使用 TransmittableThreadLocal 支持父子线程传递(线程池场景)</li>
 *   <li>自动同步到 SLF4J MDC，日志中通过 %X{traceId} 输出</li>
 *   <li>支持 spanId 记录调用层级</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public final class TraceContext {

    private static final TransmittableThreadLocal<String> TRACE_ID_HOLDER = new TransmittableThreadLocal<>();
    private static final TransmittableThreadLocal<Integer> SPAN_DEPTH_HOLDER = new TransmittableThreadLocal<>();

    private TraceContext() {
    }

    /**
     * 生成并设置 traceId（请求入口调用）
     */
    public static String generateTraceId() {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        TRACE_ID_HOLDER.set(traceId);
        SPAN_DEPTH_HOLDER.set(0);
        MDC.put("traceId", traceId);
        return traceId;
    }

    /**
     * 设置已有的 traceId（跨服务透传时使用）
     */
    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.isEmpty()) {
            TRACE_ID_HOLDER.set(traceId);
            SPAN_DEPTH_HOLDER.set(0);
            MDC.put("traceId", traceId);
        } else {
            generateTraceId();
        }
    }

    /**
     * 获取当前 traceId
     */
    public static String getTraceId() {
        String traceId = TRACE_ID_HOLDER.get();
        if (traceId == null || traceId.isEmpty()) {
            traceId = generateTraceId();
        }
        return traceId;
    }

    /**
     * 获取当前 span 深度（调用层级）
     */
    public static int getSpanDepth() {
        Integer depth = SPAN_DEPTH_HOLDER.get();
        return depth != null ? depth : 0;
    }

    /**
     * 递增 span 深度（进入子调用时使用）
     */
    public static int incrementSpanDepth() {
        int depth = getSpanDepth() + 1;
        SPAN_DEPTH_HOLDER.set(depth);
        return depth;
    }

    /**
     * 递减 span 深度（退出子调用时使用）
     */
    public static int decrementSpanDepth() {
        int depth = Math.max(0, getSpanDepth() - 1);
        SPAN_DEPTH_HOLDER.set(depth);
        return depth;
    }

    /**
     * 清理上下文（请求结束后必须调用，防止内存泄漏）
     */
    public static void clear() {
        TRACE_ID_HOLDER.remove();
        SPAN_DEPTH_HOLDER.remove();
        MDC.remove("traceId");
    }
}
