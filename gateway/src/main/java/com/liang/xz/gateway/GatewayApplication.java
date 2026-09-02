package com.liang.xz.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * <p>API 网关启动类</p>
 *
 * <p>核心能力:</p>
 * <ul>
 *   <li><b>统一入口:</b> 所有外部请求通过网关路由到内部服务</li>
 *   <li><b>Token校验:</b> 验证JWT Bearer Token，无状态认证</li>
 *   <li><b>限流保护:</b> 基于IP/用户的令牌桶限流</li>
 *   <li><b>熔断降级:</b> Resilience4j 断路器，后端服务异常时自动熔断</li>
 *   <li><b>重试机制:</b> 临时故障自动重试</li>
 *   <li><b>负载均衡:</b> 基于Nacos的客户端负载均衡(轮询)</li>
 *   <li><b>超时控制:</b> 全局请求超时 + 路由级超时</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = "com.liang.xz")
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
