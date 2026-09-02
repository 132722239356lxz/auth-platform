package com.liang.xz.flow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * <p>审批流服务启动类</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = "com.liang.xz")
@EnableDiscoveryClient
public class AuthFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthFlowApplication.class, args);
    }
}
