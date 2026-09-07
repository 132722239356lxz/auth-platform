package com.liang.xz.message.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置 —— 定义交换机/队列/绑定关系 + 消息序列化
 */
@Configuration
public class RabbitMQConfig {

    // ======================== 交换机 ========================

    /** 消息广播扇出交换机(所有绑定队列都收到) */
    public static final String BROADCAST_EXCHANGE = "auth.message.broadcast";

    /** 事件推送直连交换机(按路由Key分发) */
    public static final String EVENT_EXCHANGE = "auth.message.event";

    /** 死信交换机(消息重试) */
    public static final String DLX_EXCHANGE = "auth.message.dlx";

    /** 子系统同步交换机(子系统→门户, 如博客用户创建事件) */
    public static final String SUBSYSTEM_SYNC_EXCHANGE = "subsystem.sync";

    // ======================== 队列名称 ========================

    /** 短信队列 */
    public static final String SMS_QUEUE = "auth.message.sms";

    /** 邮件队列 */
    public static final String EMAIL_QUEUE = "auth.message.email";

    /** 站内信队列 */
    public static final String IN_APP_QUEUE = "auth.message.inapp";

    /** WebSocket推送队列 */
    public static final String WEBSOCKET_QUEUE = "auth.message.websocket";

    /** 子系统广播队列(通用) */
    public static final String SUBSYSTEM_QUEUE = "auth.message.subsystem";

    /** 子系统用户同步队列(接收子系统用户创建/更新/删除事件) */
    public static final String PORTAL_USER_SYNC_QUEUE = "portal.user.sync";

    /** 死信队列 */
    public static final String DLQ_QUEUE = "auth.message.dlq";

    // ======================== Routing Keys ========================

    public static final String RK_SMS = "channel.sms";
    public static final String RK_EMAIL = "channel.email";
    public static final String RK_IN_APP = "channel.inapp";
    public static final String RK_WEBSOCKET = "channel.websocket";
    public static final String RK_SUBSYSTEM = "channel.subsystem";

    /** 子系统用户事件路由Key前缀 */
    public static final String RK_USER_CREATED = "user.created";
    public static final String RK_USER_UPDATED = "user.updated";
    public static final String RK_USER_DELETED = "user.deleted";

    // ======================== Bean 定义 ========================

    /** 广播交换机 */
    @Bean
    public FanoutExchange broadcastExchange() {
        return new FanoutExchange(BROADCAST_EXCHANGE, true, false);
    }

    /** 事件直连交换机 */
    @Bean
    public DirectExchange eventExchange() {
        return new DirectExchange(EVENT_EXCHANGE, true, false);
    }

    /** 死信交换机 */
    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    /** 子系统同步交换机(子系统→门户) */
    @Bean
    public DirectExchange subsystemSyncExchange() {
        return new DirectExchange(SUBSYSTEM_SYNC_EXCHANGE, true, false);
    }

    // ----- 队列(绑定死信) -----

    private Queue buildQueue(String name) {
        return QueueBuilder.durable(name)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", name + ".dlq")
                .build();
    }

    @Bean public Queue smsQueue() { return buildQueue(SMS_QUEUE); }
    @Bean public Queue emailQueue() { return buildQueue(EMAIL_QUEUE); }
    @Bean public Queue inAppQueue() { return buildQueue(IN_APP_QUEUE); }
    @Bean public Queue websocketQueue() { return buildQueue(WEBSOCKET_QUEUE); }
    @Bean public Queue subsystemQueue() { return buildQueue(SUBSYSTEM_QUEUE); }
    @Bean public Queue portalUserSyncQueue() { return buildQueue(PORTAL_USER_SYNC_QUEUE); }

    /** 死信队列 */
    @Bean
    public Queue dlqQueue() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    // ----- 绑定 -----

    @Bean
    public Binding bindSms() {
        return BindingBuilder.bind(smsQueue()).to(eventExchange()).with(RK_SMS);
    }

    @Bean
    public Binding bindEmail() {
        return BindingBuilder.bind(emailQueue()).to(eventExchange()).with(RK_EMAIL);
    }

    @Bean
    public Binding bindInApp() {
        return BindingBuilder.bind(inAppQueue()).to(eventExchange()).with(RK_IN_APP);
    }

    @Bean
    public Binding bindWebSocket() {
        return BindingBuilder.bind(websocketQueue()).to(eventExchange()).with(RK_WEBSOCKET);
    }

    @Bean
    public Binding bindSubsystem() {
        return BindingBuilder.bind(subsystemQueue()).to(eventExchange()).with(RK_SUBSYSTEM);
    }

    /** 子系统用户同步队列绑定到子系统同步交换机 */
    @Bean
    public Binding bindPortalUserSync() {
        return BindingBuilder.bind(portalUserSyncQueue()).to(subsystemSyncExchange()).with("user.*");
    }

    /** 所有渠道队列绑定到广播交换机(扇出模式) */
    @Bean
    public Binding bindBroadcastSms() {
        return BindingBuilder.bind(smsQueue()).to(broadcastExchange());
    }

    @Bean
    public Binding bindBroadcastEmail() {
        return BindingBuilder.bind(emailQueue()).to(broadcastExchange());
    }

    @Bean
    public Binding bindBroadcastInApp() {
        return BindingBuilder.bind(inAppQueue()).to(broadcastExchange());
    }

    @Bean
    public Binding bindBroadcastWebSocket() {
        return BindingBuilder.bind(websocketQueue()).to(broadcastExchange());
    }

    @Bean
    public Binding bindBroadcastSubsystem() {
        return BindingBuilder.bind(subsystemQueue()).to(broadcastExchange());
    }

    /** 死信队列绑定 */
    @Bean
    public Binding bindDlq() {
        return BindingBuilder.bind(dlqQueue()).to(dlxExchange()).with("#");
    }

    // ----- JSON 序列化 -----

    @Bean
    public MessageConverter messageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        // 发送确认回调
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack && correlationData != null) {
                // 可记录发送失败日志
                System.err.println("[RabbitMQ] 消息发送失败: id=" + correlationData.getId() + ", cause=" + cause);
            }
        });
        return template;
    }
}
