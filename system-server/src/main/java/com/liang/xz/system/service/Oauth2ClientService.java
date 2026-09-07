package com.liang.xz.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.common.core.crypto.CryptoManager;
import com.liang.xz.common.core.entity.Oauth2Client;
import com.liang.xz.common.core.entity.Oauth2ClientSubsystem;
import com.liang.xz.common.core.repository.Oauth2ClientRepository;
import com.liang.xz.common.core.repository.Oauth2ClientSubsystemRepository;
import com.liang.xz.system.dto.ClientPageQuery;
import com.liang.xz.system.dto.ClientRequest;
import com.liang.xz.system.dto.ClientResponse;
import com.liang.xz.system.dto.ClientSubsystemItem;
import com.liang.xz.system.dto.PageResponse;
import com.liang.xz.system.dto.SubsystemResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OAuth2 客户端管理服务
 *
 * <p>客户端不再直接维护回调地址，回调地址改由 {@code oauth2_client_subsystem}
 * 中每个子系统条目提供。服务在保存客户端时自动从子系统的 redirectUri
 * 聚合成 {@code oauth2_registered_client.redirect_uris}，保持 Spring Authorization Server 兼容。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Oauth2ClientService {

    private final Oauth2ClientRepository repository;
    private final Oauth2ClientSubsystemRepository subsystemRepository;
    private final CryptoManager cryptoManager;
    private final JdbcTemplate jdbcTemplate;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public List<ClientResponse> listAll() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 查询当前用户门户可见的客户端列表。
     *
     * <p>优先读取 {@code sys_user_subsystem} 用户-应用关联表，
     * 若该用户无显式关联数据，则降级返回所有已启用客户端（兼容历史数据）。</p>
     *
     * @param userId 当前登录用户ID
     * @return 用户可见的客户端列表（已启用）
     */
    public List<ClientResponse> listAccessibleClients(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }

        // 1. 查询用户显式授权可见的 client_id 列表
        List<String> grantedClientIds = jdbcTemplate.queryForList(
                "SELECT client_id FROM sys_user_subsystem WHERE user_id = ? AND visible = 1",
                String.class, userId);

        List<Oauth2Client> clients;
        if (grantedClientIds.isEmpty()) {
            // 2. 无显式关联时，返回所有已启用客户端（门户首页向后兼容）
            clients = repository.findAllEnabled();
        } else {
            // 3. 有显式关联时，仅返回这些客户端中仍启用的
            clients = repository.findByClientIdIn(grantedClientIds).stream()
                    .filter(c -> parseEnabled(c.getClientSettings()))
                    .toList();
        }

        return clients.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 分页查询客户端，支持多条件动态筛选
     */
    public PageResponse<ClientResponse> pageList(ClientPageQuery query) {
        long total = repository.countByKeyword(query.getKeyword(), query.getEnabled());
        if (total == 0) {
            return PageResponse.empty(query.getPage(), query.getPageSize());
        }
        int offset = (query.getPage() - 1) * query.getPageSize();
        List<ClientResponse> records = repository.findPageByKeyword(
                query.getKeyword(), query.getEnabled(), offset, query.getPageSize()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return PageResponse.of(records, total, query.getPage(), query.getPageSize());
    }

    public Optional<ClientResponse> getById(String id) {
        return repository.findById(id).map(this::toResponse);
    }

    public Optional<ClientResponse> getByClientId(String clientId) {
        return repository.findByClientId(clientId).map(this::toResponse);
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public ClientResponse create(ClientRequest request) {
        if (repository.findByClientId(request.getClientId()).isPresent()) {
            throw new IllegalArgumentException("客户端标识 [" + request.getClientId() + "] 已存在");
        }

        Oauth2Client entity = new Oauth2Client();
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        entity.setClientId(request.getClientId());
        entity.setClientIdIssuedAt(LocalDateTime.now());

        // 密钥：不传则自动生成 32 位随机字符串
        String secret = (request.getClientSecret() == null || request.getClientSecret().isBlank())
                ? generateSecret() : request.getClientSecret();
        entity.setClientSecret(cryptoManager.encrypt(secret));
        entity.setClientSecretExpiresAt(request.getClientSecretExpiresAt());
        entity.setClientName(request.getClientName());

        // authMethods: 不传默认 client_secret_basic + client_secret_post
        List<String> authMethods = (request.getAuthMethods() == null || request.getAuthMethods().isEmpty())
                ? List.of("client_secret_basic", "client_secret_post") : request.getAuthMethods();
        entity.setClientAuthenticationMethods(String.join(",", authMethods));

        entity.setAuthorizationGrantTypes(String.join(",", request.getGrantTypes()));

        // scopes: 不传默认 openid
        List<String> scopes = (request.getScopes() == null || request.getScopes().isEmpty())
                ? List.of("openid") : request.getScopes();
        entity.setScopes(String.join(",", scopes));

        entity.setClientSettings(buildClientSettingsJson(request.getEnabled(), request.getRequireAuthorizationConsent()));
        entity.setTokenSettings(buildTokenSettingsJson(request.getTokenTtl(), request.getRefreshTtl()));

        repository.insert(entity);

        // 同步子系统（事务内）
        List<Oauth2ClientSubsystem> savedSubsystems =
                syncSubsystems(entity.getClientId(), request.getSubsystems());

        // 从子系统聚合 redirect_uris 回写到 oauth2_registered_client
        updateClientRedirectUris(entity.getClientId(), savedSubsystems);

        return toResponse(entity);
    }

    /** 生成 32 位随机安全密钥 */
    private String generateSecret() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Transactional
    public Optional<ClientResponse> update(String id, ClientRequest request) {
        return repository.findById(id).map(entity -> {
            entity.setClientName(request.getClientName());
            if (request.getAuthMethods() != null && !request.getAuthMethods().isEmpty()) {
                entity.setClientAuthenticationMethods(String.join(",", request.getAuthMethods()));
            }
            entity.setAuthorizationGrantTypes(String.join(",", request.getGrantTypes()));
            if (request.getScopes() != null && !request.getScopes().isEmpty()) {
                entity.setScopes(String.join(",", request.getScopes()));
            }
            entity.setClientSecretExpiresAt(request.getClientSecretExpiresAt());
            entity.setClientSettings(buildClientSettingsJson(request.getEnabled(), request.getRequireAuthorizationConsent()));
            entity.setTokenSettings(buildTokenSettingsJson(request.getTokenTtl(), request.getRefreshTtl()));
            repository.update(entity);

            // 同步子系统（事务内，diff：增/改/删）
            List<Oauth2ClientSubsystem> syncedSubsystems =
                    syncSubsystems(entity.getClientId(), request.getSubsystems());

            // 重新聚合 redirect_uris 写回 oauth2_registered_client
            updateClientRedirectUris(entity.getClientId(), syncedSubsystems);

            return toResponse(entity);
        });
    }

    @Transactional
    public Optional<ClientResponse> resetSecret(String id, String newSecret) {
        String secret = (newSecret == null || newSecret.isBlank()) ? generateSecret() : newSecret;
        return repository.findById(id).map(entity -> {
            String encrypted = cryptoManager.encrypt(secret);
            repository.updateSecret(id, encrypted);
            entity.setClientSecret(encrypted);
            return toResponse(entity);
        });
    }

    @Transactional
    public Optional<ClientResponse> enable(String id) {
        return toggleStatus(id, true);
    }

    @Transactional
    public Optional<ClientResponse> disable(String id) {
        return toggleStatus(id, false);
    }

    private Optional<ClientResponse> toggleStatus(String id, boolean enabled) {
        return repository.findById(id).map(entity -> {
            // 上下线时不重置 require-authorization-consent，沿用现有设置
            boolean keepConsent = entity.getClientSettings() != null
                    && entity.getClientSettings().contains("\"require-authorization-consent\":true");
            entity.setClientSettings(buildClientSettingsJson(enabled, keepConsent));
            repository.update(entity);
            return toResponse(entity);
        });
    }

    @Transactional
    public boolean delete(String id) {
        return repository.findById(id).map(entity -> {
            // 级联删除该客户端下的所有子系统
            int deleted = subsystemRepository.deleteByClientId(entity.getClientId());
            if (deleted > 0) {
                log.info("[Oauth2Client] 级联删除客户端 {} 的子系统 {} 条", entity.getClientId(), deleted);
            }
            repository.deleteById(id);
            return true;
        }).orElse(false);
    }

    private String buildClientSettingsJson(Boolean enabled, Boolean requireAuthorizationConsent) {
        boolean isEnabled = enabled != null && enabled;
        // consent 默认 false: 内部受信应用无需每次授权时再点一次确认页
        //   用户可通过 ClientRequest.requireAuthorizationConsent=true 显式开启
        boolean consentRequired = requireAuthorizationConsent != null && requireAuthorizationConsent;
        return String.format(
                "{\"@class\":\"java.util.Collections$UnmodifiableMap\"," +
                "\"settings.client.require-authorization-consent\":%s," +
                "\"settings.client.require-proof-key\":false," +
                "\"status\":\"%s\"}",
                consentRequired, isEnabled ? "enabled" : "disabled");
    }

    private String buildTokenSettingsJson(Long tokenTtl, Long refreshTtl) {
        long accessTtl = tokenTtl != null && tokenTtl > 0 ? tokenTtl : 3600;
        long refreshTtlVal = refreshTtl != null && refreshTtl > 0 ? refreshTtl : 43200;
        return String.format(
                "{\"@class\":\"java.util.Collections$UnmodifiableMap\"," +
                "\"settings.token.access-token-time-to-live\":[\"java.time.Duration\",%d.000000000]," +
                "\"settings.token.refresh-token-time-to-live\":[\"java.time.Duration\",%d.000000000]," +
                "\"settings.token.reuse-refresh-tokens\":false}",
                accessTtl, refreshTtlVal);
    }

    private boolean parseEnabled(String clientSettings) {
        if (clientSettings == null) return true;
        return !clientSettings.contains("\"status\":\"disabled\"");
    }

    private ClientResponse toResponse(Oauth2Client entity) {
        return ClientResponse.builder()
                .id(entity.getId())
                .clientId(entity.getClientId())
                .clientName(entity.getClientName())
                .clientIdIssuedAt(entity.getClientIdIssuedAt())
                .clientSecretExpiresAt(entity.getClientSecretExpiresAt())
                .scopes(splitToList(entity.getScopes()))
                .grantTypes(splitToList(entity.getAuthorizationGrantTypes()))
                .authMethods(splitToList(entity.getClientAuthenticationMethods()))
                .tokenTtl(parseTokenTtl(entity.getTokenSettings()))
                .refreshTtl(parseRefreshTtl(entity.getTokenSettings()))
                .enabled(parseEnabled(entity.getClientSettings()))
                .requireAuthorizationConsent(
                        entity.getClientSettings() != null
                                && entity.getClientSettings().contains("\"require-authorization-consent\":true"))
                .subsystems(loadSubsystemResponses(entity.getClientId(), entity.getClientName()))
                .build();
    }

    /**
     * 加载客户端下所有子系统响应（按 sort_order ASC, id ASC 排序）。
     */
    private List<SubsystemResponse> loadSubsystemResponses(String clientId, String clientName) {
        return subsystemRepository.findByClientId(clientId).stream()
                .map(s -> toSubsystemResponse(s, clientName))
                .collect(Collectors.toList());
    }

    private SubsystemResponse toSubsystemResponse(Oauth2ClientSubsystem entity, String clientName) {
        return SubsystemResponse.builder()
                .id(entity.getId())
                .clientId(entity.getClientId())
                .clientName(clientName)
                .code(entity.getCode())
                .name(entity.getName())
                .iconUrl(entity.getIconUrl())
                .redirectUri(entity.getRedirectUri())
                .description(entity.getDescription())
                .sortOrder(entity.getSortOrder())
                .visiblePortal(entity.getVisiblePortal())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    // ======================== 子系统同步逻辑（diff 算法） ========================

    /**
     * 同步子系统列表：以入参为准，diff 增/改/删。
     *
     * @return 同步后所有子系统（按 sort_order ASC 排序）
     */
    private List<Oauth2ClientSubsystem> syncSubsystems(String clientId, List<ClientSubsystemItem> items) {
        // 1. 读取现有
        Map<Long, Oauth2ClientSubsystem> existingMap = subsystemRepository.findByClientId(clientId).stream()
                .collect(Collectors.toMap(Oauth2ClientSubsystem::getId, e -> e));

        // 2. 入参为空时 → 清空
        if (items == null || items.isEmpty()) {
            if (!existingMap.isEmpty()) {
                for (Long id : existingMap.keySet()) {
                    subsystemRepository.deleteById(id);
                }
                log.info("[Oauth2Client] 客户端 {} 子系统已全部清空", clientId);
            }
            return List.of();
        }

        // 3. 入参非空 → diff
        Set<Long> incomingIds = new HashSet<>();
        for (ClientSubsystemItem item : items) {
            if (item.getId() != null && existingMap.containsKey(item.getId())) {
                // 更新
                Oauth2ClientSubsystem entity = existingMap.get(item.getId());
                applyItem(item, entity);
                subsystemRepository.update(entity);
                incomingIds.add(item.getId());
            } else {
                // 新增
                Oauth2ClientSubsystem entity = new Oauth2ClientSubsystem();
                entity.setClientId(clientId);
                applyItem(item, entity);
                Long newId = subsystemRepository.insert(entity);
                entity.setId(newId);
                existingMap.put(newId, entity);
                incomingIds.add(newId);
            }
        }

        // 4. 删除未出现在入参中的
        for (Map.Entry<Long, Oauth2ClientSubsystem> entry : existingMap.entrySet()) {
            if (!incomingIds.contains(entry.getKey())) {
                subsystemRepository.deleteById(entry.getKey());
            }
        }

        // 5. 返回最终列表
        return existingMap.values().stream()
                .sorted(Comparator.comparing(
                        s -> Optional.ofNullable(s.getSortOrder()).orElse(0)
                ))
                .collect(Collectors.toList());
    }

    /**
     * 将 {@link ClientSubsystemItem} 的字段应用到实体。
     * <p>code 为空时回退到 {@code web}，保证历史数据兼容。</p>
     */
    private void applyItem(ClientSubsystemItem item, Oauth2ClientSubsystem entity) {
        entity.setCode(item.getCode() != null ? item.getCode() : "web");
        entity.setName(item.getName());
        entity.setIconUrl(item.getIconUrl());
        entity.setRedirectUri(item.getRedirectUri());
        entity.setDescription(item.getDescription());
        entity.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : 0);
        entity.setVisiblePortal(item.getVisiblePortal() != null ? item.getVisiblePortal() : true);
    }

    /**
     * 从子系统聚合 redirect_uris 写回 {@code oauth2_registered_client}。
     *
     * <p>Spring Authorization Server 在做 OAuth2 流程时需要读客户端的 {@code redirect_uris}，
     * 因此即使回调地址由子系统维护，也要在客户端行上保留一份聚合后的 JSON，
     * 保证鉴权流程不报错。</p>
     */
    private void updateClientRedirectUris(String clientId, List<Oauth2ClientSubsystem> subsystems) {
        List<Map<String, String>> uris = subsystems.stream()
                .filter(s -> s.getRedirectUri() != null && !s.getRedirectUri().isBlank())
                .map(s -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("uri", s.getRedirectUri());
                    // platform 跟随 subsystem.code，区分 Web / 小程序 / App 等数据来源
                    item.put("platform", s.getCode() != null ? s.getCode() : "web");
                    // 把 subsystem.name 写到 label，便于审计时区分
                    item.put("label", s.getName() != null ? s.getName() : "");
                    return item;
                })
                .collect(Collectors.toList());

        String json;
        if (uris.isEmpty()) {
            // 没有任何子系统配置回调地址 → 写空数组
            json = "[]";
        } else {
            try {
                json = OBJECT_MAPPER.writeValueAsString(uris);
            } catch (Exception e) {
                log.error("[Oauth2Client] 聚合 redirect_uris 失败: {}", e.getMessage(), e);
                return;
            }
        }

        jdbcTemplate.update(
                "UPDATE oauth2_registered_client SET redirect_uris = ? WHERE client_id = ?",
                json, clientId);
    }

    // ======================== 通用工具方法 ========================

    private Long parseTokenTtl(String tokenSettings) {
        if (tokenSettings == null) return 3600L;
        try {
            String marker = "\"settings.token.access-token-time-to-live\":[\"java.time.Duration\",";
            int idx = tokenSettings.indexOf(marker);
            if (idx < 0) return 3600L;
            String sub = tokenSettings.substring(idx + marker.length());
            int dotIdx = sub.indexOf('.');
            if (dotIdx < 0) return 3600L;
            return Long.parseLong(sub.substring(0, dotIdx));
        } catch (Exception e) {
            return 3600L;
        }
    }

    private Long parseRefreshTtl(String tokenSettings) {
        if (tokenSettings == null) return 43200L;
        try {
            String marker = "\"settings.token.refresh-token-time-to-live\":[\"java.time.Duration\",";
            int idx = tokenSettings.indexOf(marker);
            if (idx < 0) return 43200L;
            String sub = tokenSettings.substring(idx + marker.length());
            int dotIdx = sub.indexOf('.');
            if (dotIdx < 0) return 43200L;
            return Long.parseLong(sub.substring(0, dotIdx));
        } catch (Exception e) {
            return 43200L;
        }
    }

    private List<String> splitToList(String str) {
        if (str == null || str.trim().isEmpty()) return Collections.emptyList();
        return Arrays.stream(str.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
