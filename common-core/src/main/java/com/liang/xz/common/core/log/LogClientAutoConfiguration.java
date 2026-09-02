package com.liang.xz.common.core.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 日志客户端自动配置
 * <p>
 * 条件: log.client.enabled=true 时生效
 *
 * @author liang
 */
@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(LogClientProperties.class)
@ConditionalOnProperty(value = "log.client.enabled", havingValue = "true")
public class LogClientAutoConfiguration {

    @Bean
    public RestTemplate logClientRestTemplate(LogClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeout()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeout()));
        return new RestTemplate(factory);
    }

    @Bean
    @ConditionalOnProperty(value = "log.client.request-log.enabled", havingValue = "true", matchIfMissing = true)
    public RequestLogAspect requestLogAspect(LogReportClient logReportClient,
            LogClientProperties properties, ObjectMapper objectMapper,
            Environment environment, @Value("${spring.application.name:unknown}") String applicationName) {
        return new RequestLogAspect(logReportClient, properties, objectMapper, environment, applicationName);
    }
}
