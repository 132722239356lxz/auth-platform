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

/**
 * <p>角色权限申请场景 —— 审批通过后为目标用户赋予角色</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RolePermissionGrantProcessor implements ApplyPostApproveProcessor {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public ApplyType supportedType() {
        return ApplyType.ROLE;
    }

    @Override
    public void onApproved(WorkflowInstance instance) {
        if (instance.getApplyContent() == null || instance.getApplyContent().isBlank()) {
            return;
        }
        PermissionApplyContent content;
        try {
            content = objectMapper.readValue(instance.getApplyContent(), PermissionApplyContent.class);
        } catch (Exception e) {
            log.debug("[RoleGrant] applyContent 非结构化权限申请, 跳过赋权: instanceId={}", instance.getId());
            return;
        }
        if (content.getRoleIds() == null || content.getRoleIds().isEmpty()) {
            return;
        }
        Long userId = resolveUserId(content);
        if (userId == null) {
            throw new com.liang.xz.flow.exception.GrantFailedException(
                    String.format("目标用户不存在, 无法完成角色赋权: instanceId=%d, content=%s",
                            instance.getId(), instance.getApplyContent()));
        }
        for (Long roleId : content.getRoleIds()) {
            grantRole(userId, roleId);
        }
        log.info("[RoleGrant] 审批通过已赋权: instanceId={}, userId={}, roles={}",
                instance.getId(), userId, content.getRoleIds());
    }

    private Long resolveUserId(PermissionApplyContent content) {
        if (content.getTargetUserId() != null) {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT id FROM sys_user WHERE id = ?", Long.class, content.getTargetUserId());
            if (!ids.isEmpty()) {
                return ids.get(0);
            }
        }
        if (content.getTargetUsername() != null && !content.getTargetUsername().isBlank()) {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT id FROM sys_user WHERE username = ?", Long.class, content.getTargetUsername());
            if (!ids.isEmpty()) {
                return ids.get(0);
            }
        }
        return null;
    }

    private void grantRole(Long userId, Long roleId) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_user_role WHERE user_id = ? AND role_id = ?",
                Integer.class, userId, roleId);
        if (exists != null && exists > 0) {
            log.info("[RoleGrant] 用户已拥有角色, 跳过: userId={}, roleId={}", userId, roleId);
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
    }
}
