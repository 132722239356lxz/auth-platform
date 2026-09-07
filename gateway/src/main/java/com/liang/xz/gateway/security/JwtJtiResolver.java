package com.liang.xz.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * <p>JWT 声明解析器 —— 从 Token 中快速提取 {@code jti}（仅解析，不验签）</p>
 *
 * <p><b>用途：</b>网关需要用 jti 查询吊销黑名单。此处只读取 Payload 内容，
 * <b>不做签名校验</b>，因为验签由下游资源服务器（JwtDecoder）负责，
 * 网关侧重复验签会浪费 CPU。因此本类的输出<b>不可作为认证凭据</b>，
 * 仅用于黑名单查询。</p>
 *
 * <p>解析失败（格式错误、编码异常）时返回 null，由调用方降级放行，
 * 最终安全性由资源服务器的验签保证。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
public final class JwtJtiResolver {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JwtJtiResolver() {
    }

    /**
     * 提取 JWT 的 jti 声明。
     *
     * @param token 裸 Token 字符串（不含 "Bearer " 前缀）
     * @return jti；解析失败或不存在返回 null
     */
    public static String resolveJti(String token) {
        if (token == null || Strings.isBlank(token)) {
            return null;
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode node = OBJECT_MAPPER.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
            JsonNode jti = node.get("jti");
            return jti == null || jti.isNull() ? null : jti.asText();
        } catch (Exception e) {
            log.debug("[Gateway] 解析JWT的jti失败: {}", e.getMessage());
            return null;
        }
    }
}
