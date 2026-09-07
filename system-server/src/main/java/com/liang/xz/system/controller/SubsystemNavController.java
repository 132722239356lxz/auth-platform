package com.liang.xz.system.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.system.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 门户子系统导航接口
 * <p>
 * 基于当前登录用户的 sys_user_subsystem 可见性关系,
 * 返回该用户可见的子系统列表（使用 oauth2_client_subsystem 表）
 */
@Slf4j
@RestController
@RequestMapping("/api/subsystem")
@RequiredArgsConstructor
public class SubsystemNavController {

    private final JdbcTemplate jdbcTemplate;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @GetMapping("/nav")
    public ApiResponse<List<Map<String, Object>>> nav(Authentication authentication) {
        // 从JWT获取当前用户ID
        Long userId = extractUserId(authentication);

        if (userId == null) {
            log.warn("[SubsystemNav] 无法获取用户ID, 返回空列表");
            return ApiResponse.success(List.of());
        }

        // 基于 sys_user_subsystem + oauth2_registered_client + oauth2_client_subsystem 联合查询
        // 从子系统表获取独立的图标、URL、描述、平台标识信息
        String sql = "SELECT c.client_id, c.client_name, c.authorization_grant_types, " +
                "c.redirect_uris, c.scopes, " +
                "sub.id AS subsystem_id, sub.code AS subsystem_code, sub.name AS subsystem_name, " +
                "sub.icon_url, sub.redirect_uri, sub.description, sub.sort_order " +
                "FROM sys_user_subsystem s " +
                "INNER JOIN oauth2_registered_client c ON s.client_id = c.client_id " +
                "LEFT JOIN oauth2_client_subsystem sub ON c.client_id = sub.client_id AND sub.visible_portal = 1 " +
                "WHERE s.user_id = ? AND s.visible = 1 " +
                "ORDER BY sub.sort_order ASC, c.client_name ASC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String grants = String.valueOf(row.get("authorization_grant_types"));
            boolean ssoEnabled = grants != null && grants.contains("authorization_code");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("subsystemId", row.get("subsystem_id"));
            item.put("clientId", row.get("client_id"));
            item.put("clientName", row.get("client_name"));
            item.put("subsystemCode", row.get("subsystem_code"));
            item.put("subsystemName", row.get("subsystem_name"));
            item.put("ssoEnabled", ssoEnabled);
            item.put("authorizeEndpoint", "/auth-server/oauth2/authorize");
            item.put("redirectUris", split(String.valueOf(row.get("redirect_uris"))));
            item.put("scopes", split(String.valueOf(row.get("scopes"))));
            item.put("iconUrl", row.get("icon_url"));
            item.put("redirectUri", row.get("redirect_uri"));
            item.put("description", row.get("description"));
            item.put("sortOrder", row.get("sort_order"));
            result.add(item);
        }

        log.debug("[SubsystemNav] 用户{}可见子系统: {}个", userId, result.size());
        return ApiResponse.success(result);
    }

    /**
     * 从 Authentication 中提取用户ID
     */
    private Long extractUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String userIdStr = jwt.getClaimAsString("user_id");
            if (userIdStr != null && !userIdStr.isEmpty()) {
                try {
                    return Long.parseLong(userIdStr);
                } catch (NumberFormatException e) {
                    log.warn("[SubsystemNav] user_id格式无效: {}", userIdStr);
                }
            }
        }
        return null;
    }

    /**
     * 解析 redirect_uris DB 列（兼容 JSON 新格式和逗号分隔旧格式）。
     */
    private List<String> split(String str) {
        if (str == null || str.isBlank()) {
            return List.of();
        }
        // 新格式: JSON 数组
        if (str.trim().startsWith("[")) {
            try {
                List<Map<String, String>> items = OBJECT_MAPPER.readValue(str,
                        new TypeReference<List<Map<String, String>>>() {});
                return items.stream()
                        .map(item -> item.get("uri"))
                        .filter(uri -> uri != null && !uri.isBlank())
                        .toList();
            } catch (Exception e) {
                log.warn("[SubsystemNav] JSON redirect_uris 解析失败，fallback: {}", e.getMessage());
            }
        }
        // 旧格式: 逗号分隔
        return Arrays.stream(str.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
