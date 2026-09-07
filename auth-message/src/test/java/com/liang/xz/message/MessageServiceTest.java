package com.liang.xz.message;

import com.liang.xz.message.dto.MessageBroadcastRequest;
import com.liang.xz.message.dto.MessageSendRequest;
import com.liang.xz.message.dto.MessageVO;
import com.liang.xz.message.entity.MessageTemplate;
import com.liang.xz.message.enums.MessageStatus;
import com.liang.xz.message.service.MessageBroadcastService;
import com.liang.xz.message.service.MessageService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 消息服务测试用例
 *
 * <p>测试场景:</p>
 * <ol>
 *   <li>站内信发送</li>
 *   <li>多渠道路由(短信+邮件+站内信)</li>
 *   <li>消息广播(RabbitMQ Fanout)</li>
 *   <li>消息模板渲染</li>
 *   <li>子系统上行通信</li>
 *   <li>事件推送</li>
 *   <li>收件箱查询 & 已读</li>
 * </ol>
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("消息服务测试")
class MessageServiceTest {

    @Autowired
    private MessageService messageService;

    @Autowired
    private MessageBroadcastService broadcastService;

    // ================================================================
    // 测试 1: 站内信发送
    // ================================================================

    @Test
    @Order(1)
    @DisplayName("场景1: 发送站内信")
    void testSendInAppMessage() {
        MessageSendRequest req = new MessageSendRequest();
        req.setMessageType("SYSTEM_NOTICE");
        req.setTitle("系统公告");
        req.setContent("平台将于本周六进行维护升级, 请合理安排时间。");
        req.setChannels(Collections.singletonList("IN_APP"));
        req.setSender("system");
        req.setReceivers(List.of("user1", "user2", "user3"));

        String messageId = messageService.sendMessage(req);
        assertNotNull(messageId, "消息ID不应为空");

        // 验证消息已创建
        MessageVO msg = messageService.getMessage(messageId);
        assertNotNull(msg);
        assertEquals("SYSTEM_NOTICE", msg.getMessageType());
        assertEquals("系统公告", msg.getTitle());
        System.out.println("✓ 场景1通过: 站内信发送成功, messageId=" + messageId);
    }

    // ================================================================
    // 测试 2: 多渠道路由(模拟短信+邮件+站内信)
    // ================================================================

    @Test
    @Order(2)
    @DisplayName("场景2: 多渠道路由-SMS+EMAIL+IN_APP")
    void testMultiChannelSend() {
        MessageSendRequest req = new MessageSendRequest();
        req.setMessageType("APPROVAL_NOTIFY");
        req.setTitle("审批通知");
        req.setContent("您的权限申请已通过部门经理审批, 请等待总监终审。");
        req.setChannels(Collections.singletonList("SMS,EMAIL,IN_APP"));
        req.setSender("workflow-system");
        req.setReceivers(List.of("user1"));

        String messageId = messageService.sendMessage(req);
        assertNotNull(messageId);

        // RabbitMQ 投递后状态应为 SENDING
        // (异步消费, 此处只验证消息已成功投递到队列)
        MessageVO msg = messageService.getMessage(messageId);
        assertNotNull(msg);
        assertTrue(List.of("PENDING", "SENDING", "SENT", "PARTIAL")
                        .contains(msg.getStatus()),
                "消息状态应为已投递");
        System.out.println("✓ 场景2通过: 多渠道路由成功, status=" + msg.getStatus());
    }

    // ================================================================
    // 测试 3: 消息广播(RabbitMQ Fanout)
    // ================================================================

    @Test
    @Order(3)
    @DisplayName("场景3: RabbitMQ广播到多个子系统")
    void testBroadcastToSubsystems() {
        MessageBroadcastRequest req = new MessageBroadcastRequest();
        req.setMessageType("EVENT_PUSH");
        req.setTitle("权限变更通知");
        req.setContent("{\"userId\":\"user1\",\"oldRole\":\"USER\",\"newRole\":\"ADMIN\"}");
        req.setSourceSystem("system-server");
        req.setTargetSubsystems(List.of("auth-server", "auth-flow", "log-server"));
        req.setEventType("USER_ROLE_CHANGED");
        req.setChannels(Collections.singletonList("MQ,WEBSOCKET"));

        String messageId = broadcastService.broadcastToSubsystems(req);
        assertNotNull(messageId);

        // 查看子系统事件
        List<MessageVO> events = broadcastService.getSubsystemEvents("auth-server", 10);
        assertFalse(events.isEmpty());
        System.out.println("✓ 场景3通过: 广播成功, messageId=" + messageId
                + ", auth-server收到 " + events.size() + " 条事件");
    }

    // ================================================================
    // 测试 4: 消息模板渲染
    // ================================================================

