package com.liang.xz.flow.enums;

import lombok.Getter;

/**
 * 流程实例状态
 */
@Getter
public enum WorkflowStatus {
    PENDING("审批中"),
    APPROVED("已通过"),
    REJECTED("已驳回"),
    WITHDRAWN("已撤回");

    private final String desc;

    WorkflowStatus(String desc) {
        this.desc = desc;
    }
}
