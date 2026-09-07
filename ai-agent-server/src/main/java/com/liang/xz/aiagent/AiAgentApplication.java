package com.liang.xz.aiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * <p>AI智能体服务启动类</p>
 * <p>能力: RAG知识库 / 本地离线检索 / 联网搜索 / 业务数据分析预警智能体</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = "com.liang.xz")
@EnableDiscoveryClient
@EnableScheduling
@EnableAsync
public class AiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAgentApplication.class, args);
    }
}
