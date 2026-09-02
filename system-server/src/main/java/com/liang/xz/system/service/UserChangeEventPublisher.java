package com.liang.xz.system.service;

import com.liang.xz.system.config.SystemRabbitMQConfig;
import com.liang.xz.system.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 用户变更事件发布器
 * <p>
 * 在 UserManageService 的 create/update/delete/enable/disable 中调用,
 * 通过 auth.message.event 交换机广播 portal.user.* 消息,
 * 子系统(如博客)监听并同步本地用户数据
 *
 * @author auth-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserChangeEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发布用户创建事件
     */
    public void publishUserCreated(UserEntity user) {
        publish("USER_CREATED", user);
    }

    /**
     * 发布用户更新事件
     */
    public void publishUserUpdated(UserEntity user) {
        publish("USER_UPDATED", user);
    }

    /**
     * 发布用户删除事件
     */
    public void publishUserDeleted(UserEntity user) {
        publish("USER_DELETED", user);
    }

    /**
     * 发布用户启用/禁用事件
     */
    public void publishUserStatusChanged(UserEntity user) {
        publish("USER_UPDATED", user);
    }

    /**
     * 核心发布方法
     */
    private void publish(String eventType, UserEntity user) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("messageId", UUID.randomUUID().toString());
            message.put("eventType", eventType);
            message.put("sourceSystem", "portal");
            message.put("timestamp", System.currentTimeMillis());

            Map<String, Object> data = new HashMap<>();
            data.put("userId", user.getId());
            data.put("username", user.getUsername());
            data.put("nickname", user.getNickname());
            data.put("email", user.getEmail());
            data.put("phone", user.getPhone());
            data.put("avatar", user.getAvatar());
            data.put("enabled", user.getEnabled() ? 1 : 0);
            message.put("data", data);

            // 路由Key: portal.user.created / portal.user.updated / portal.user.deleted
            String routingKey = "portal.user." + eventType.toLowerCase().replace("user_", "");

            rabbitTemplate.convertAndSend(
                    SystemRabbitMQConfig.PORTAL_EVENT_EXCHANGE,
                    routingKey,
                    message
            );

            log.info("[PortalEvent] 用户变更已广播: type={}, userId={}, username={}, routingKey={}",
                    eventType, user.getId(), user.getUsername(), routingKey);
        } catch (Exception e) {
            log.error("[PortalEvent] 用户变更广播失败: type={}, userId={}, error={}",
                    eventType, user.getId(), e.getMessage());
            // 不阻断业务
        }
    }
}
