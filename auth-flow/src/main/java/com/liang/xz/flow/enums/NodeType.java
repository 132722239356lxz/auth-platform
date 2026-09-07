package com.liang.xz.flow.enums;

import lombok.Getter;

/**
 * 节点类型
 */
@Getter
public enum NodeType {
    START("开始节点"),
    APPROVAL("审批节点"),
    CONDITION("条件分支"),
    CALLBACK("回调通知"),
    END("结束节点");

    private final String desc;

    NodeType(String desc) {
        this.desc = desc;
    }
}
