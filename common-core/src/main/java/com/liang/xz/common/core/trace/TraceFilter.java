package com.liang.xz.common.core.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * <p>TraceId 过滤器 —— 请求入口自动生成/透传 traceId</p>
 *
 * <p>逻辑:</p>
 * <ol>
 *   <li>从请求头 X-Trace-Id 获取上游 traceId（微服务透传）</li>
 *   <li>如果不存在则自动生成</li>
 *   <li>设置到 TraceContext + MDC</li>
 *   <li>响应头中回写 X-Trace-Id 便于问题排查</li>
 *   <li>请求结束后清理上下文</li>
 * </ol>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public class TraceFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceFilter.class);

    public static final String TRACE_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // 1. 尝试从请求头获取上游 traceId
            String traceId = request.getHeader(TRACE_HEADER);
            if (traceId != null && !traceId.isEmpty()) {
                TraceContext.setTraceId(traceId);
            } else {
                TraceContext.generateTraceId();
            }

            // 2. 响应头回写 traceId
            response.setHeader(TRACE_HEADER, TraceContext.getTraceId());

            // 3. 执行后续过滤器
            filterChain.doFilter(request, response);

        } finally {
            // 4. 清理上下文，防止内存泄漏
            TraceContext.clear();
        }
    }
}