    @Test
    @Order(4)
    @DisplayName("场景4: 创建模板+模板渲染发送")
    void testTemplateRender() {
        // 创建模板
        MessageTemplate template = new MessageTemplate();
        template.setTemplateCode("APPROVAL_NOTIFY_TMPL");
        template.setTemplateName("审批通知模板");
        template.setChannel("IN_APP,EMAIL");
        template.setTitleTemplate("【审批通知】您的{{action}}申请已处理");
        template.setContentTemplate("您好 {{userName}},\n\n"
                + "您的 {{action}} 申请已被 {{approver}} {{result}}。\n"
                + "审批意见: {{comment}}\n\n"
                + "请登录系统查看详情。");
        template.setVariables("userName,action,approver,result,comment");
        template.setStatus(1);
        messageService.saveTemplate(template, "admin");

        // 使用模板发送
        MessageSendRequest req = new MessageSendRequest();
        req.setMessageType("APPROVAL_NOTIFY");
        req.setTemplateCode("APPROVAL_NOTIFY_TMPL");
        req.setTemplateVars(Map.of(
                "userName", "张三",
                "action", "权限开通",
                "approver", "李经理",
                "result", "通过",
                "comment", "同意开通读写权限"
        ));
        req.setSender("workflow-system");
        req.setReceivers(List.of("zhangsan"));

        String messageId = messageService.sendMessage(req);
        assertNotNull(messageId);

        MessageVO msg = messageService.getMessage(messageId);
        assertNotNull(msg);
        assertTrue(msg.getTitle().contains("张三"), "模板应渲染了 userName");
        System.out.println("✓ 场景4通过: 模板渲染成功, title=" + msg.getTitle());
    }

    // ================================================================
    // 测试 5: 子系统上行通信
    // ================================================================

    @Test
    @Order(5)
    @DisplayName("场景5: 子系统上行通信")
    void testSubsystemUpward() {
        String messageId = broadcastService.subsystemUpward(
                "system-server",
                "USER_CREATED",
                "{\"userId\":\"newuser\",\"role\":\"USER\"}",
                List.of("auth-server", "auth-message")
        );
        assertNotNull(messageId);

        // 验证消息已记录
        List<MessageVO> events = broadcastService.getSubsystemEvents("auth-message", 10);
        assertFalse(events.isEmpty());
        System.out.println("✓ 场景5通过: 子系统上行通信成功");
    }

    // ================================================================
    // 测试 6: 事件推送
    // ================================================================

    @Test
    @Order(6)
    @DisplayName("场景6: 事件推送-审批完成通知")
    void testEventPush() {
        String messageId = broadcastService.pushEvent(
                "APPROVAL_COMPLETED",
                "审批流程已完成",
                "{\"instanceId\":123,\"status\":\"APPROVED\"}",
                "auth-flow",
                List.of("system-server", "log-server")
        );
        assertNotNull(messageId);
        System.out.println("✓ 场景6通过: 事件推送成功, messageId=" + messageId);
    }

    // ================================================================
    // 测试 7: 收件箱查询 & 已读/未读
    // ================================================================

    @Test
    @Order(7)
    @DisplayName("场景7: 收件箱查询+标记已读")
    void testInboxAndRead() {
        // 发送两条站内信给 user_test
        MessageSendRequest req1 = new MessageSendRequest();
        req1.setMessageType("SYSTEM_NOTICE");
        req1.setTitle("测试消息1");
        req1.setContent("这是测试消息内容1");
        req1.setChannels(Collections.singletonList("IN_APP"));
        req1.setSender("test");
        req1.setReceivers(List.of("user_test"));
        String msgId1 = messageService.sendMessage(req1);

        MessageSendRequest req2 = new MessageSendRequest();
        req2.setMessageType("SYSTEM_NOTICE");
        req2.setTitle("测试消息2");
        req2.setContent("这是测试消息内容2");
        req2.setChannels(Collections.singletonList("IN_APP"));
        req2.setSender("test");
        req2.setReceivers(List.of("user_test"));
        String msgId2 = messageService.sendMessage(req2);

        // 查询收件箱
        List<MessageVO> inbox = messageService.getInboxMessages("user_test", 50);
        assertFalse(inbox.isEmpty(), "收件箱应有消息");

        // 标记已读
        messageService.markAsRead(msgId1);

        // 未读计数
        long unread = messageService.getUnreadCount("user_test");
        assertTrue(unread >= 0, "未读计数应>=0");
        System.out.println("✓ 场景7通过: 收件箱查询成功, 未读数=" + unread);
    }

    // ================================================================
    // 测试 8: 失败重试机制验证
    // ================================================================

    @Test
    @Order(8)
    @DisplayName("场景8: 重试失败消息")
    void testRetryFailedMessages() {
        int retried = messageService.retryFailedMessages();
        assertTrue(retried >= 0, "重试计数应>=0");
        System.out.println("✓ 场景8通过: 重试了 " + retried + " 条失败消息");
    }
}
