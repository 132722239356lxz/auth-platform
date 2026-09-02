package com.liang.xz.message.enums;

import lombok.Getter;

/**
 * 消息类型
 */
@Getter
public enum MessageType {
    SYSTEM_NOTICE("系统公告"),
    EVENT_PUSH("事件推送"),
    APPROVAL_NOTIFY("审批通知"),
    SUBSYSTEM_COMM("子系统通信"),
    USER_MESSAGE("用户消息");

    private final String desc;

    MessageType(String desc) {
        this.desc = desc;
    }
}
