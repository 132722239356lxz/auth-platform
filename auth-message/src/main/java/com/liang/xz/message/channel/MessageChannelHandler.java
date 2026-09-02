package com.liang.xz.message.channel;

import com.liang.xz.message.entity.MessageRecord;
import com.liang.xz.message.enums.MessageChannel;

import java.util.List;

/**
 * 消息渠道处理器接口 —— 策略模式，各渠道独立处理发送逻辑
 *
 * <p>区别于之前的 RabbitMQ 中转方案，各处理器直接执行发送：</p>
 * <ul>
 *   <li>SMS → SmsProvider 直发短信</li>
 *   <li>EMAIL → JavaMailSender 直发邮件</li>
 *   <li>IN_APP → Redis 缓存，前端轮询</li>
 *   <li>WEBSOCKET → Redis Pub/Sub 实时推送</li>
 *   <li>MQ → RabbitMQ 子系统间广播</li>
 * </ul>
 */
public interface MessageChannelHandler {

    /**
     * 当前处理器支持的渠道
     */
    MessageChannel getChannel();

    /**
     * 执行消息发送
     *
     * @param record    消息记录（含标题、内容、类型等完整信息）
     * @param receivers 接收者列表
     * @return true=成功，false=失败
     */
    boolean handle(MessageRecord record, List<String> receivers);
}
