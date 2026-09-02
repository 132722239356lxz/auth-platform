package com.liang.xz.system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户-子系统可见性管理服务
 * <p>
 * 维护 sys_user_subsystem 表:
 * - 查询用户可见的子系统
 * - 批量分配/撤销子系统可见性
 * - 查询子系统下的用户
 *
 * @author auth-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSubsystemService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 查询用户可见的子系统列表（基于 oauth2_client_subsystem 表）
     */
    public List<Map<String, Object>> getUserSubsystems(Long userId) {
        // 关联 oauth2_client_subsystem 获取子系统名称/图标/URL/描述
        String sql = "SELECT s.id, s.client_id, s.visible, s.granted_time, s.granted_by, " +
                "c.client_name, c.scopes, " +
                "sub.id AS subsystem_id, sub.name AS subsystem_name, " +
                "sub.icon_url, sub.redirect_uri, sub.description " +
                "FROM sys_user_subsystem s " +
                "LEFT JOIN oauth2_registered_client c ON s.client_id = c.client_id " +
                "LEFT JOIN oauth2_client_subsystem sub ON c.client_id = sub.client_id AND sub.visible_portal = 1 " +
                "WHERE s.user_id = ? AND s.visible = 1 " +
                "ORDER BY sub.sort_order ASC, c.client_name ASC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("clientId", row.get("client_id"));
            item.put("clientName", row.get("client_name"));
            item.put("scopes", row.get("scopes"));
            item.put("visible", row.get("visible"));
            item.put("grantedBy", row.get("granted_by"));
            item.put("grantedTime", row.get("granted_time"));
            item.put("subsystemId", row.get("subsystem_id"));
            item.put("subsystemName", row.get("subsystem_name"));
            item.put("iconUrl", row.get("icon_url"));
            item.put("redirectUri", row.get("redirect_uri"));
            item.put("description", row.get("description"));
            result.add(item);
        }
        return result;
    }

    /**
     * 批量分配用户可见子系统(先删后插)
     */
    @Transactional
    public void assignSubsystems(Long userId, List<String> clientIds) {
        // 删除旧的关系
        jdbcTemplate.update("DELETE FROM sys_user_subsystem WHERE user_id = ?", userId);

        // 插入新的关系
        if (clientIds != null) {
            for (String clientId : clientIds) {
                jdbcTemplate.update(
                        "INSERT INTO sys_user_subsystem (user_id, client_id, visible, granted_by) " +
                                "VALUES (?, ?, 1, NULL) " +
                                "ON DUPLICATE KEY UPDATE visible=1",
                        userId, clientId);
            }
        }

        log.info("[UserSubsystem] 用户{}子系统可见性已更新: {}个", userId, clientIds != null ? clientIds.size() : 0);
    }

    /**
     * 查询所有已注册子系统（基于 oauth2_client_subsystem 表，含客户端名称等信息）
     */
    public List<Map<String, Object>> listAllSubsystems() {
        String sql = "SELECT sub.id AS subsystem_id, sub.client_id, sub.name AS subsystem_name, " +
                "sub.icon_url, sub.redirect_uri, sub.description, sub.sort_order, sub.visible_portal, " +
                "c.id, c.client_name, c.scopes, c.client_settings, c.token_settings " +
                "FROM oauth2_client_subsystem sub " +
                "LEFT JOIN oauth2_registered_client c ON sub.client_id = c.client_id " +
                "ORDER BY sub.sort_order ASC, sub.client_id ASC";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("clientId", row.get("client_id"));
            item.put("clientName", row.get("client_name"));
            item.put("scopes", row.get("scopes"));
            item.put("subsystemId", row.get("subsystem_id"));
            item.put("subsystemName", row.get("subsystem_name"));
            item.put("iconUrl", row.get("icon_url"));
            item.put("redirectUri", row.get("redirect_uri"));
            item.put("description", row.get("description"));
            item.put("sortOrder", row.get("sort_order"));
            item.put("visiblePortal", row.get("visible_portal"));
            result.add(item);
        }
        return result;
    }

    /**
     * 查询子系统下的用户列表(分页)
     */
    public Map<String, Object> getSubsystemUsers(String clientId, int page, int size) {
        int offset = (page - 1) * size;

        String countSql = "SELECT COUNT(*) FROM sys_user_subsystem WHERE client_id = ?";
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, clientId);

        String listSql = "SELECT s.id as record_id, s.user_id, s.visible, s.granted_time, " +
                "u.username, u.nickname, u.email, u.phone, u.user_type, u.enabled " +
                "FROM sys_user_subsystem s " +
                "INNER JOIN sys_user u ON s.user_id = u.id " +
                "WHERE s.client_id = ? " +
                "ORDER BY s.granted_time DESC " +
                "LIMIT ? OFFSET ?";

        List<Map<String, Object>> users = jdbcTemplate.queryForList(listSql, clientId, size, offset);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", users);
        result.put("total", total != null ? total : 0);
        result.put("page", page);
        result.put("pageSize", size);
        return result;
    }

    /**
     * 授权用户子系统可见
     */
    @Transactional
    public void grant(Long userId, String clientId, Long grantedBy) {
        jdbcTemplate.update(
                "INSERT INTO sys_user_subsystem (user_id, client_id, visible, granted_by) " +
                        "VALUES (?, ?, 1, ?) " +
                        "ON DUPLICATE KEY UPDATE visible=1",
                userId, clientId, grantedBy);
        log.info("[UserSubsystem] 授权: userId={}, clientId={}, grantedBy={}", userId, clientId, grantedBy);
    }

    /**
     * 撤销用户子系统可见
     */
    @Transactional
    public void revoke(Long userId, String clientId) {
        jdbcTemplate.update(
                "UPDATE sys_user_subsystem SET visible=0 WHERE user_id=? AND client_id=?",
                userId, clientId);
        log.info("[UserSubsystem] 撤销: userId={}, clientId={}", userId, clientId);
    }
}
