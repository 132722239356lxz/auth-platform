package com.liang.xz.flow.exception;

/**
 * <p>审批通过后业务赋权失败异常</p>
 *
 * <p>当工作流最终审批通过、但执行具体业务动作(赋角色 / 赋子系统可见 / 调用外部决策服务等)
 * 失败时抛出。该异常为运行时异常, 触发外层 {@code @Transactional} 整体回滚,
 * 使审批状态与业务数据保持一致(要么都成功, 要么都回滚到审批前状态)。</p>
 *
 * <p>注意: 仅"真正赋权失败"才抛此异常; 业务规则判断后的正常跳过(如用户已拥有角色、
 * 无可授权候选)不应抛此异常, 否则会无谓回滚已完成的审批。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public class GrantFailedException extends RuntimeException {

    public GrantFailedException(String message) {
        super(message);
    }

    public GrantFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
