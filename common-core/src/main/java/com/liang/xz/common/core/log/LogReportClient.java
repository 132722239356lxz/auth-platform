package com.liang.xz.common.core.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.common.core.trace.TraceContext;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 日志上报客户端
 * <p>
 * 各业务模块通过此类将日志异步批量上报到 log-server。
 * 使用内存队列缓冲 + 定时/定量的双触发机制, 最小化对业务性能的影响。
 *
 * <pre>
 * 使用方式:
 *   1. 注入 LogReportClient
 *   2. 调用 report(info) 上报日志
 *
 * 配置 (application.yml):
 *   log.client.enabled: true
 *   log.client.server-url: http://log-server  # 可选, 默认通过 Nacos
 * </pre>
 *
 * @author liang
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "log.client.enabled", havingValue = "true")
public class LogReportClient {

    private final LogClientProperties properties;
    private final RestTemplate restTemplate;
    private final LoadBalancerClient loadBalancerClient;
    private final ObjectMapper objectMapper;

    public LogReportClient(LogClientProperties properties,
            @Qualifier("logClientRestTemplate") RestTemplate restTemplate,
            LoadBalancerClient loadBalancerClient,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.loadBalancerClient = loadBalancerClient;
        this.objectMapper = objectMapper;
    }

    /** 内存缓冲队列 */
    private final Queue<LogEntry> buffer = new ConcurrentLinkedQueue<>();

    @PostConstruct
    public void init() {
        String serverUrl = properties.getServerUrl();
        String batchUrl = serverUrl + "/api/logs/collect/batch";
        log.info("[LogClient] 日志上报客户端已启动, 目标地址: {}, 批量大小: {}, 刷新间隔: {}s",
                batchUrl, properties.getBatchSize(), properties.getFlushIntervalSeconds());
    }

    // ======================== 上报入口 ========================

    /**
     * 上报一条应用日志 (自动填充 traceId)
     */
    public void info(String module, String message) {
        enqueue(buildEntry(module, "INFO", "application", message));
    }

    /**
     * 上报一条错误日志
     */
    public void error(String module, String message, Throwable ex) {
        LogEntry entry = buildEntry(module, "ERROR", "exception", message);
        if (ex != null) {
            entry.setExceptionType(ex.getClass().getName());
            entry.setExceptionStack(stackTraceToString(ex));
        }
        enqueue(entry);
    }

    /**
     * 上报一条请求日志
     */
    public void request(String module, String uri, String method,
                        int httpStatus, long costTime) {
        LogEntry entry = buildEntry(module, "INFO", "request",
                method + " " + uri + " -> " + httpStatus);
        entry.setRequestUri(uri);
        entry.setHttpMethod(method);
        entry.setHttpStatus(httpStatus);
        entry.setCostTime(costTime);
        enqueue(entry);
    }

    /**
     * 上报子系统异常日志 (封装完整异常信息, 便于 log-server 分析)
     */
    public void reportException(String module, String category, Throwable ex,
                                String requestUri, String username, String clientIp) {
        if (ex == null) {
            return;
        }
        LogEntry entry = buildEntry(module, "ERROR", category != null ? category : "exception",
                ex.getMessage());
        entry.setExceptionType(ex.getClass().getName());
        entry.setExceptionStack(stackTraceToString(ex));
        entry.setRequestUri(requestUri);
        entry.setUsername(username);
        entry.setClientIp(clientIp);
        enqueue(entry);
    }

    /**
     * 上报自定义日志条目
     */
    public void report(LogEntry entry) {
        if (entry != null) {
            if (entry.getTraceId() == null) {
                entry.setTraceId(TraceContext.getTraceId());
            }
            enqueue(entry);
        }
    }

    /**
     * 立即刷新缓冲队列 (如应用关闭时确保不丢日志)
     */
    public void flush() {
        List<LogEntry> batch = drainBuffer();
        if (!batch.isEmpty()) {
            sendBatch(batch);
        }
    }

    // ======================== 定时刷新 ========================

