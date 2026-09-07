package com.liang.xz.system.service;

import com.liang.xz.system.dto.AuditPageQuery;
import com.liang.xz.system.dto.AuthorizationRecordResponse;
import com.liang.xz.system.dto.PageResponse;
import com.liang.xz.system.dto.RevokeLogRequest;
import com.liang.xz.system.dto.RevokeLogResponse;
import com.liang.xz.system.entity.AuthorizationRecord;
import com.liang.xz.system.entity.TokenRevokeLog;
import com.liang.xz.system.repository.AuthorizationRecordRepository;
import com.liang.xz.common.core.repository.Oauth2ClientRepository;
import com.liang.xz.system.repository.TokenRevokeLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 授权审计服务
 */
@Service
@RequiredArgsConstructor
public class AuthorizationAuditService {

    private final AuthorizationRecordRepository authRepository;
    private final TokenRevokeLogRepository revokeLogRepository;
    private final Oauth2ClientRepository clientRepository;

    public List<AuthorizationRecordResponse> findRecordsByClient(String registeredClientId) {
        return authRepository.findByClientId(registeredClientId).stream()
                .map(this::toAuthResponse)
                .collect(Collectors.toList());
    }

    public List<AuthorizationRecordResponse> findRecordsByPrincipal(String principalName) {
        return authRepository.findByPrincipal(principalName).stream()
                .map(this::toAuthResponse)
                .collect(Collectors.toList());
    }

