package com.liang.xz.flow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.flow.dto.PermissionApplyContent;
import com.liang.xz.flow.entity.WorkflowInstance;
import com.liang.xz.flow.enums.ApplyType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>审批通过后的业务分发器</p>
 *
 * <p>当工作流(如"权限申请审批")最终审批通过时, 解析 applyContent.type(申请场景类型),
 * 路由到对应的 {@link ApplyPostApproveProcessor} 执行具体业务动作(赋角色 / 调AI赋子系统可见等)。</p>
 *
 * <p>设计说明: 通过场景类型 + 处理器策略, 使同一套工作流引擎可承载多种审批业务,
 * 新增场景只需新增 {@code ApplyType} 与对应处理器, 无需改动引擎。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionGrantService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final List<ApplyPostApproveProcessor> processors;
    private final Map<ApplyType, ApplyPostApproveProcessor> processorMap = new ConcurrentHashMap<>();

    /**
     * 审批通过后, 按 applyContent.type 路由到对应处理器执行赋权/业务动作。
     */
    public void grantOnApproved(WorkflowInstance instance) {
        if (instance.getApplyContent() == null || instance.getApplyContent().isBlank()) {
            return;
        }
        ApplyType type;
        try {
            PermissionApplyContent content =
                    objectMapper.readValue(instance.getApplyContent(), PermissionApplyContent.class);
            type = Optional.ofNullable(content.getType())
                    .map(t -> {
                        try {
                            return ApplyType.valueOf(t);
                        } catch (IllegalArgumentException e) {
                            return ApplyType.ROLE;
                        }
                    })
                    .orElse(ApplyType.ROLE);
        } catch (Exception e) {
            log.debug("[PermissionGrant] applyContent 非结构化申请, 按 ROLE 场景兜底处理: instanceId={}",
                    instance.getId());
            type = ApplyType.ROLE;
        }
        ApplyPostApproveProcessor processor = processorMap.computeIfAbsent(type, t ->
                processors.stream()
                        .filter(p -> p.supportedType() == t)
                        .findFirst()
                        .orElse(null));
        if (processor == null) {
            // 无对应处理器属于配置缺失, 视为赋权失败, 触发回滚避免审批通过却未赋权
            throw new com.liang.xz.flow.exception.GrantFailedException(
                    String.format("未找到场景处理器, 无法完成赋权: instanceId=%d, type=%s",
                            instance.getId(), type));
        }
        try {
            processor.onApproved(instance);
        } catch (com.liang.xz.flow.exception.GrantFailedException e) {
            // 业务赋权失败, 向上抛出以触发 @Transactional 整体回滚,
            // 保证"审批状态"与"业务数据"要么都成功, 要么都回滚
            throw e;
        } catch (Exception e) {
            // 处理器内部未预期的异常, 同样视为赋权失败, 包装后抛出触发回滚
            throw new com.liang.xz.flow.exception.GrantFailedException(
                    String.format("场景处理器执行异常, 赋权未完成: instanceId=%d, type=%s",
                            instance.getId(), type), e);
        }
    }
}