    @Scheduled(fixedDelayString = "${log.client.flush-interval-seconds:5}000")
    public void scheduledFlush() {
        List<LogEntry> batch = drainBuffer();
        if (!batch.isEmpty()) {
            sendBatch(batch);
        }
    }

    // ======================== 内部方法 ========================

    private void enqueue(LogEntry entry) {
        buffer.offer(entry);
        if (buffer.size() >= properties.getBatchSize()) {
            List<LogEntry> batch = drainBuffer();
            if (!batch.isEmpty()) {
                sendBatch(batch);
            }
        }
    }

    private List<LogEntry> drainBuffer() {
        List<LogEntry> batch = new ArrayList<>();
        LogEntry entry;
        while ((entry = buffer.poll()) != null) {
            batch.add(entry);
        }
        return batch;
    }

    private void sendBatch(List<LogEntry> entries) {
        String resolvedUrl;
        try {
            resolvedUrl = resolveServerUrl() + "/api/logs/collect/batch";
        } catch (Exception e) {
            log.error("[LogClient] 无法解析 load-server 服务地址, 丢弃 {} 条日志: {}", entries.size(), e.getMessage());
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 手动构造 JSON (避免额外 DTO 依赖)
            StringBuilder json = new StringBuilder("{\"entries\":[");
            for (int i = 0; i < entries.size(); i++) {
                if (i > 0) json.append(",");
                json.append(objectMapper.writeValueAsString(entries.get(i)));
            }
            json.append("]}");

            HttpEntity<String> request = new HttpEntity<>(json.toString(), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(resolvedUrl, request, String.class);

            log.info("[LogClient] 成功上报 {} 条日志到 log-server, HTTP {}", entries.size(),
                    response.getStatusCode().value());
        } catch (Exception e) {
            // 打印完整异常信息便于排查
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            log.error("[LogClient] 上报日志失败, URL={}, 丢弃 {} 条日志, 原因: {} ({})",
                    resolvedUrl, entries.size(), root.toString(), e.getClass().getSimpleName());
        }
    }

    /**
     * 通过 LoadBalancerClient 解析 log-server 的实际地址
     */
    private String resolveServerUrl() {
        String serverUrl = properties.getServerUrl();
        // 如果是 http://log-server 格式, 通过 LoadBalancerClient 解析
        if (serverUrl.startsWith("http://") || serverUrl.startsWith("https://")) {
            URI uri = URI.create(serverUrl);
            String serviceId = uri.getHost();
            ServiceInstance instance = loadBalancerClient.choose(serviceId);
            if (instance == null) {
                throw new IllegalStateException("No instance available for service: " + serviceId);
            }
            String resolved = instance.getUri().toString();
            log.debug("[LogClient] 服务发现: {} -> {}", serverUrl, resolved);
            return resolved;
        }
        // 如果已经是完整地址 (如 http://127.0.0.1:9009) 则直接使用
        return serverUrl;
    }

    private LogEntry buildEntry(String module, String level, String category, String message) {
        LogEntry entry = new LogEntry();
        entry.setTraceId(TraceContext.getTraceId());
        entry.setModule(module);
        entry.setLevel(level);
        entry.setCategory(category);
        entry.setMessage(message);
        entry.setLogTime(LocalDateTime.now());
        return entry;
    }

    private String stackTraceToString(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        sb.append(ex.toString()).append("\n");
        for (StackTraceElement ste : ex.getStackTrace()) {
            sb.append("\tat ").append(ste.toString()).append("\n");
        }
        return sb.toString();
    }

    // ======================== 内嵌日志条目 ========================

    @Data
    public static class LogEntry {
        private String traceId;
        private String module;
        private String category;
        private String level;
        private String className;
        private String methodName;
        private String message;
        private String fullMessage;
        private String exceptionStack;
        private String exceptionType;
        private String username;
        private String clientIp;
        private String requestUri;
        private String httpMethod;
        private Integer httpStatus;
        private Long costTime;
        private String metadata;
        private LocalDateTime logTime;
    }
}
