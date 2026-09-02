package com.liang.xz.system.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * system-server RabbitMQ 配置
 * <p>
 * 仅配置消息转换器, RabbitTemplate 由 Spring Boot AMQP 自动配置
 * RabbitMQ 连接配置从 Nacos 获取
 *
 * @author auth-platform
 */
@Configuration
public class SystemRabbitMQConfig {

    /** 门户事件交换机(门户→子系统, 复用auth-message的交换机) */
    public static final String PORTAL_EVENT_EXCHANGE = "auth.message.event";

    @Bean
    public MessageConverter messageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(mapper);
    }
}
