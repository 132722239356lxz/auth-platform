package com.liang.xz.message.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.common.core.redis.RedisHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 子系统用户同步监听器
 * <p>
 * 接收子系统(如博客)发送的用户创建/更新/删除事件:
 * - 在门户 sys_user 创建/更新用户
 * - 维护 sys_user_subsystem 可见性关系
 * <p>
 * 监听队列: portal.user.sync
 * 交换机: subsystem.sync
 * 路由Key: user.created / user.updated / user.deleted
 *
 * @author auth-platform
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubsystemUserSyncListener {

    private final JdbcTemplate jdbcTemplate;
    private final RedisHelper redisHelper;
    private final ObjectMapper objectMapper;

    /** 幂等key前缀 */
    private static final String IDEMPOTENT_KEY_PREFIX = "portal:user:sync:";
    /** 幂等key TTL: 24小时 */
    private static final long IDEMPOTENT_TTL = 86400;

    /**
     * 监听子系统用户事件
     */
    @RabbitListener(queues = "portal.user.sync", concurrency = "2-5")
    public void onSubsystemUserEvent(Map<String, Object> message) {
        try {
            String messageId = (String) message.get("messageId");
            String eventType = (String) message.get("eventType");
            String sourceSystem = (String) message.get("sourceSystem");

            log.info("[UserSync] 收到子系统用户事件: type={}, source={}, messageId={}",
                    eventType, sourceSystem, messageId);

            // 幂等性检查
            String idempotentKey = IDEMPOTENT_KEY_PREFIX + messageId;
            if (Boolean.TRUE.equals(redisHelper.hasKey(idempotentKey))) {
                log.info("[UserSync] 重复消息已忽略: messageId={}", messageId);
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) message.get("data");
            if (data == null) {
                log.warn("[UserSync] 消息data为空, 跳过");
                return;
            }

            // 根据事件类型处理
            if (eventType == null) {
                log.warn("[UserSync] 事件类型为空, 跳过");
                return;
            }

            switch (eventType) {
                case "USER_CREATED" -> handleUserCreated(data, sourceSystem);
                case "USER_UPDATED" -> handleUserUpdated(data, sourceSystem);
                case "USER_DELETED" -> handleUserDeleted(data, sourceSystem);
                default -> log.warn("[UserSync] 未知事件类型: {}", eventType);
            }

            // 标记消息已处理
            redisHelper.set(idempotentKey, "1", IDEMPOTENT_TTL, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("[UserSync] 处理子系统用户事件失败: {}", e.getMessage(), e);
            throw new RuntimeException("用户同步失败", e); // 触发重试
        }
    }

    /**
     * 处理用户创建:
     * 1. 检查sys_user是否已存在(按username)
     * 2. 不存在则创建sys_user
     * 3. 维护 sys_user_subsystem 可见性关系
     */
    @Transactional
    public void handleUserCreated(Map<String, Object> data, String sourceSystem) {
        String username = (String) data.get("username");
        String nickname = (String) data.get("nickname");
        String email = (String) data.get("email");
        String phone = (String) data.get("phone");
        String avatar = (String) data.get("avatar");

        if (username == null || username.isEmpty()) {
            log.warn("[UserSync] username为空, 跳过");
            return;
        }

        // 查询用户是否已存在
        var existingIds = jdbcTemplate.queryForList(
                "SELECT id FROM sys_user WHERE username = ?", Long.class, username);

        Long userId;
        if (existingIds.isEmpty()) {
            // 创建新用户
            jdbcTemplate.update(
                    "INSERT INTO sys_user (username, password, nickname, email, phone, avatar, user_type, tenant_id, enabled) " +
                            "VALUES (?, NULL, ?, ?, ?, ?, 'user', 'default', 1)",
                    username, nickname, email, phone, avatar);

            userId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            log.info("[UserSync] 门户创建用户: username={}, userId={}", username, userId);
        } else {
            userId = existingIds.get(0);
            // 更新用户信息
            jdbcTemplate.update(
                    "UPDATE sys_user SET nickname=?, email=?, phone=?, avatar=? WHERE id=?",
                    nickname, email, phone, avatar, userId);
            log.info("[UserSync] 门户用户已存在, 更新信息: username={}, userId={}", username, userId);
        }

        // 维护 sys_user_subsystem 可见性关系
        if (sourceSystem != null && !sourceSystem.isEmpty()) {
            jdbcTemplate.update(
                    "INSERT INTO sys_user_subsystem (user_id, client_id, visible, granted_by) " +
                            "VALUES (?, ?, 1, NULL) " +
                            "ON DUPLICATE KEY UPDATE visible=1",
                    userId, sourceSystem);

            log.info("[UserSync] 用户-子系统关系已建立: userId={}, clientId={}", userId, sourceSystem);
        }
    }

    /**
     * 处理用户更新:
     * 更新 sys_user 的 nickname/email/phone/avatar
     */
    @Transactional
    public void handleUserUpdated(Map<String, Object> data, String sourceSystem) {
        String username = (String) data.get("username");
        String nickname = (String) data.get("nickname");
        String email = (String) data.get("email");
        String phone = (String) data.get("phone");
        String avatar = (String) data.get("avatar");

        if (username == null) {
            log.warn("[UserSync] 更新事件缺少username, 跳过");
            return;
        }

        int updated = jdbcTemplate.update(
                "UPDATE sys_user SET nickname=?, email=?, phone=?, avatar=? WHERE username=?",
                nickname, email, phone, avatar, username);

        if (updated > 0) {
            log.info("[UserSync] 门户用户信息已更新: username={}", username);
        } else {
            log.warn("[UserSync] 门户用户不存在, 未更新: username={}", username);
        }
    }

    /**
     * 处理用户删除:
     * 撤销用户对子系统的可见性(不删除用户本身)
     */
    @Transactional
    public void handleUserDeleted(Map<String, Object> data, String sourceSystem) {
        String username = (String) data.get("username");

        if (username == null || sourceSystem == null) {
            log.warn("[UserSync] 删除事件缺少username或sourceSystem, 跳过");
            return;
        }

        // 查询用户ID
        var userIds = jdbcTemplate.queryForList(
                "SELECT id FROM sys_user WHERE username = ?", Long.class, username);

        if (userIds.isEmpty()) {
            log.warn("[UserSync] 门户用户不存在, 无需删除: username={}", username);
            return;
        }

        Long userId = userIds.get(0);

        // 撤销子系统可见性
        jdbcTemplate.update(
                "UPDATE sys_user_subsystem SET visible=0 WHERE user_id=? AND client_id=?",
                userId, sourceSystem);

        log.info("[UserSync] 用户-子系统关系已撤销: userId={}, clientId={}", userId, sourceSystem);
    }
}
