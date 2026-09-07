package com.liang.xz.system.service;

import com.liang.xz.system.dto.PageResponse;
import com.liang.xz.system.dto.SubsystemTokenPageQuery;
import com.liang.xz.system.dto.SubsystemTokenRequest;
import com.liang.xz.system.dto.SubsystemTokenResponse;
import com.liang.xz.system.dto.TokenRefreshRequest;
import com.liang.xz.system.entity.SubsystemToken;
import com.liang.xz.system.entity.TokenRevokeLog;
import com.liang.xz.system.repository.SubsystemTokenRepository;
import com.liang.xz.system.repository.TokenRevokeLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 子系统Token管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubsystemTokenService {

    private final SubsystemTokenRepository tokenRepository;
    private final TokenRevokeLogRepository revokeLogRepository;

    @Transactional
    public SubsystemTokenResponse recordToken(SubsystemTokenRequest request) {
        SubsystemToken token = SubsystemToken.builder()
                .clientId(request.getClientId())
                .userId(request.getUserId())
                .username(request.getUsername())
                .accessToken(request.getAccessToken())
                .refreshToken(request.getRefreshToken())
                .tokenType(request.getTokenType() != null ? request.getTokenType() : "JWT_BEARER")
                .accessTokenExpiresAt(request.getAccessTokenExpiresAt() != null
                        ? LocalDateTime.parse(request.getAccessTokenExpiresAt()) : null)
                .refreshTokenExpiresAt(request.getRefreshTokenExpiresAt() != null
                        ? LocalDateTime.parse(request.getRefreshTokenExpiresAt()) : null)
                .status("ACTIVE")
                .issuedIp(request.getIssuedIp())
                .userAgent(request.getUserAgent())
                .createTime(LocalDateTime.now())
                .refreshCount(0)
                .build();

        Long id = tokenRepository.insert(token);
        log.info("[SubsystemToken] 记录子系统Token: id={}, clientId={}, username={}",
                id, request.getClientId(), request.getUsername());

        token.setId(id);
        return toResponse(token);
    }

    @Transactional
    public SubsystemTokenResponse refreshToken(TokenRefreshRequest request) {
        SubsystemToken oldToken = tokenRepository.findByRefreshToken(request.getOldRefreshToken())
                .orElseThrow(() -> new IllegalArgumentException("RefreshToken无效或已过期"));

        if (!oldToken.getClientId().equals(request.getClientId())) {
            throw new IllegalArgumentException("客户端ID不匹配");
        }
        if (!oldToken.getUsername().equals(request.getUsername())) {
            throw new IllegalArgumentException("用户名不匹配");
        }

        tokenRepository.markAsRefreshed(oldToken.getId());

        int newRefreshCount = (oldToken.getRefreshCount() != null ? oldToken.getRefreshCount() : 0) + 1;
        tokenRepository.updateRefreshInfo(oldToken.getId(), LocalDateTime.now(), newRefreshCount);

        SubsystemToken newToken = SubsystemToken.builder()
                .clientId(request.getClientId())
                .userId(oldToken.getUserId())
                .username(request.getUsername())
                .accessToken(request.getNewAccessToken())
                .refreshToken(request.getNewRefreshToken())
                .tokenType(oldToken.getTokenType())
                .accessTokenExpiresAt(request.getAccessTokenExpiresAt() != null
                        ? LocalDateTime.parse(request.getAccessTokenExpiresAt()) : null)
                .refreshTokenExpiresAt(request.getRefreshTokenExpiresAt() != null
                        ? LocalDateTime.parse(request.getRefreshTokenExpiresAt()) : null)
                .status("ACTIVE")
                .parentTokenId(oldToken.getId())
                .issuedIp(request.getIssuedIp() != null ? request.getIssuedIp() : oldToken.getIssuedIp())
                .userAgent(oldToken.getUserAgent())
                .createTime(LocalDateTime.now())
                .refreshCount(0)
                .build();

        Long newId = tokenRepository.insert(newToken);
        log.info("[SubsystemToken] Token刷新: oldId={} → newId={}, username={}, refreshCount={}",
                oldToken.getId(), newId, request.getUsername(), newRefreshCount);

        newToken.setId(newId);
        return toResponse(newToken);
    }

    @Transactional
    public boolean revokeToken(Long tokenId, int revokeReason, String remark) {
        SubsystemToken token = tokenRepository.findById(tokenId).orElse(null);
        if (token == null) {
            return false;
        }

        int updated = tokenRepository.revoke(tokenId, revokeReason, remark);
        if (updated > 0) {
            writeRevokeLog(token, revokeReason, remark);
            log.info("[SubsystemToken] 吊销Token: id={}, clientId={}, username={}, reason={}",
                    tokenId, token.getClientId(), token.getUsername(), revokeReason);
            return true;
        }
        return false;
    }

    @Transactional
    public Map<String, Object> revokeAllActive(String clientId, String username,
            int revokeReason, String remark) {
        List<SubsystemToken> activeTokens = tokenRepository.findActiveByClientAndUser(clientId, username);
        int count = tokenRepository.revokeAllActiveByClientAndUser(
                clientId, username, revokeReason, remark);

        if (count > 0) {
            activeTokens.forEach(token -> writeRevokeLog(token, revokeReason, remark));
        }

        log.info("[SubsystemToken] 批量吊销: clientId={}, username={}, count={}, reason={}",
                clientId, username, count, revokeReason);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clientId", clientId);
        result.put("username", username);
        result.put("revokedCount", count);
        result.put("message", "成功吊销 " + count + " 个活跃Token");
        return result;
    }

    public SubsystemTokenResponse findById(Long id) {
        return tokenRepository.findById(id)
                .map(this::toResponse)
                .orElse(null);
    }

    public List<SubsystemTokenResponse> findActiveByClientAndUser(String clientId, String username) {
        return tokenRepository.findActiveByClientAndUser(clientId, username).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<SubsystemTokenResponse> findAllByClientAndUser(String clientId, String username,
            int limit, int offset) {
        return tokenRepository.findAllByClientAndUser(clientId, username, limit, offset).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 分页查询Token记录，支持多条件动态筛选
     */
    public PageResponse<SubsystemTokenResponse> pageList(SubsystemTokenPageQuery query) {
        long total = tokenRepository.countByFilter(query.getClientId(), query.getUsername(), query.getStatus(), query.getExpired());
        if (total == 0) {
            return PageResponse.empty(query.getPage(), query.getPageSize());
        }
        int offset = (query.getPage() - 1) * query.getPageSize();
        List<SubsystemTokenResponse> records = tokenRepository.findPageByFilter(
                query.getClientId(), query.getUsername(), query.getStatus(), query.getExpired(), offset, query.getPageSize()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return PageResponse.of(records, total, query.getPage(), query.getPageSize());
    }

    public List<SubsystemTokenResponse> findByClientId(String clientId, int limit, int offset) {
        return tokenRepository.findByClientId(clientId, limit, offset).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<SubsystemTokenResponse> findByUsername(String username, int limit, int offset) {
        return tokenRepository.findByUsername(username, limit, offset).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalTokens", tokenRepository.countAll());
        return stats;
    }

    private SubsystemTokenResponse toResponse(SubsystemToken entity) {
        boolean expired = entity.getAccessTokenExpiresAt() != null
                && entity.getAccessTokenExpiresAt().isBefore(LocalDateTime.now());

        String revokeReasonDesc = null;
        if (entity.getRevokeReason() != null) {
            revokeReasonDesc = switch (entity.getRevokeReason()) {
                case 1 -> "用户登出";
                case 2 -> "后台强制下线";
                case 3 -> "Token过期";
                case 4 -> "密钥轮转";
                default -> "未知原因";
            };
        }

        return SubsystemTokenResponse.builder()
                .id(entity.getId())
                .clientId(entity.getClientId())
                .userId(entity.getUserId())
                .username(entity.getUsername())
                .accessTokenSnip(snipToken(entity.getAccessToken()))
                .refreshTokenSnip(snipToken(entity.getRefreshToken()))
                .tokenType(entity.getTokenType())
                .accessTokenExpiresAt(entity.getAccessTokenExpiresAt())
                .refreshTokenExpiresAt(entity.getRefreshTokenExpiresAt())
                .status(entity.getStatus())
                .expired(expired)
                .parentTokenId(entity.getParentTokenId())
                .issuedIp(entity.getIssuedIp())
                .createTime(entity.getCreateTime())
                .lastRefreshTime(entity.getLastRefreshTime())
                .refreshCount(entity.getRefreshCount())
                .revokeTime(entity.getRevokeTime())
                .revokeReasonDesc(revokeReasonDesc)
                .build();
    }

    private String snipToken(String token) {
        if (token == null) return null;
        if (token.length() <= 12) return token;
        return token.substring(0, 8) + "***" + token.substring(token.length() - 4);
    }

    private void writeRevokeLog(SubsystemToken token, int revokeType, String remark) {
        TokenRevokeLog log = TokenRevokeLog.builder()
                .userId(token.getUsername())
                .clientId(token.getClientId())
                .tokenType(token.getTokenType() != null ? token.getTokenType() : "ACCESS_TOKEN")
                .tokenSnip(snipToken(token.getAccessToken()))
                .revokeType(revokeType)
                .remark(remark)
                .build();
        revokeLogRepository.insert(log);
    }
}
