package com.liang.xz.flow.service;

import com.liang.xz.flow.entity.WorkflowInstance;

/**
 * <p>审批通过后业务处理器接口</p>
 *
 * <p>每一种申请场景({@code ApplyType})对应一个实现。工作流引擎在「最终审批通过」后
 * 由 {@link PermissionGrantService} 按 applyContent.type 路由到对应处理器，
 * 从而把"审批通过"与"具体业务动作(赋权/调AI等)"解耦，方便扩展新审批场景。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public interface ApplyPostApproveProcessor {

    /**
     * 该处理器对应的申请场景类型。
     */
    com.liang.xz.flow.enums.ApplyType supportedType();

    /**
     * 审批通过后的业务处理。
     *
     * @param instance 审批通过的工作流实例
     */
    void onApproved(WorkflowInstance instance);
}
