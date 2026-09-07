package com.liang.xz.log;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 统一日志管理服务
 * <p>
 * 职责:
 * 1. 接收各模块上报的日志并分类存储
 * 2. 提供日志多维查询/统计/导出
 * 3. AI驱动的相似错误上下文分析与解决方案推荐
 *
 * @author liang
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.liang.xz")
@EnableDiscoveryClient  // 注册到Nacos服务发现
public class LogServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogServerApplication.class, args);
    }
}