    public List<AuthorizationRecordResponse> findRecordsByPrincipalAndClient(
            String principalName, String registeredClientId) {
        return authRepository.findByPrincipalAndClient(principalName, registeredClientId).stream()
                .map(this::toAuthResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> forceRevoke(RevokeLogRequest request) {
        List<AuthorizationRecord> records;
        if ("ALL".equalsIgnoreCase(request.getTokenType())) {
            records = authRepository.findByPrincipalAndClient(
                    request.getUserId(), request.getClientId());
        } else if ("ACCESS_TOKEN".equalsIgnoreCase(request.getTokenType())) {
            records = authRepository.findByPrincipalAndClient(
                    request.getUserId(), request.getClientId()).stream()
                    .filter(r -> r.getAccessTokenValue() != null)
                    .collect(Collectors.toList());
        } else {
            records = authRepository.findByPrincipalAndClient(
                    request.getUserId(), request.getClientId()).stream()
                    .filter(r -> r.getRefreshTokenValue() != null)
                    .collect(Collectors.toList());
        }

        int revokedCount = 0;
        for (AuthorizationRecord record : records) {
            if (record.getAccessTokenValue() != null) {
                writeRevokeLog(request, record, "ACCESS_TOKEN", record.getAccessTokenValue());
            }
            if (record.getRefreshTokenValue() != null) {
                writeRevokeLog(request, record, "REFRESH_TOKEN", record.getRefreshTokenValue());
            }
            authRepository.deleteById(record.getId());
            revokedCount++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", request.getUserId());
        result.put("clientId", request.getClientId());
        result.put("revokedCount", revokedCount);
        result.put("message", "成功吊销 " + revokedCount + " 条授权记录");
        return result;
    }

    public List<RevokeLogResponse> findRevokeLogs(int limit, int offset) {
        return revokeLogRepository.findAll(limit, offset).stream()
                .map(this::toRevokeLogResponse)
                .collect(Collectors.toList());
    }

    /**
     * 分页查询吊销日志，支持多条件动态筛选
     */
    public PageResponse<RevokeLogResponse> pageRevokeLogs(AuditPageQuery query) {
        long total = revokeLogRepository.countByFilter(
                query.getClientId(), query.getUsername(), query.getRevokeType());
        if (total == 0) {
            return PageResponse.empty(query.getPage(), query.getPageSize());
        }
        int offset = (query.getPage() - 1) * query.getPageSize();
        List<RevokeLogResponse> records = revokeLogRepository.findPageByFilter(
                query.getClientId(), query.getUsername(), query.getRevokeType(),
                offset, query.getPageSize()).stream()
                .map(this::toRevokeLogResponse)
                .collect(Collectors.toList());
        return PageResponse.of(records, total, query.getPage(), query.getPageSize());
    }

    public List<RevokeLogResponse> findRevokeLogsByUser(String userId) {
        return revokeLogRepository.findByUserId(userId).stream()
                .map(this::toRevokeLogResponse)
                .collect(Collectors.toList());
    }

    public List<RevokeLogResponse> findRevokeLogsByClient(String clientId) {
        return revokeLogRepository.findByClientId(clientId).stream()
                .map(this::toRevokeLogResponse)
                .collect(Collectors.toList());
    }

    public List<RevokeLogResponse> findRevokeLogsByType(int revokeType) {
        return revokeLogRepository.findByRevokeType(revokeType).stream()
                .map(this::toRevokeLogResponse)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalClients", clientRepository.count());
        stats.put("totalAuthorizations", authRepository.countAll());
        stats.put("totalRevokeLogs", revokeLogRepository.countAll());
        return stats;
    }

    public Map<String, Object> getClientStats(String registeredClientId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("clientId", registeredClientId);
        stats.put("activeTokens", authRepository.countActiveTokensByClient(registeredClientId));
        return stats;
    }

    private void writeRevokeLog(RevokeLogRequest request, AuthorizationRecord record,
                                 String tokenType, String tokenValue) {
        TokenRevokeLog log = TokenRevokeLog.builder()
                .userId(request.getUserId())
                .clientId(request.getClientId())
                .tokenType(tokenType)
                .tokenSnip(snipToken(tokenValue))
                .revokeType(request.getRevokeType())
                .createTime(LocalDateTime.now())
                .remark(request.getRemark())
                .build();
        revokeLogRepository.insert(log);
    }

    private String snipToken(String token) {
        if (token == null || token.length() <= 12) return token;
        return token.substring(0, 8) + "***" + token.substring(token.length() - 4);
    }

    private AuthorizationRecordResponse toAuthResponse(AuthorizationRecord r) {
        String clientName = clientRepository.findById(r.getRegisteredClientId())
                .map(c -> c.getClientName())
                .orElse("未知客户端");

        return AuthorizationRecordResponse.builder()
                .id(r.getId())
                .clientId(r.getRegisteredClientId())
                .clientName(clientName)
                .principalName(r.getPrincipalName())
                .authorizationGrantType(r.getAuthorizationGrantType())
                .authorizedScopes(r.getAuthorizedScopes() != null
                        ? Arrays.asList(r.getAuthorizedScopes().split(","))
                        : Collections.emptyList())
                .accessTokenSnip(snipToken(r.getAccessTokenValue()))
                .accessTokenIssuedAt(r.getAccessTokenIssuedAt())
                .accessTokenExpiresAt(r.getAccessTokenExpiresAt())
                .accessTokenExpired(r.getAccessTokenExpiresAt() != null
                        && r.getAccessTokenExpiresAt().isBefore(LocalDateTime.now()))
                .refreshTokenSnip(snipToken(r.getRefreshTokenValue()))
                .refreshTokenIssuedAt(r.getRefreshTokenIssuedAt())
                .refreshTokenExpiresAt(r.getRefreshTokenExpiresAt())
                .refreshTokenExpired(r.getRefreshTokenExpiresAt() != null
                        && r.getRefreshTokenExpiresAt().isBefore(LocalDateTime.now()))
                .authorizationCodeSnip(snipToken(r.getAuthorizationCodeValue()))
                .authorizationCodeExpiresAt(r.getAuthorizationCodeExpiresAt())
                .build();
    }

    private RevokeLogResponse toRevokeLogResponse(TokenRevokeLog log) {
        String clientName = clientRepository.findByClientId(log.getClientId())
                .map(c -> c.getClientName())
                .orElse(log.getClientId());

        String revokeTypeDesc = switch (log.getRevokeType()) {
            case 1 -> "用户主动登出";
            case 2 -> "后台强制下线";
            case 3 -> "IAM凭证失效";
            case 4 -> "密钥轮转";
            default -> "未知类型";
        };

        return RevokeLogResponse.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .clientId(log.getClientId())
                .clientName(clientName)
                .tokenType(log.getTokenType())
                .tokenSnip(log.getTokenSnip())
                .revokeType(log.getRevokeType())
                .revokeTypeDesc(revokeTypeDesc)
                .createTime(log.getCreateTime())
                .remark(log.getRemark())
                .build();
    }
}
