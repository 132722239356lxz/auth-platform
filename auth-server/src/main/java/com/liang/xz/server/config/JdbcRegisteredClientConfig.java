package com.liang.xz.server.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.common.core.crypto.CryptoManager;
import com.liang.xz.common.core.entity.Oauth2Client;
import com.liang.xz.common.core.repository.Oauth2ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>数据库客户端注册配置</p>
 *
 * <p>将 oauth2_registered_client 表中的客户端注册到 Spring Authorization Server</p>
 *
 * <p>认证流程:</p>
 * <ol>
 *   <li>客户端请求 /oauth2/token 携带 client_id + client_secret</li>
 *   <li>Spring Security 调用 findByClientId 查找客户端</li>
 *   <li>从数据库取出加密的 client_secret</li>
 *   <li>使用 CryptoManager 解密得到明文</li>
 *   <li>使用 BCryptPasswordEncoder 编码后与请求中的密钥匹配</li>
 * </ol>
 *
 * <p>上下线控制:</p>
 * <ul>
 *   <li>client_settings 中包含 status 字段: enabled/disabled</li>
 *   <li>disabled 的客户端会在认证时被拒绝</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class JdbcRegisteredClientConfig {

    private final Oauth2ClientRepository repository;
    private final CryptoManager cryptoManager;
    private final PasswordEncoder passwordEncoder;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 自定义 RegisteredClientRepository 实现
     * 从 oauth2_registered_client 表加载客户端并解密密钥
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        return new RegisteredClientRepository() {

            @Override
            public void save(RegisteredClient registeredClient) {
                // 客户端管理通过 ClientManageController API 操作，此处不需要实现
            }

            @Override
            public RegisteredClient findById(String id) {
                return repository.findById(id)
                        .map(this::toRegisteredClient)
                        .orElse(null);
            }

            @Override
            public RegisteredClient findByClientId(String clientId) {
                return repository.findByClientId(clientId)
                        .map(this::toRegisteredClient)
                        .orElse(null);
            }

            /**
             * 将数据库实体转换为 Spring Security 的 RegisteredClient
             *
             * <p>转换流程:</p>
             * <ol>
             *   <li>检查客户端状态，disabled 的客户端拒绝加载(阻止认证)</li>
             *   <li>使用 CryptoManager 解密 client_secret</li>
             *   <li>使用 BCryptPasswordEncoder 编码后设置</li>
             *   <li>解析授权模式、scope、回调地址等配置</li>
             * </ol>
             */
            private RegisteredClient toRegisteredClient(Oauth2Client entity) {
                // ★ 上下线控制: disabled 客户端拒绝加载
                if (entity.getClientSettings() != null
                        && entity.getClientSettings().contains("\"status\":\"disabled\"")) {
                    log.warn("[ClientRepo] 客户端 {} 已下线，拒绝认证", entity.getClientId());
                    return null; // 返回null, Spring Security会将其视为客户端不存在
                }

                // ★ 使用 CryptoManager 解密 client_secret
                String decryptedSecret = cryptoManager.decrypt(entity.getClientSecret());

                // ★ 检查解密后的值是否已经是 BCrypt 哈希（数据库存储了 {bcrypt} 前缀或直接存了哈希值）
                //    避免对已哈希的值二次编码导致认证失败
                boolean alreadyHashed = decryptedSecret != null
                        && (decryptedSecret.startsWith("$2a$")
                            || decryptedSecret.startsWith("$2b$")
                            || decryptedSecret.startsWith("$2y$"));

                // client_secret_expires_at 为 NULL 时视为永不过期，用远未来时间兜底
                // Spring RegisteredClient.Builder 不允许 null (Assert.notNull)
                Instant secretExpiresAt = entity.getClientSecretExpiresAt() != null
                        ? entity.getClientSecretExpiresAt().toInstant(ZoneOffset.UTC)
                        : Instant.now().plus(100 * 365, java.time.temporal.ChronoUnit.DAYS);

                RegisteredClient.Builder builder = RegisteredClient.withId(entity.getId())
                        .clientId(entity.getClientId())
                        .clientSecret(alreadyHashed ? decryptedSecret : passwordEncoder.encode(decryptedSecret))
                        .clientIdIssuedAt(entity.getClientIdIssuedAt() != null
                                ? entity.getClientIdIssuedAt().toInstant(ZoneOffset.UTC)
                                : Instant.now())
                        .clientSecretExpiresAt(secretExpiresAt)
                        .clientName(entity.getClientName())
                        // 解析 scope
                        .scopes(scopes -> scopes.addAll(
                                split(entity.getScopes())))
                        // 解析授权模式
                        .authorizationGrantTypes(types -> {
                            for (String gt : split(entity.getAuthorizationGrantTypes())) {
                                types.add(resolveGrantType(gt));
                            }
                        })
                        // 解析回调地址
                        .redirectUris(uris -> {
                            List<String> list = split(entity.getRedirectUris());
                            if (!list.isEmpty()) uris.addAll(list);
                        })
                        // 解析登出回调地址
                        .postLogoutRedirectUris(uris -> {
                            List<String> list = split(entity.getPostLogoutRedirectUris());
                            if (!list.isEmpty()) uris.addAll(list);
                        })
                        // 解析认证方式
                        .clientAuthenticationMethods(methods -> {
                            for (String method : split(entity.getClientAuthenticationMethods())) {
                                methods.add(resolveAuthMethod(method));
                            }
                        })
                        // 使用数据库中的 token_settings
                        .tokenSettings(TokenSettings.builder()
                                .accessTokenTimeToLive(Duration.ofSeconds(
                                        parseTtl(entity.getTokenSettings(), "access-token-time-to-live", 3600L)))
                                .refreshTokenTimeToLive(Duration.ofSeconds(
                                        parseTtl(entity.getTokenSettings(), "refresh-token-time-to-live", 43200L)))
                                .reuseRefreshTokens(false)
                                .build())
                        // 使用数据库中的 client_settings
                        .clientSettings(ClientSettings.builder()
                                .requireAuthorizationConsent(
                                        parseBoolean(entity.getClientSettings(), "require-authorization-consent", true))
                                .build());

                return builder.build();
            }
        };
    }

    // ======================== 解析辅助方法 ========================

    /** 逗号分隔字符串 → List（兼容旧格式），或 JSON 数组 → 提取 URI 列表（新格式） */
    private List<String> split(String str) {
        if (str == null || str.isBlank()) return List.of();
        // 新格式: JSON 数组 [{"uri":"...","platform":"..."}]
        if (str.trim().startsWith("[")) {
            try {
                List<Map<String, String>> items = OBJECT_MAPPER.readValue(str,
                        new TypeReference<List<Map<String, String>>>() {});
                return items.stream()
                        .map(item -> item.get("uri"))
                        .filter(uri -> uri != null && !uri.isBlank())
                        .toList();
            } catch (Exception e) {
                log.warn("[ClientRepo] JSON redirect_uris 解析失败，fallback: {}", e.getMessage());
            }
        }
        // 旧格式: 逗号分隔纯 URL
        return Arrays.stream(str.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /** 授权模式字符串 → AuthorizationGrantType */
    private AuthorizationGrantType resolveGrantType(String grantType) {
        return switch (grantType.trim().toLowerCase()) {
            case "authorization_code" -> AuthorizationGrantType.AUTHORIZATION_CODE;
            case "client_credentials" -> AuthorizationGrantType.CLIENT_CREDENTIALS;
            case "refresh_token" -> AuthorizationGrantType.REFRESH_TOKEN;
            case "device_code" -> AuthorizationGrantType.DEVICE_CODE;
            default -> throw new IllegalArgumentException("不支持的授权模式: " + grantType);
        };
    }

    /** 认证方式字符串 → ClientAuthenticationMethod */
    private ClientAuthenticationMethod resolveAuthMethod(String method) {
        return switch (method.trim().toLowerCase()) {
            case "client_secret_basic" -> ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
            case "client_secret_post" -> ClientAuthenticationMethod.CLIENT_SECRET_POST;
            case "client_secret_jwt" -> ClientAuthenticationMethod.CLIENT_SECRET_JWT;
            case "private_key_jwt" -> ClientAuthenticationMethod.PRIVATE_KEY_JWT;
            case "none" -> ClientAuthenticationMethod.NONE;
            default -> ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
        };
    }

    /** 从 token_settings JSON 解析 TTL */
    private long parseTtl(String tokenSettings, String key, long defaultVal) {
        if (tokenSettings == null) return defaultVal;
        try {
            String marker = "\"settings.token." + key + "\":[\"java.time.Duration\",";
            int idx = tokenSettings.indexOf(marker);
            if (idx < 0) return defaultVal;
            String sub = tokenSettings.substring(idx + marker.length());
            int dotIdx = sub.indexOf('.');
            if (dotIdx < 0) return defaultVal;
            return Long.parseLong(sub.substring(0, dotIdx));
        } catch (Exception e) {
            return defaultVal;
        }
    }

    /** 从 client_settings JSON 解析布尔值 */
    private boolean parseBoolean(String clientSettings, String key, boolean defaultVal) {
        if (clientSettings == null) return defaultVal;
        String marker = "\"settings.client." + key + "\":";
        int idx = clientSettings.indexOf(marker);
        if (idx < 0) return defaultVal;
        String sub = clientSettings.substring(idx + marker.length());
        return sub.trim().startsWith("true");
    }
}
