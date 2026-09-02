package com.liang.xz.system.service;

import com.liang.xz.system.dto.SubsystemTokenRequest;
import com.liang.xz.system.dto.SubsystemTokenResponse;
import com.liang.xz.system.dto.TokenRefreshRequest;
import com.liang.xz.system.entity.SubsystemToken;
import com.liang.xz.system.repository.SubsystemTokenRepository;
import com.liang.xz.system.repository.TokenRevokeLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * <p>子系统Token管理服务单元测试</p>
 *
 * <p>测试覆盖:</p>
 * <ul>
 *   <li>Token记录</li>
 *   <li>Token刷新(正常/无效Token/客户端不匹配)</li>
 *   <li>Token吊销(单个/批量)</li>
 *   <li>Token查询(按ID/按客户端+用户)</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class SubsystemTokenServiceTest {

    @Mock
    private SubsystemTokenRepository tokenRepository;

    @Mock
    private TokenRevokeLogRepository revokeLogRepository;

    @InjectMocks
    private SubsystemTokenService tokenService;

    private SubsystemToken mockToken;
    private final Long TOKEN_ID = 1L;
    private final String CLIENT_ID = "test-client";
    private final String USERNAME = "admin";

    @BeforeEach
    void setUp() {
        mockToken = SubsystemToken.builder()
                .id(TOKEN_ID)
                .clientId(CLIENT_ID)
                .userId(1L)
                .username(USERNAME)
                .accessToken("eyJhbGciOiJIUzI1NiJ9.mock_access_token_1234567890")
                .refreshToken("mock-refresh-token-abcdefgh12345678")
                .tokenType("JWT_BEARER")
                .accessTokenExpiresAt(LocalDateTime.now().plusHours(2))
                .refreshTokenExpiresAt(LocalDateTime.now().plusDays(7))
                .status("ACTIVE")
                .issuedIp("127.0.0.1")
                .createTime(LocalDateTime.now())
                .refreshCount(0)
                .build();
    }

    // ======================== Token记录测试 ========================

    @Test
    @DisplayName("UT-01: 记录子系统Token成功")
    void testRecordToken() {
        SubsystemTokenRequest request = new SubsystemTokenRequest();
        request.setClientId(CLIENT_ID);
        request.setUserId(1L);
        request.setUsername(USERNAME);
        request.setAccessToken("eyJhbGciOiJIUzI1NiJ9.test_access_token");
        request.setRefreshToken("test-refresh-token");
        request.setTokenType("JWT_BEARER");
        request.setAccessTokenExpiresAt(LocalDateTime.now().plusHours(2).toString());
        request.setRefreshTokenExpiresAt(LocalDateTime.now().plusDays(7).toString());
        request.setIssuedIp("127.0.0.1");
        request.setUserAgent("Test-Agent/1.0");

        when(tokenRepository.insert(any(SubsystemToken.class))).thenReturn(TOKEN_ID);

        SubsystemTokenResponse response = tokenService.recordToken(request);

        assertNotNull(response);
        assertEquals(TOKEN_ID, response.getId());
        assertEquals(CLIENT_ID, response.getClientId());
        assertEquals(USERNAME, response.getUsername());
        assertEquals("ACTIVE", response.getStatus());
        assertFalse(response.getExpired());
        assertNotNull(response.getAccessTokenSnip());
        assertTrue(response.getAccessTokenSnip().contains("***"));

        verify(tokenRepository, times(1)).insert(any(SubsystemToken.class));
    }

    @Test
    @DisplayName("UT-02: 记录子系统Token(无RefreshToken)")
    void testRecordTokenWithoutRefresh() {
        SubsystemTokenRequest request = new SubsystemTokenRequest();
        request.setClientId(CLIENT_ID);
        request.setUsername(USERNAME);
        request.setAccessToken("access-token-only");

        when(tokenRepository.insert(any(SubsystemToken.class))).thenReturn(2L);

        SubsystemTokenResponse response = tokenService.recordToken(request);

        assertNotNull(response);
        assertEquals(2L, response.getId());
        assertNull(response.getRefreshTokenSnip());
    }

    // ======================== Token刷新测试 ========================

    @Test
    @DisplayName("UT-03: 刷新Token成功")
    void testRefreshTokenSuccess() {
        TokenRefreshRequest request = new TokenRefreshRequest();
        request.setClientId(CLIENT_ID);
        request.setUsername(USERNAME);
        request.setOldRefreshToken("mock-refresh-token-abcdefgh12345678");
        request.setNewAccessToken("new-access-token");
        request.setNewRefreshToken("new-refresh-token");
        request.setAccessTokenExpiresAt(LocalDateTime.now().plusHours(2).toString());
        request.setRefreshTokenExpiresAt(LocalDateTime.now().plusDays(7).toString());
        request.setIssuedIp("127.0.0.1");

        when(tokenRepository.findByRefreshToken(request.getOldRefreshToken()))
                .thenReturn(Optional.of(mockToken));
        when(tokenRepository.markAsRefreshed(TOKEN_ID)).thenReturn(1);
        when(tokenRepository.updateRefreshInfo(eq(TOKEN_ID), any(LocalDateTime.class), eq(1)))
                .thenReturn(1);
        when(tokenRepository.insert(any(SubsystemToken.class))).thenReturn(3L);

        SubsystemTokenResponse response = tokenService.refreshToken(request);

        assertNotNull(response);
        assertEquals(3L, response.getId());
        assertEquals(CLIENT_ID, response.getClientId());
        assertEquals("ACTIVE", response.getStatus());

        verify(tokenRepository, times(1)).findByRefreshToken(request.getOldRefreshToken());
        verify(tokenRepository, times(1)).markAsRefreshed(TOKEN_ID);
        verify(tokenRepository, times(1)).insert(any(SubsystemToken.class));
    }

    @Test
    @DisplayName("UT-04: 刷新Token - RefreshToken无效")
    void testRefreshTokenInvalid() {
        TokenRefreshRequest request = new TokenRefreshRequest();
        request.setClientId(CLIENT_ID);
        request.setUsername(USERNAME);
        request.setOldRefreshToken("invalid-refresh-token");
        request.setNewAccessToken("new-access-token");
        request.setNewRefreshToken("new-refresh-token");

        when(tokenRepository.findByRefreshToken(request.getOldRefreshToken()))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> tokenService.refreshToken(request));
        assertTrue(exception.getMessage().contains("RefreshToken无效"));

        verify(tokenRepository, never()).insert(any(SubsystemToken.class));
    }

    @Test
    @DisplayName("UT-05: 刷新Token - 客户端ID不匹配")
    void testRefreshTokenClientMismatch() {
        TokenRefreshRequest request = new TokenRefreshRequest();
        request.setClientId("other-client");
        request.setUsername(USERNAME);
        request.setOldRefreshToken("mock-refresh-token-abcdefgh12345678");
        request.setNewAccessToken("new-access-token");
        request.setNewRefreshToken("new-refresh-token");

        when(tokenRepository.findByRefreshToken(request.getOldRefreshToken()))
                .thenReturn(Optional.of(mockToken));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> tokenService.refreshToken(request));
        assertTrue(exception.getMessage().contains("客户端ID不匹配"));
    }

    @Test
    @DisplayName("UT-06: 刷新Token - 用户名不匹配")
    void testRefreshTokenUserMismatch() {
        TokenRefreshRequest request = new TokenRefreshRequest();
        request.setClientId(CLIENT_ID);
        request.setUsername("other-user");
        request.setOldRefreshToken("mock-refresh-token-abcdefgh12345678");
        request.setNewAccessToken("new-access-token");
        request.setNewRefreshToken("new-refresh-token");

        when(tokenRepository.findByRefreshToken(request.getOldRefreshToken()))
                .thenReturn(Optional.of(mockToken));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> tokenService.refreshToken(request));
        assertTrue(exception.getMessage().contains("用户名不匹配"));
    }

    // ======================== Token吊销测试 ========================

    @Test
    @DisplayName("UT-07: 吊销单个Token成功")
    void testRevokeToken() {
        when(tokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(mockToken));
        when(tokenRepository.revoke(eq(TOKEN_ID), eq(1), eq("用户登出")))
                .thenReturn(1);
        when(revokeLogRepository.insert(any())).thenReturn(1L);

        boolean result = tokenService.revokeToken(TOKEN_ID, 1, "用户登出");
        assertTrue(result);
        verify(tokenRepository, times(1)).findById(TOKEN_ID);
        verify(tokenRepository, times(1)).revoke(TOKEN_ID, 1, "用户登出");
        verify(revokeLogRepository, times(1)).insert(any());
    }

    @Test
    @DisplayName("UT-08: 吊销不存在的Token")
    void testRevokeNonExistentToken() {
        when(tokenRepository.findById(999L)).thenReturn(Optional.empty());

        boolean result = tokenService.revokeToken(999L, 1, "不存在");
        assertFalse(result);
        verify(tokenRepository, never()).revoke(anyLong(), anyInt(), anyString());
        verify(revokeLogRepository, never()).insert(any());
    }

    @Test
    @DisplayName("UT-09: 批量吊销Token")
    void testBatchRevoke() {
        List<SubsystemToken> activeTokens = List.of(
                SubsystemToken.builder().id(1L).clientId(CLIENT_ID).username(USERNAME)
                        .accessToken("token-1").tokenType("JWT_BEARER").build(),
                SubsystemToken.builder().id(2L).clientId(CLIENT_ID).username(USERNAME)
                        .accessToken("token-2").tokenType("JWT_BEARER").build(),
                SubsystemToken.builder().id(3L).clientId(CLIENT_ID).username(USERNAME)
                        .accessToken("token-3").tokenType("JWT_BEARER").build());

        when(tokenRepository.findActiveByClientAndUser(CLIENT_ID, USERNAME))
                .thenReturn(activeTokens);
        when(tokenRepository.revokeAllActiveByClientAndUser(CLIENT_ID, USERNAME, 2, "强制下线"))
                .thenReturn(3);
        when(revokeLogRepository.insert(any())).thenReturn(1L);

        Map<String, Object> result = tokenService.revokeAllActive(CLIENT_ID, USERNAME, 2, "强制下线");

        assertEquals(CLIENT_ID, result.get("clientId"));
        assertEquals(USERNAME, result.get("username"));
        assertEquals(3, result.get("revokedCount"));
        assertTrue(result.get("message").toString().contains("3"));
        verify(revokeLogRepository, times(3)).insert(any());
    }

    // ======================== Token查询测试 ========================

    @Test
    @DisplayName("UT-10: 按ID查询Token")
    void testFindById() {
        when(tokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(mockToken));

        SubsystemTokenResponse response = tokenService.findById(TOKEN_ID);

        assertNotNull(response);
        assertEquals(TOKEN_ID, response.getId());
        assertEquals(CLIENT_ID, response.getClientId());
        assertEquals(USERNAME, response.getUsername());
        assertEquals("ACTIVE", response.getStatus());
    }

    @Test
    @DisplayName("UT-11: 按ID查询不存在的Token")
    void testFindByIdNotFound() {
        when(tokenRepository.findById(999L)).thenReturn(Optional.empty());

        SubsystemTokenResponse response = tokenService.findById(999L);
        assertNull(response);
    }

    @Test
    @DisplayName("UT-12: 查询活跃Token")
    void testFindActiveTokens() {
        when(tokenRepository.findActiveByClientAndUser(CLIENT_ID, USERNAME))
                .thenReturn(List.of(mockToken));

        List<SubsystemTokenResponse> responses = tokenService.findActiveByClientAndUser(CLIENT_ID, USERNAME);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(TOKEN_ID, responses.get(0).getId());
        assertEquals("ACTIVE", responses.get(0).getStatus());
    }

    @Test
    @DisplayName("UT-13: 查询活跃Token(空列表)")
    void testFindActiveTokensEmpty() {
        when(tokenRepository.findActiveByClientAndUser(CLIENT_ID, USERNAME))
                .thenReturn(List.of());

        List<SubsystemTokenResponse> responses = tokenService.findActiveByClientAndUser(CLIENT_ID, USERNAME);

        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }

    // ======================== Token脱敏测试 ========================

    @Test
    @DisplayName("UT-14: Token脱敏 - 正常长度Token")
    void testTokenSnipNormal() {
        when(tokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(mockToken));

        SubsystemTokenResponse response = tokenService.findById(TOKEN_ID);

        assertNotNull(response.getAccessTokenSnip());
        assertTrue(response.getAccessTokenSnip().contains("***"));
        String snip = response.getAccessTokenSnip();
        assertEquals(8 + 3 + 4, snip.length());
    }

    @Test
    @DisplayName("UT-15: Token脱敏 - 短Token")
    void testTokenSnipShort() {
        SubsystemToken shortToken = SubsystemToken.builder()
                .id(2L)
                .clientId(CLIENT_ID)
                .username(USERNAME)
                .accessToken("short")
                .status("ACTIVE")
                .createTime(LocalDateTime.now())
                .build();

        when(tokenRepository.findById(2L)).thenReturn(Optional.of(shortToken));

        SubsystemTokenResponse response = tokenService.findById(2L);
        assertEquals("short", response.getAccessTokenSnip());
    }

    // ======================== 过期判断测试 ========================

    @Test
    @DisplayName("UT-16: Token已过期判断")
    void testTokenExpired() {
        SubsystemToken expiredToken = SubsystemToken.builder()
                .id(3L)
                .clientId(CLIENT_ID)
                .username(USERNAME)
                .accessToken("expired-token-test-1234567890")
                .accessTokenExpiresAt(LocalDateTime.now().minusHours(1))
                .status("ACTIVE")
                .createTime(LocalDateTime.now())
                .build();

        when(tokenRepository.findById(3L)).thenReturn(Optional.of(expiredToken));

        SubsystemTokenResponse response = tokenService.findById(3L);
        assertTrue(response.getExpired(), "已过期的Token expired应为true");
    }

    @Test
    @DisplayName("UT-17: Token未过期判断")
    void testTokenNotExpired() {
        when(tokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(mockToken));

        SubsystemTokenResponse response = tokenService.findById(TOKEN_ID);
        assertFalse(response.getExpired(), "未过期的Token expired应为false");
    }

    // ======================== 统计测试 ========================

    @Test
    @DisplayName("UT-18: Token统计")
    void testStats() {
        when(tokenRepository.countAll()).thenReturn(42);

        Map<String, Object> stats = tokenService.getStats();
        assertEquals(42, stats.get("totalTokens"));
    }
}
