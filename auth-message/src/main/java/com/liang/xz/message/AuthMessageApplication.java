package com.liang.xz.message;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * <p>消息服务启动类</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = "com.liang.xz")
@EnableDiscoveryClient
public class AuthMessageApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthMessageApplication.class, args);
    }
}
