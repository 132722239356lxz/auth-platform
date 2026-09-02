package com.liang.xz.message.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * 用户联系方式查询 —— 查询 sys_user 表获取邮箱/手机号
 *
 * <p>auth-message 与 system-server 共享 auth_platform 数据库，直接 JDBC 查询 sys_user 表</p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UserContactRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 根据用户名列表批量查询邮箱
     *
     * @param usernames 用户名列表
     * @return Map&lt;username, email&gt;，不存在的 key 不会出现在结果中
     */
    public Map<String, String> findEmailsByUsernames(Collection<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> distinctUsernames = usernames.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctUsernames.isEmpty()) {
            return Collections.emptyMap();
        }

        String placeholders = String.join(",", Collections.nCopies(distinctUsernames.size(), "?"));
        String sql = "SELECT username, email FROM sys_user WHERE username IN (" + placeholders + ") AND email IS NOT NULL AND email <> ''";

        Map<String, String> result = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            result.put(rs.getString("username"), rs.getString("email"));
        }, distinctUsernames.toArray());

        if (!result.isEmpty()) {
            log.debug("[UserContact] 查询邮箱成功: count={}, usernames={}", result.size(), result.keySet());
        }
        return result;
    }

    /**
     * 根据用户名列表批量查询手机号
     *
     * @param usernames 用户名列表
     * @return Map&lt;username, phone&gt;，不存在的 key 不会出现在结果中
     */
    public Map<String, String> findPhonesByUsernames(Collection<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> distinctUsernames = usernames.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctUsernames.isEmpty()) {
            return Collections.emptyMap();
        }

        String placeholders = String.join(",", Collections.nCopies(distinctUsernames.size(), "?"));
        String sql = "SELECT username, phone FROM sys_user WHERE username IN (" + placeholders + ") AND phone IS NOT NULL AND phone <> ''";

        Map<String, String> result = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            result.put(rs.getString("username"), rs.getString("phone"));
        }, distinctUsernames.toArray());

        if (!result.isEmpty()) {
            log.debug("[UserContact] 查询手机号成功: count={}, usernames={}", result.size(), result.keySet());
        }
        return result;
    }

    /**
     * 判断字符串是否看起来像邮箱地址（含@符号）
     */
    public static boolean looksLikeEmail(String value) {
        return value != null && value.contains("@");
    }

    /**
     * 判断字符串是否看起来像手机号（纯数字且长度11位左右）
     */
    public static boolean looksLikePhone(String value) {
        return value != null && value.matches("\\d{7,15}");
    }
}
