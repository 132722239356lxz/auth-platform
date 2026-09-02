package com.liang.xz.common.core.log;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/**
 * 日志上报客户端配置属性
 *
 * @author liang
 */
@Data
@ConfigurationProperties(prefix = "log.client")
public class LogClientProperties {

    /** log-server 服务地址 (默认通过 Nacos 服务发现) */
    private String serverUrl = "http://log-server";

    /** 是否启用在各模块中开启，无需显式配置也可自动通过 common-core 依赖生效 */
    private boolean enabled = false;

    /** 批量上报阈值(达到此数量自动触发上报) */
    private int batchSize = 50;

    /** 定时刷新间隔(秒) */
    private int flushIntervalSeconds = 5;

    /** 上报超时时间(毫秒) */
    private int connectTimeout = 3000;

    /** 读取超时(毫秒) */
    private int readTimeout = 5000;

    /**
     * 请求日志相关配置
     */
    private RequestLog requestLog = new RequestLog();

    @Data
    public static class RequestLog {

        /** 是否启用请求日志切面 (默认启用) */
        private boolean enabled = true;

        /** 忽略记录的请求路径前缀 */
        private Set<String> ignorePaths = Set.of(
                "/actuator/", "/swagger-ui", "/v3/api-docs", "/doc.html",
                "/webjars/", "/favicon.ico", "/api/logs/collect/"
        );
    }
}
