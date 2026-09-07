package com.liang.xz.flow.enums;

import lombok.Getter;

/**
 * <p>工作流申请场景类型</p>
 *
 * <p>用于审批通过后按类型路由到不同的赋权/业务处理器，使同一套工作流引擎
 * 可承载多种审批业务（角色权限、子系统可见权限等）。新增场景只需在此追加枚举
 * 并实现对应的 {@code ApplyPostApproveProcessor}。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Getter
public enum ApplyType {

    /** 角色权限申请（历史默认场景，审批通过后为申请人赋角色） */
    ROLE("角色权限申请"),

    /** 子系统可见权限申请（审批通过后调用 AI 决策应可见的子系统并赋权） */
    SUBSYSTEM_VISIBILITY("子系统可见权限申请");

    private final String description;

    ApplyType(String description) {
        this.description = description;
    }
}
