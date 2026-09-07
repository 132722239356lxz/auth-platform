package com.liang.xz.server.service;

import com.liang.xz.common.core.token.CustomTokenClaims;
import com.liang.xz.server.dto.LoginResponse;
import com.liang.xz.server.entity.UserEntity;
import com.liang.xz.server.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.security.Principal;
import java.time.Instant;
import java.util.*;

/**
 * <p>Token 签发服务 —— 前后分离架构下的 REST 登录核心</p>
 *
 * <p>职责:</p>
 * <ol>
 *   <li>校验用户名密码(BCrypt)</li>
 *   <li>检查账号状态(启用/未锁定/未过期)</li>
 *   <li>查找对应的 OAuth2 客户端</li>
 *   <li>调用 Spring Authorization Server 的 TokenGenerator 签发 JWT</li>
 *   <li>将授权记录写入 oauth2_authorization 表(审计追踪)</li>
 *   <li>更新用户最后登录IP和时间</li>
 *   <li>返回 access_token + refresh_token + 用户信息</li>
 * </ol>
 *
 * <p>Token 增强:</p>
 * <ul>
 *   <li>CustomTokenEnhancer 会自动在 JWT 中添加 tenant_id / user_id / nickname 等身份信息</li>
 *   <li>支持 RS256 或 HS256 签名算法(由 JwkConfig 控制)</li>
 * </ul>
 *
 * <p>安全措施:</p>
 * <ul>
 *   <li>密码 BCrypt 匹配，不以明文或可逆方式存储</li>
 *   <li>账号状态检查(启用/锁定/过期)</li>
 *   <li>登录成功记录 IP</li>
 *   <li>全部操作在同一事务中完成</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenIssuerService {

    /** 默认客户端ID(前端管理后台) */
    private static final String DEFAULT_CLIENT_ID = "admin-web";

    /** 自定义 grant_type 标识(REST直接登录) */
    private static final String GRANT_TYPE_PASSWORD = "password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RegisteredClientRepository clientRepository;
    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenGenerator<?> tokenGenerator;
    private final JdbcTemplate jdbcTemplate;
    private final AuthorizationServerSettings authorizationServerSettings;

    /**
     * 手机号+密码登录 —— 校验密码 → 签发Token → 写入授权记录
     *
     * @param phone    手机号
     * @param password 密码(明文)
     * @param clientId 客户端ID(默认 admin-web)
     * @param request  HTTP请求(获取客户端IP)
     * @return LoginResponse 包含 access_token + refresh_token + 用户信息
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse loginByPhone(String phone, String password, String clientId, HttpServletRequest request) {
        // ========== 1. 校验用户凭证 ==========
        UserEntity user = validateUserByPhone(phone, password);

        // ========== 1.5 后台管理系统入口拦截：通过角色关联判断，仅ROLE_USER角色不可登录 ==========
        if (DEFAULT_CLIENT_ID.equals(clientId) && isRegularUser(user.getId())) {
            log.warn("[TokenIssuer] 普通用户尝试登录后台: phone={}", phone);
            throw new AccessDeniedException("普通用户无法登录后台管理系统，请使用用户门户");
        }

        // ========== 2. 查找客户端 ==========
        RegisteredClient client = resolveClient(clientId);

        // ========== 3. 构建授权并生成Token ==========
        OAuth2Authorization authorization = buildAuthorization(user, client, request);
        authorizationService.save(authorization);

        // ========== 4. 提取Token值 ==========
        OAuth2AccessToken accessToken = authorization.getAccessToken().getToken();
        OAuth2RefreshToken refreshToken = authorization.getRefreshToken() != null
                ? authorization.getRefreshToken().getToken() : null;

        // ========== 5. 更新用户登录信息 ==========
        String ip = getClientIp(request);
        userRepository.updateLoginInfoByPhone(phone, ip);

        // ========== 6. 查询权限和角色编码 ==========
        List<String> permissions = resolveUserPermissions(user.getId());
        List<String> roleCodes = resolveUserRoleCodes(user.getId());

        // ========== 7. 构建响应 ==========
        return buildResponse(user, authorization, accessToken, refreshToken, permissions, roleCodes, clientId);
    }

    /**
     * 用户名+密码登录 —— 校验密码 → 签发Token → 写入授权记录
     *
     * <p>与 loginByPhone 逻辑相同，仅查询用户的方式不同（按 username 查询）。</p>
     *
     * @param username 用户名
     * @param password 密码(明文)
     * @param clientId 客户端ID(默认 admin-web)
     * @param request  HTTP请求(获取客户端IP)
     * @return LoginResponse
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse loginByUsername(String username, String password, String clientId, HttpServletRequest request) {
        // ========== 1. 校验用户凭证（按用户名） ==========
        UserEntity user = validateUserByUsername(username, password);

        // ========== 1.5 后台管理系统入口拦截 ==========
        if (DEFAULT_CLIENT_ID.equals(clientId) && isRegularUser(user.getId())) {
            log.warn("[TokenIssuer] 普通用户尝试登录后台: username={}", username);
            throw new AccessDeniedException("普通用户无法登录后台管理系统，请使用用户门户");
        }

        // ========== 2. 查找客户端 ==========
        RegisteredClient client = resolveClient(clientId);

        // ========== 3. 构建授权并生成Token ==========
        OAuth2Authorization authorization = buildAuthorization(user, client, request);
        authorizationService.save(authorization);

        // ========== 4. 提取Token值 ==========
        OAuth2AccessToken accessToken = authorization.getAccessToken().getToken();
        OAuth2RefreshToken refreshToken = authorization.getRefreshToken() != null
                ? authorization.getRefreshToken().getToken() : null;

        // ========== 5. 更新用户登录信息 ==========
        String ip = getClientIp(request);
        userRepository.updateLoginInfoByUsername(username, ip);

        // ========== 6. 查询权限和角色编码 ==========
        List<String> permissions = resolveUserPermissions(user.getId());
        List<String> roleCodes = resolveUserRoleCodes(user.getId());

        log.info("[TokenIssuer] 用户名登录成功: username={}, userId={}", username, user.getId());
        return buildResponse(user, authorization, accessToken, refreshToken, permissions, roleCodes, clientId);
    }

    /**
     * 短信验证码登录 —— 校验验证码 → 签发Token → 写入授权记录
     *
     * <p>验证码由上层(AuthController)校验后调用，本方法仅负责签发 Token。</p>
     *
     * @param phone    手机号
     * @param clientId 客户端ID
     * @param request  HTTP请求
     * @return LoginResponse
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse loginBySmsCode(String phone, String clientId, HttpServletRequest request) {
        // ========== 1. 按手机号查找用户 ==========
        UserEntity user = userRepository.findByPhoneIncludeDisabled(phone)
                .orElseThrow(() -> {
                    log.warn("[TokenIssuer] 短信登录失败: 手机号未注册 phone={}", phone);
                    return new BadCredentialsException("该手机号未注册");
                });

        // ========== 2. 账号状态检查 ==========
        checkAccountStatus(user, phone);

        // ========== 3. 后台管理入口拦截 ==========
        if (DEFAULT_CLIENT_ID.equals(clientId) && isRegularUser(user.getId())) {
            log.warn("[TokenIssuer] 普通用户尝试短信登录后台: phone={}", phone);
            throw new AccessDeniedException("普通用户无法登录后台管理系统，请使用用户门户");
        }

        // ========== 4. 查找客户端 ==========
        RegisteredClient client = resolveClient(clientId);

        // ========== 5. 构建授权并生成Token ==========
        OAuth2Authorization authorization = buildAuthorization(user, client, request);
        authorizationService.save(authorization);

        // ========== 6. 提取Token值 ==========
        OAuth2AccessToken accessToken = authorization.getAccessToken().getToken();
        OAuth2RefreshToken refreshToken = authorization.getRefreshToken() != null
                ? authorization.getRefreshToken().getToken() : null;

        // ========== 7. 更新用户登录信息 ==========
        String ip = getClientIp(request);
        userRepository.updateLoginInfoByPhone(phone, ip);

        // ========== 8. 查询权限和角色编码 ==========
        List<String> permissions = resolveUserPermissions(user.getId());
        List<String> roleCodes = resolveUserRoleCodes(user.getId());

        log.info("[TokenIssuer] 短信验证码登录成功: phone={}, userId={}", phone, user.getId());
        return buildResponse(user, authorization, accessToken, refreshToken, permissions, roleCodes, clientId);
    }

    /**
     * @deprecated 使用 {@link #loginByPhone(String, String, String, HttpServletRequest)} 替代
     */
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(String username, String password, String clientId, HttpServletRequest request) {
        // 兼容旧调用：将 username 当作 phone 处理
        return loginByPhone(username, password, clientId, request);
    }

    /**
     * 用 refresh_token 换取新的 access_token / refresh_token。
     *
     * <p>流程: 根据 refresh_token 反查授权记录 → 校验用户状态 → 重新签发 Token → 作废旧授权。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse refresh(String refreshTokenValue, HttpServletRequest request) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new IllegalArgumentException("refresh_token不能为空");
        }
        OAuth2Authorization oldAuthorization = authorizationService.findByToken(
                refreshTokenValue, OAuth2TokenType.REFRESH_TOKEN);
        if (oldAuthorization == null) {
            throw new IllegalArgumentException("refresh_token无效或已过期，请重新登录");
        }

        String principalName = oldAuthorization.getPrincipalName();
        // 优先按 phone 查，兼容旧的 username 记录
        UserEntity user = userRepository.findByPhoneIncludeDisabled(principalName)
                .orElseGet(() -> userRepository.findByUsername(principalName)
                        .orElseThrow(() -> new IllegalArgumentException("用户不存在")));
        if (user.getEnabled() != null && !user.getEnabled()) {
            throw new DisabledException("账号已被禁用，请联系管理员");
        }
        if (user.getAccountNonLocked() != null && !user.getAccountNonLocked()) {
            throw new LockedException("账号已被锁定，请联系管理员");
        }

        RegisteredClient client = clientRepository.findByClientId(
                oldAuthorization.getRegisteredClientId());
        if (client == null) {
            throw new IllegalArgumentException("客户端不存在: " + oldAuthorization.getRegisteredClientId());
        }

        // 重新签发授权记录
        OAuth2Authorization newAuthorization = buildAuthorization(user, client, request);
        authorizationService.save(newAuthorization);

        // 作废旧的 refresh_token，防止重复使用
        try {
            authorizationService.remove(oldAuthorization);
        } catch (Exception e) {
            log.warn("[TokenIssuer] 旧授权记录删除失败(可忽略): {}", e.getMessage());
        }

        OAuth2AccessToken accessToken = newAuthorization.getAccessToken().getToken();
        OAuth2RefreshToken refreshToken = newAuthorization.getRefreshToken() != null
                ? newAuthorization.getRefreshToken().getToken() : null;

        List<String> permissions = resolveUserPermissions(user.getId());
        List<String> roleCodes = resolveUserRoleCodes(user.getId());

        log.info("[TokenIssuer] Token刷新成功: phone={}, userId={}", user.getPhone(), user.getId());
        return buildResponse(user, newAuthorization, accessToken, refreshToken, permissions, roleCodes, client.getClientId());
    }

    /**
     * 构建登录/刷新响应对象。
     */
    private LoginResponse buildResponse(UserEntity user, OAuth2Authorization authorization,
            OAuth2AccessToken accessToken, OAuth2RefreshToken refreshToken,
            List<String> permissions, List<String> roleCodes, String clientId) {
        long expiresIn = accessToken.getExpiresAt() != null
                ? accessToken.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond()
                : 3600L;

        log.info("[TokenIssuer] 签发成功: phone={}, client={}, userId={}, expiresIn={}s",
                user.getPhone(), clientId, user.getId(), expiresIn);

        return LoginResponse.builder()
                .idToken(Objects.requireNonNull(authorization.getToken(OidcIdToken.class)).getToken().getTokenValue())
                .accessToken(accessToken.getTokenValue())
                .refreshToken(refreshToken != null ? refreshToken.getTokenValue() : null)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .expiresAt(accessToken.getExpiresAt() != null
                        ? accessToken.getExpiresAt().toEpochMilli() : null)
                .userInfo(LoginResponse.UserInfo.builder()
                        .userId(String.valueOf(user.getId()))
                        .username(user.getUsername())
                        .nickname(user.getNickname())
                        .userType(user.getUserType())
                        .tenantId(user.getTenantId())
                        .email(user.getEmail())
                        .phone(user.getPhone())
                        .build())
                .permissions(permissions)
                .roles(roleCodes)
                .build();
    }

    // ======================== 私有方法 ========================

    /**
     * 按手机号校验用户凭证和状态
     */
    private UserEntity validateUserByPhone(String phone, String password) {
        UserEntity user = userRepository.findByPhoneIncludeDisabled(phone)
                .orElseThrow(() -> {
                    log.warn("[TokenIssuer] 登录失败: 手机号未注册 phone={}", phone);
                    return new BadCredentialsException("手机号或密码错误");
                });

        // BCrypt 密码匹配
        if (!passwordEncoder.matches(password, user.getPassword())) {
            log.warn("[TokenIssuer] 登录失败: 密码错误 phone={}", phone);
            throw new BadCredentialsException("手机号或密码错误或密钥更新,可刷新页面重试");
        }

        checkAccountStatus(user, phone);
        return user;
    }

    /**
     * 按用户名校验用户凭证和状态
     */
    private UserEntity validateUserByUsername(String username, String password) {
        UserEntity user = userRepository.findByUsernameIncludeDisabled(username)
                .orElseThrow(() -> {
                    log.warn("[TokenIssuer] 登录失败: 用户名不存在 username={}", username);
                    return new BadCredentialsException("用户名或密码错误");
                });

        if (!passwordEncoder.matches(password, user.getPassword())) {
            log.warn("[TokenIssuer] 登录失败: 密码错误 username={}", username);
            throw new BadCredentialsException("用户名或密码错误或密钥更新,可刷新页面重试");
        }

        checkAccountStatus(user, username);
        return user;
    }

    /**
     * 检查账号状态（启用/锁定/过期）
     */
    private void checkAccountStatus(UserEntity user, String phone) {
        if (user.getEnabled() != null && !user.getEnabled()) {
            throw new DisabledException("账号已被禁用，请联系管理员");
        }
        if (user.getAccountNonLocked() != null && !user.getAccountNonLocked()) {
            throw new LockedException("账号已被锁定，请联系管理员");
        }
        if (user.getAccountNonExpired() != null && !user.getAccountNonExpired()) {
            throw new BadCredentialsException("账号已过期，请联系管理员");
        }
        if (user.getCredentialsNonExpired() != null && !user.getCredentialsNonExpired()) {
            throw new BadCredentialsException("密码已过期，请修改密码");
        }
    }

    /**
     * @deprecated 使用 {@link #validateUserByPhone(String, String)} 替代
     */
    @Deprecated
    private UserEntity validateUser(String username, String password) {
        return validateUserByPhone(username, password);
    }

    /**
     * 解析客户端ID: 指定则用指定的，不存在则回退到默认 admin-web
     */
    private RegisteredClient resolveClient(String clientId) {
        String effectiveClientId = (clientId != null && !clientId.isBlank())
                ? clientId : DEFAULT_CLIENT_ID;

        RegisteredClient client = clientRepository.findByClientId(effectiveClientId);
        if (client == null) {
            // 如果不是默认客户端，回退到默认客户端（兼容 portal-web 等非注册客户端）
            if (!DEFAULT_CLIENT_ID.equals(effectiveClientId)) {
                log.info("[TokenIssuer] 客户端 '{}' 未注册，回退到默认客户端 '{}'", effectiveClientId, DEFAULT_CLIENT_ID);
                client = clientRepository.findByClientId(DEFAULT_CLIENT_ID);
                if (client == null) {
                    throw new IllegalArgumentException("默认客户端不存在: " + DEFAULT_CLIENT_ID);
                }
                return client;
            }
            throw new IllegalArgumentException("客户端不存在: " + effectiveClientId);
        }
        return client;
    }

    /**
     * 构建 OAuth2Authorization 对象并生成 Token
     * <p>集成 Spring Authorization Server 的 TokenGenerator + CustomTokenEnhancer</p>
     */
    private OAuth2Authorization buildAuthorization(UserEntity user, RegisteredClient client,
                                                    HttpServletRequest request) {
        // 以 username 作为 principal 标识（JWT sub claim），
        // phone 只是联系方式属性，不应作为身份主键
        String principalName = user.getUsername();

        // === 构建用户认证对象 (SAS TokenGenerator 需要 Authentication 类型) ===
        Authentication principal = new UsernamePasswordAuthenticationToken(
                principalName, null, Collections.emptyList());

        AuthorizationGrantType grantType = new AuthorizationGrantType(GRANT_TYPE_PASSWORD);

        // === 构建 Authorization (暂不包含Token, SAS 要求先构建再追加) ===
        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(client)
                .principalName(principalName)
                .authorizationGrantType(grantType)
                .authorizedScopes(client.getScopes())
                .attribute(Principal.class.getName(), principal)
                .attribute(CustomTokenClaims.CLAIM_USER_TYPE, user.getUserType())
                .attribute(CustomTokenClaims.CLAIM_TENANT_ID,
                        user.getTenantId() != null ? user.getTenantId() : "default")
                .attribute(CustomTokenClaims.CLAIM_USER_ID, String.valueOf(user.getId()))
                .attribute(CustomTokenClaims.CLAIM_NICKNAME,
                        user.getNickname() != null ? user.getNickname() : user.getUsername());

        OAuth2Authorization authorization = builder.build();

        // === 手动设置 AuthorizationServerContext ===
        // 自定义 REST 登录不在标准 OAuth2 端点内执行，AuthorizationServerContextHolder 为空
        // 必须手动注入，否则 JwtGenerator 在生成 iss claim 时获取不到 issuer
        AuthorizationServerContext authServerContext = new AuthorizationServerContext() {
            @Override
            public String getIssuer() {
                return authorizationServerSettings.getIssuer();
            }

            @Override
            public AuthorizationServerSettings getAuthorizationServerSettings() {
                return authorizationServerSettings;
            }
        };
        AuthorizationServerContextHolder.setContext(authServerContext);

        try {
            // === 生成 AccessToken ===
            OAuth2TokenContext accessTokenContext = DefaultOAuth2TokenContext.builder()
                    .put("sub", principalName)
                    .registeredClient(client)
                    .principal(principal)
                    .authorization(authorization)
                    .authorizationServerContext(authServerContext)
                    .authorizedScopes(client.getScopes())
                    .tokenType(OAuth2TokenType.ACCESS_TOKEN)
                    .authorizationGrantType(grantType)
                    .authorizationGrant(principal)
                    .build();

            OAuth2Token generatedAccessToken = tokenGenerator.generate(accessTokenContext);
            if (generatedAccessToken == null) {
                throw new RuntimeException("AccessToken 生成失败");
            }

            OAuth2AccessToken accessToken = new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    generatedAccessToken.getTokenValue(),
                    generatedAccessToken.getIssuedAt(),
                    generatedAccessToken.getExpiresAt(),
                    accessTokenContext.getAuthorizedScopes());

            builder.accessToken(accessToken);

            // === 生成 RefreshToken ===
            if (client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
                OAuth2Authorization afterAccessToken = builder.build();

                OAuth2TokenContext refreshTokenContext = DefaultOAuth2TokenContext.builder()
                        .registeredClient(client)
                        .principal(principal)
                        .put("sub", principalName)
                        .authorization(afterAccessToken)
                        .authorizedScopes(client.getScopes())
                        .tokenType(OAuth2TokenType.REFRESH_TOKEN)
                        .authorizationGrantType(grantType)
                        .authorizationGrant(principal)
                        .build();

                OAuth2Token generatedRefreshToken = tokenGenerator.generate(refreshTokenContext);
                if (generatedRefreshToken != null) {
                    OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(
                            generatedRefreshToken.getTokenValue(),
                            generatedRefreshToken.getIssuedAt(),
                            generatedRefreshToken.getExpiresAt());
                    builder.token(refreshToken);
                }
            }

            // === 生成 OidcIdToken (供 OIDC /userinfo 端点使用) ===
            OidcIdToken oidcIdToken = generateAndAddOidcIdToken(builder, client, user, accessToken);
            builder.token(oidcIdToken);
        } finally {
            // 清理上下文，避免线程池复用时的数据污染
            AuthorizationServerContextHolder.resetContext();
        }

        return builder.build();
    }

    /**
     * 生成 OidcIdToken 并添加到授权构建器中。
     *
     * <p>id_token 是 OIDC 协议的核心组件，/userinfo 端点依赖它来返回用户身份信息。
     * 自定义 REST 登录不走标准 OAuth2 端点，需手动生成 id_token 并存入 oauth2_authorization 表。</p>
     *
     * <p>不复用 tokenGenerator 生成新 JWT，而是复用 access_token 的 tokenValue，
     * 避免生成独立签名的 JWT 导致 claims 与 JWT body 不一致的问题。</p>
     */
    private OidcIdToken generateAndAddOidcIdToken(OAuth2Authorization.Builder builder,
                                            RegisteredClient client,
                                            UserEntity user,
                                            OAuth2AccessToken accessToken) {
        Map<String, Object> claims = buildOidcIdTokenClaims(user, client,
                accessToken.getIssuedAt(), accessToken.getExpiresAt());

        OidcIdToken oidcIdToken = new OidcIdToken(
                accessToken.getTokenValue(),
                accessToken.getIssuedAt(),
                accessToken.getExpiresAt(),
                claims);
        log.debug("[TokenIssuer] OidcIdToken 生成成功: sub={}, claims={}",
                user.getUsername(), claims.keySet());
        return oidcIdToken;
    }

    /**
     * 构建 OIDC IdToken 标准 claims + 自定义业务 claims。
     */
    private Map<String, Object> buildOidcIdTokenClaims(UserEntity user, RegisteredClient client,
                                                        Instant issuedAt, Instant expiresAt) {
        Map<String, Object> claims = new LinkedHashMap<>();
        // ---- OIDC 标准 claims ----
        claims.put("iss", authorizationServerSettings.getIssuer());
        claims.put("sub", user.getUsername());
        claims.put("aud", Collections.singletonList(client.getClientId()));
        claims.put("iat", issuedAt);
        claims.put("exp", expiresAt);
        claims.put("auth_time", issuedAt);
        claims.put("azp", client.getClientId());

        // ---- 用户身份 claims ----
        claims.put(CustomTokenClaims.CLAIM_USER_ID, String.valueOf(user.getId()));
        claims.put(CustomTokenClaims.CLAIM_USER_TYPE, user.getUserType());
        claims.put(CustomTokenClaims.CLAIM_TENANT_ID,
                user.getTenantId() != null ? user.getTenantId() : "default");
        claims.put(CustomTokenClaims.CLAIM_NICKNAME,
                user.getNickname() != null ? user.getNickname() : user.getUsername());

        if (user.getPhone() != null && !user.getPhone().isEmpty()) {
            claims.put("phone_number", user.getPhone());
        }
        if (user.getEmail() != null && !user.getEmail().isEmpty()) {
            claims.put("email", user.getEmail());
        }

        return claims;
    }

    /**
     * 查询用户权限标识列表(用户→角色→菜单 链路)
     */
    private List<String> resolveUserPermissions(Long userId) {
        try {
            String sql = """
                    SELECT DISTINCT m.permission FROM sys_menu m
                    INNER JOIN sys_role_menu rm ON m.id = rm.menu_id
                    INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id
                    WHERE ur.user_id = ? AND m.enabled = 1
                    AND m.permission IS NOT NULL AND m.permission != ''
                    """;
            return jdbcTemplate.queryForList(sql, String.class, userId);
        } catch (Exception e) {
            log.warn("[TokenIssuer] 查询用户权限失败: userId={}, error={}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 查询用户角色编码列表(用户→角色 链路)。
     *
     * @param userId 用户ID
     * @return 角色编码集合，查询失败返回空列表
     */
    private List<String> resolveUserRoleCodes(Long userId) {
        try {
            String sql = """
                    SELECT r.role_code FROM sys_role r
                    INNER JOIN sys_user_role ur ON r.id = ur.role_id
                    WHERE ur.user_id = ?
                    """;
            return jdbcTemplate.queryForList(sql, String.class, userId);
        } catch (Exception e) {
            log.warn("[TokenIssuer] 查询用户角色失败: userId={}, error={}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 通过角色关联判断是否为普通用户。
     *
     * <p>查询 sys_user_role → sys_role 获取用户角色编码集合，
     * 如果用户未分配任何角色或所有角色编码均为 ROLE_USER 则视为普通用户，
     * 只要拥有任意一个非 ROLE_USER 的角色（如 ROLE_ADMIN）就不是普通用户。</p>
     *
     * @param userId 用户ID
     * @return true 表示是普通用户（仅拥有ROLE_USER角色），false 表示拥有管理角色
     */
    private boolean isRegularUser(Long userId) {
        List<String> roleCodes = resolveUserRoleCodes(userId);
        return roleCodes.isEmpty() || roleCodes.stream().allMatch("ROLE_USER"::equals);
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
