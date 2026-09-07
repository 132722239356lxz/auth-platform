package com.liang.xz.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>熔断降级处理 —— 后端服务不可用时的兜底响应</p>
 *
 * <p>触发场景:</p>
 * <ul>
 *   <li>后端服务超时无响应</li>
 *   <li>断路器打开（失败率超过阈值）</li>
 *   <li>服务实例全部下线</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@RestController
public class FallbackController {

    @RequestMapping("/fallback/auth-server")
    public Mono<Map<String, Object>> authServerFallback() {
        log.warn("[Fallback] 授权服务降级");
        return Mono.just(buildFallback("auth-server", "授权服务"));
    }

    @RequestMapping("/fallback/auth-flow")
    public Mono<Map<String, Object>> authFlowFallback() {
        log.warn("[Fallback] 审批流服务降级");
        return Mono.just(buildFallback("auth-flow", "审批流服务"));
    }

    @RequestMapping("/fallback/auth-message")
    public Mono<Map<String, Object>> authMessageFallback() {
        log.warn("[Fallback] 消息服务降级");
        return Mono.just(buildFallback("auth-message", "消息服务"));
    }

    @RequestMapping("/fallback/ai-agent-server")
    public Mono<Map<String, Object>> aiAgentFallback() {
        log.warn("[Fallback] AI智能体服务降级");
        return Mono.just(buildFallback("ai-agent-server", "AI智能体服务"));
    }

    /**
     * 构建降级响应
     */
    private Map<String, Object> buildFallback(String service, String serviceName) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 503);
        result.put("message", String.format("%s 暂不可用，请稍后重试", serviceName));
        result.put("service", service);
        result.put("timestamp", LocalDateTime.now().toString());
        result.put("data", null);
        return result;
    }
}
