package com.liang.xz.gateway.filter;

import com.liang.xz.gateway.config.IpWhitelistProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

/**
 * <p>IP白名单全局过滤器(网关层) —— 只允许白名单内的IP访问</p>
 *
 * <p>匹配规则:</p>
 * <ul>
 *   <li>精确IP匹配: 192.168.1.100</li>
 *   <li>CIDR网段匹配: 192.168.1.0/24, 10.0.0.0/8</li>
 *   <li>0.0.0.0/0 表示允许所有IP</li>
 * </ul>
 *
 * <p>排除路径:</p>
 * <ul>
 *   <li>健康检查和文档接口不受限制</li>
 *   <li>白名单禁用时直接放行</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IpWhitelistGatewayFilter implements GlobalFilter, Ordered {

    private final IpWhitelistProperties ipWhitelistProperties;

    /** 不需要拦截的路径前缀 */
    private static final String[] EXCLUDED_PATHS = {
            "/actuator/health",
            "/swagger-ui",
            "/v3/api-docs",
            "/doc.html",
            "/webjars",
            "/.well-known",
            "/favicon.ico",
            "/fallback"
    };

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String clientIp = getClientIp(request);

        // 跳过不需要拦截的路径
        if (isExcludedPath(path)) {
            return chain.filter(exchange);
        }

        // 白名单未启用，直接放行
        if (!ipWhitelistProperties.isEnabled()) {
            return chain.filter(exchange);
        }

        // 检查IP是否在白名单中
        if (isIpAllowed(clientIp)) {
            return chain.filter(exchange);
        }

        log.warn("[IP白名单] 拒绝访问 - IP: {}, 路径: {}", clientIp, path);
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":403,\"message\":\"IP not in whitelist, access denied\",\"data\":null}";
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -200; // 在AuthGlobalFilter(-100)之前执行
    }

    /**
     * 判断IP是否在白名单中
     */
    private boolean isIpAllowed(String clientIp) {
        if (ipWhitelistProperties.getIps().isEmpty()) {
            // 白名单为空时，仅允许本地访问
            return "127.0.0.1".equals(clientIp) || "0:0:0:0:0:0:0:1".equals(clientIp);
        }

        for (String allowedIp : ipWhitelistProperties.getIps()) {
            if (matchIp(clientIp, allowedIp)) {
                return true;
            }
        }
        return false;
    }

    /**
     * IP匹配：支持精确IP和CIDR格式
     */
    private boolean matchIp(String clientIp, String allowedIp) {
        // 精确匹配
        if (clientIp.equals(allowedIp)) {
            return true;
        }

        // CIDR网段匹配
        if (allowedIp.contains("/")) {
            return matchCidr(clientIp, allowedIp);
        }

        return false;
    }

    /**
     * CIDR网段匹配
     */
    private boolean matchCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            String networkIp = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);

            byte[] ipBytes = InetAddress.getByName(ip).getAddress();
            byte[] networkBytes = InetAddress.getByName(networkIp).getAddress();

            if (ipBytes.length != networkBytes.length) {
                return false;
            }

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (ipBytes[i] != networkBytes[i]) {
                    return false;
                }
            }

            if (remainingBits > 0) {
                int mask = (0xFF << (8 - remainingBits)) & 0xFF;
                if ((ipBytes[fullBytes] & mask) != (networkBytes[fullBytes] & mask)) {
                    return false;
                }
            }

            return true;
        } catch (UnknownHostException e) {
            log.warn("[IP白名单] CIDR匹配异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取客户端真实IP（考虑代理）
     */
    private String getClientIp(ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeaders().getFirst("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeaders().getFirst("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeaders().getFirst("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddress() != null
                    ? request.getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
        }
        // 多级代理取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 判断路径是否需要跳过白名单检查
     */
    private boolean isExcludedPath(String path) {
        for (String excluded : EXCLUDED_PATHS) {
            if (path.startsWith(excluded)) {
                return true;
            }
        }
        return false;
    }
}
