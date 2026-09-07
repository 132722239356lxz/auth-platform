package com.liang.xz.flow.enums;

import lombok.Getter;

/**
 * 审批动作
 */
@Getter
public enum ApprovalAction {
    APPROVE("通过"),
    REJECT("驳回"),
    TRANSFER("转交"),
    WITHDRAW("撤回");

    private final String desc;

    ApprovalAction(String desc) {
        this.desc = desc;
    }
}
