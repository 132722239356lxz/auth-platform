package com.liang.xz.message.enums;

import lombok.Getter;

/**
 * 消息状态
 */
@Getter
public enum MessageStatus {
    PENDING("待发送"),
    SENDING("发送中"),
    SENT("已发送"),
    PARTIAL("部分成功"),
    FAILED("发送失败");

    private final String desc;

    MessageStatus(String desc) {
        this.desc = desc;
    }
}
