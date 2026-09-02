package com.liang.xz.message.enums;

import lombok.Getter;

/**
 * 发送渠道
 */
@Getter
public enum MessageChannel {
    SMS("短信"),
    EMAIL("邮件"),
    IN_APP("站内信"),
    WEBSOCKET("实时推送"),
    MQ("消息队列广播"),
    WECHAT("微信");

    private final String desc;

    MessageChannel(String desc) {
        this.desc = desc;
    }
}
