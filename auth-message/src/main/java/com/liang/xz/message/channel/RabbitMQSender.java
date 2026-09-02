package com.liang.xz.message.channel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.message.config.RabbitMQConfig;
import com.liang.xz.message.entity.MessageRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RabbitMQ 消息发送器
 * <p>负责将消息推送到 RabbitMQ 交换机, 由各渠道监听器消费</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitMQSender {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 发送消息到指定渠道队列(点对点模式, 按 routingKey 路由)
     */
    public void sendToChannel(MessageRecord record, String channel, List<String> receivers) {
        String routingKey = channelToRoutingKey(channel);
        Map<String, Object> payload = buildPayload(record, receivers);
        try {
            String json = objectMapper.writeValueAsString(payload);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EVENT_EXCHANGE, routingKey, json);
            log.info("[RabbitMQ] 消息已投递: channel={}, messageId={}", channel, record.getMessageId());
        } catch (JsonProcessingException e) {
            log.error("[RabbitMQ] 序列化失败: messageId={}", record.getMessageId(), e);
            throw new RuntimeException("消息序列化失败", e);
        }
    }

    /**
     * 广播消息到所有渠道队列(扇出模式)
     */
    public void broadcast(MessageRecord record, String content) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.BROADCAST_EXCHANGE, "", content);
            log.info("[RabbitMQ] 广播消息已发送: messageId={}", record.getMessageId());
        } catch (Exception e) {
            log.error("[RabbitMQ] 广播失败: messageId={}", record.getMessageId(), e);
            throw new RuntimeException("广播消息发送失败", e);
        }
    }

    /**
     * 发布事件到指定子系统
     */
    public void publishEvent(String eventType, String payload, List<String> targetSystems) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("payload", payload);
        event.put("targetSystems", targetSystems);
        event.put("timestamp", System.currentTimeMillis());
        try {
            String json = objectMapper.writeValueAsString(event);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EVENT_EXCHANGE, "event." + eventType, json);
            log.info("[RabbitMQ] 事件已发布: type={}, targets={}", eventType, targetSystems);
        } catch (JsonProcessingException e) {
            log.error("[RabbitMQ] 事件序列化失败: type={}", eventType, e);
        }
    }

    private String channelToRoutingKey(String channel) {
        return switch (channel.toUpperCase()) {
            case "SMS" -> RabbitMQConfig.RK_SMS;
            case "EMAIL" -> RabbitMQConfig.RK_EMAIL;
            case "IN_APP" -> RabbitMQConfig.RK_IN_APP;
            case "WEBSOCKET" -> RabbitMQConfig.RK_WEBSOCKET;
            case "MQ" -> RabbitMQConfig.RK_SUBSYSTEM;
            default -> RabbitMQConfig.RK_SUBSYSTEM;
        };
    }

    private Map<String, Object> buildPayload(MessageRecord record, List<String> receivers) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("messageId", record.getMessageId());
        payload.put("messageType", record.getMessageType());
        payload.put("title", record.getTitle());
        payload.put("content", record.getContent());
        payload.put("sender", record.getSender());
        payload.put("receivers", receivers != null ? receivers : List.of());
        payload.put("sourceSystem", record.getSourceSystem());
        payload.put("businessId", record.getBusinessId());
        payload.put("timestamp", System.currentTimeMillis());
        return payload;
    }
}
