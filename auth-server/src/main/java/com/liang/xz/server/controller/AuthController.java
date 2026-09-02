package com.liang.xz.server.controller;

import com.liang.xz.server.config.CryptoConfig;
import com.liang.xz.server.dto.ApiResponse;
import com.liang.xz.server.dto.LoginRequest;
import com.liang.xz.server.dto.LoginResponse;
import com.liang.xz.server.dto.SmsSendRequest;
import com.liang.xz.server.dto.UserRegisterRequest;
import com.liang.xz.server.dto.UserResponse;
import com.liang.xz.server.entity.UserEntity;
import com.liang.xz.server.repository.UserRepository;
import com.liang.xz.server.service.SmsCodeService;
import com.liang.xz.server.service.TokenIssuerService;
import com.liang.xz.server.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * <p>认证控制器 —— REST登录 | SSO登录页面 | 用户注册</p>
 *
 * <p>两种登录方式并存:</p>
 * <pre>
 *   【方式一: REST API 登录】 (前后分离, SPA/移动端)
 *     POST /api/auth/login  {username, password}
 *     → 200 {access_token, refresh_token, expires_in, user_info}
 *
 *   【方式二: SSO 登录页面】 (传统服务端渲染, OAuth2授权码模式)
 *     用户访问第三方客户端 → 302 → /oauth2/authorize
 *     → 未登录 → 302 → /login (Thymeleaf模板)
 *     → 输入账号密码 → POST /login
 *     → 认证成功 → 重定向回 /oauth2/authorize
 *     → 用户确认授权 → 302 回调携带授权码 → 换Token
 * </pre>
 *
 * <p>Session 共享机制（SSO 免二次登录核心）:</p>
 * <ol>
 *   <li>REST 登录成功后 → createSessionAfterRestLogin() 写入 SecurityContext 到 HttpSession</li>
 *   <li>SAS Order(1) 链 → SecurityContextHolderFilter 从 HttpSession 读取 → 识别已认证用户</li>
 *   <li>跳转 /oauth2/authorize → SAS 直接签发授权码，无需重复登录</li>
 * </ol>
 *
 * <p>注意: 用户管理CRUD/角色分配/菜单权限已迁移到 system-server 模块</p>
 * <p>本模块仅负责: REST登录/SSO登录页面/用户自助注册</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@Tag(name = "认证中心", description = "REST登录、SSO登录页面、用户注册")
public class AuthController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final TokenIssuerService tokenIssuerService;
    private final SmsCodeService smsCodeService;
    private final com.liang.xz.server.service.LogoutService logoutService;
    private final AuthenticationManager authenticationManager;
    private final CryptoConfig cryptoConfig;

    @Value("${app.oauth2.portal-client-id:admin-web}")
    private String portalClientId;

    @Value("${app.oauth2.portal-client-secret:secret}")
    private String portalClientSecret;

    @Value("${app.oauth2.token-endpoint:http://127.0.0.1:8080/auth-server/oauth2/token}")
    private String tokenEndpoint;

    private final RestTemplate restTemplate = new RestTemplate();

    // ======================== REST API 登录 (前后分离) ========================

    /**
     * REST 登录接口 —— 支持手机号+密码 / 用户名+密码 / 手机号+短信验证码
     *
     * <p>认证成功后:</p>
     * <ol>
     *   <li>BCrypt 校验密码 或 校验短信验证码</li>
     *   <li>Spring Authorization Server 签发 JWT (RS256/HS256)</li>
     *   <li>授权记录写入 oauth2_authorization 表</li>
     *   <li>更新用户最后登录IP和时间</li>
     *   <li>★ 创建 HttpSession → SecurityContext 写入 Session → 供后续 OAuth2 授权码流程使用</li>
     *   <li>返回 access_token + refresh_token + 用户信息</li>
     * </ol>
     */
    @PostMapping("/api/auth/login")
    @ResponseBody
    @Operation(summary = "用户登录", description = "支持手机号+密码、用户名+密码、手机号+短信验证码三种方式登录")
    public ApiResponse<LoginResponse> restLogin(
            @Parameter(description = "登录请求") @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) {
        String loginType = loginRequest.getLoginType() != null
                ? loginRequest.getLoginType().toUpperCase() : "PASSWORD";
        String phone = loginRequest.getPhone();
        String username = loginRequest.getUsername();
        String principalName;   // 用于 Session 创建的 principal
        String rawPassword;     // 明文密码，用于 Session 创建时的 AuthenticationManager 认证
        try {
            LoginResponse response;

            if ("SMS".equals(loginType)) {
                if (phone == null || phone.isBlank()) {
                    return ApiResponse.fail(400, "短信验证码登录需要提供手机号");
                }
                String smsCode = loginRequest.getSmsCode();
                if (smsCode == null || smsCode.isBlank()) {
                    return ApiResponse.fail(400, "短信验证码不能为空");
                }
                if (!smsCodeService.verifyCode(phone, smsCode)) {
                    return ApiResponse.fail(401, "验证码错误或已过期，请重新获取");
                }
                response = tokenIssuerService.loginBySmsCode(phone, loginRequest.getClientId(), request);
                principalName = phone;
                rawPassword = null; // 短信登录不走 Session 密码认证
            } else if (username != null && !username.isBlank()) {
                if (loginRequest.getPassword() == null || loginRequest.getPassword().isBlank()) {
                    return ApiResponse.fail(400, "密码不能为空");
                }
                rawPassword = decryptPassword(loginRequest.getPassword());
                response = tokenIssuerService.loginByUsername(
                        username, rawPassword, loginRequest.getClientId(), request);
                principalName = username;
            } else if (phone != null && !phone.isBlank()) {
                if (loginRequest.getPassword() == null || loginRequest.getPassword().isBlank()) {
                    return ApiResponse.fail(400, "密码不能为空");
                }
                rawPassword = decryptPassword(loginRequest.getPassword());
                response = tokenIssuerService.loginByPhone(
                        phone, rawPassword, loginRequest.getClientId(), request);
                principalName = phone;
            } else {
                return ApiResponse.fail(400, "请提供手机号或用户名");
            }

            // ★ REST 登录成功后创建 Session，确保后续 OAuth2 授权码流程可用
            createSessionAfterRestLogin(principalName, rawPassword, request);
            return ApiResponse.success("登录成功", response);
        } catch (org.springframework.security.access.AccessDeniedException e) {
            log.warn("[Auth] 普通用户拦截: {}", e.getMessage());
            return ApiResponse.fail(403, e.getMessage());
        } catch (org.springframework.security.authentication.BadCredentialsException e) {
            log.warn("[Auth] REST登录失败: {}", e.getMessage());
            return ApiResponse.fail(401, "账号或密码错误");
        } catch (org.springframework.security.authentication.DisabledException e) {
            return ApiResponse.fail(403, e.getMessage());
        } catch (org.springframework.security.authentication.LockedException e) {
            return ApiResponse.fail(423, e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("[Auth] REST登录异常: {}", e.getMessage(), e);
            return ApiResponse.fail(500, "登录失败，服务内部异常");
        }
    }

    /**
     * 发送短信验证码
     */
    @PostMapping("/api/auth/sms/send")
    @ResponseBody
    @Operation(summary = "发送短信验证码", description = "向指定手机号发送6位数字验证码，有效期5分钟")
    public ApiResponse<Void> sendSmsCode(
            @Parameter(description = "手机号") @Valid @RequestBody SmsSendRequest request) {
        String phone = request.getPhone();
        if (phone == null || phone.isBlank()) {
            return ApiResponse.fail(400, "手机号不能为空");
        }
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            return ApiResponse.fail(400, "手机号格式不正确");
        }
        try {
            smsCodeService.sendCode(phone);
            return ApiResponse.success("验证码已发送", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * Token 刷新接口
     */
    @PostMapping("/api/auth/refresh")
    @ResponseBody
    @Operation(summary = "刷新Token", description = "使用refresh_token换取新的access_token和refresh_token")
    public ApiResponse<LoginResponse> refreshToken(
            @Parameter(description = "refresh_token") @RequestBody java.util.Map<String, String> body,
            HttpServletRequest request) {
        String refreshToken = body != null ? body.get("refresh_token") : null;
        if (refreshToken == null || refreshToken.isBlank()) {
            return ApiResponse.fail(400, "refresh_token不能为空");
        }
        try {
            LoginResponse response = tokenIssuerService.refresh(refreshToken, request);
            return ApiResponse.success("刷新成功", response);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(401, e.getMessage());
        } catch (Exception e) {
            log.error("[Auth] Token刷新异常: {}", e.getMessage(), e);
            return ApiResponse.fail(500, "刷新失败，服务内部异常");
        }
    }

    // ======================== 退出登录（真正终止会话） ========================

    /**
     * 退出登录 —— 服务端吊销当前 Token，使其立即失效。
     *
     * <p><b>为什么需要这个端点：</b>JWT 是无状态凭证，仅清除前端 Token 并不能阻止旧 Token
     * 在有效期内继续调用接口。本端点将 Token 的 jti 写入 Redis 黑名单（TTL = 剩余有效期），
     * 同时作废旧授权记录使 refresh_token 失效；网关与各资源服务器校验黑名单后即刻拒绝该 Token。</p>
     *
     * <p>调用方式:</p>
     * <pre>
     *   POST /api/auth/logout
     *   Authorization: Bearer {access_token}
     * </pre>
     */
    @PostMapping("/api/auth/logout")
    @ResponseBody
    @Operation(summary = "退出登录", description = "吊销当前Token(加入黑名单)并作废refresh_token，退出后立即失效")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            return ApiResponse.fail(400, "缺少Authorization请求头");
        }
        try {
            logoutService.logout(authorization);
            // 同时销毁本地 HttpSession，保证 SSO 登录页不会凭旧 Session 免密续签
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            return ApiResponse.success("已退出登录", null);
        } catch (Exception e) {
            log.error("[Auth] 退出登录异常: {}", e.getMessage(), e);
            return ApiResponse.fail(500, "退出登录失败，服务内部异常");
        }
    }

    // ======================== 授权码换 Token（服务端代理，不暴露 client_secret） ========================

    /**
     * 授权码换 Token —— 服务端代理调用 /oauth2/token，避免前端暴露 client_secret。
     *
     * <p>OAuth2 授权码流程中，/oauth2/token 需要 client_id + client_secret 进行 Basic 认证，
     * 直接从前端调用会导致密钥泄露。此端点将密钥保存在服务端，由服务端代为调用 Token 端点。</p>
     *
     * <p>请求体:</p>
     * <pre>{@code
     *   { "code": "xyz", "state": "abc", "redirectUri": "http://127.0.0.1:5173/login/callback" }
     * }</pre>
     *
     * <p>调用链:</p>
     * <pre>
     *   前端 callback.vue → POST /api/auth/exchange-code
     *     → 本方法 → RestTemplate POST local /oauth2/token (Basic Auth: client_id:client_secret)
     *     → 返回 access_token / refresh_token / expires_in / scope → 前端
     * </pre>
     */
    @PostMapping("/api/auth/exchange-code")
    @ResponseBody
    @Operation(summary = "授权码换Token", description = "服务端代为调用 /oauth2/token，使用服务端持有的 client_secret，避免密钥泄露")
    public ApiResponse<Map<String, Object>> exchangeCode(
            @Parameter(description = "授权码信息") @RequestBody Map<String, String> body) {

        String code = body.get("code");
        String state = body.get("state");
        String redirectUri = body.get("redirectUri");

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String formBody;
            String grantType;

            if (code != null && !code.isBlank()) {
                // 有 code → 授权码模式：凭据放 body，不走 Basic Auth
                grantType = "authorization_code";
                formBody = "grant_type=" + grantType
                        + "&client_id=" + URLEncoder.encode(portalClientId, StandardCharsets.UTF_8)
                        + "&client_secret=" + URLEncoder.encode(portalClientSecret, StandardCharsets.UTF_8)
                        + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                        + "&redirect_uri=" + URLEncoder.encode(redirectUri != null ? redirectUri : "", StandardCharsets.UTF_8);
            } else {
                // 无 code → 客户端模式：走 Basic Auth
                grantType = "client_credentials";
                headers.setBasicAuth(portalClientId, portalClientSecret);
                formBody = "grant_type=" + grantType;
            }

            HttpEntity<String> request = new HttpEntity<>(formBody, headers);

            // 通过网关调用 OAuth2 Token 端点
            log.info("[exchangeCode] 请求 Token 端点: grantType={}, endpoint={}, code={}, state={}, redirectUri={}",
                    grantType, tokenEndpoint, code, state, redirectUri);

            ResponseEntity<Map> response = restTemplate.exchange(
                    tokenEndpoint, HttpMethod.POST, request, Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> tokenData = (Map<String, Object>) response.getBody();

            log.info("[exchangeCode] Token 获取成功: token_type={}, expires_in={}",
                    tokenData != null ? tokenData.get("token_type") : "unknown",
                    tokenData != null ? tokenData.get("expires_in") : "unknown");

            return ApiResponse.success("Token 获取成功", tokenData);

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("[exchangeCode] Token 端点返回错误: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return ApiResponse.fail(e.getStatusCode().value(),
                    "Token 换取失败: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("[exchangeCode] Token 换取异常: {}", e.getMessage(), e);
            return ApiResponse.fail(500, "Token 换取失败，服务内部异常: " + e.getMessage());
        }
    }

    // ======================== Session 状态检查（OAuth2 跳转前置判断） ========================

    /**
     * Session 初始化端点 —— 为已登录用户(JWT Bearer token)创建 HttpSession 并写入 SecurityContext。
     *
     * <p><b>用途：</b>门户首页点击子系统触发 SSO 跳转 /oauth2/authorize 之前调用。
     * 因为 {@code window.open} 是浏览器页面级导航，不会携带 Authorization: Bearer 头，
     * 但会携带同域 cookie。本端点将 JWT 认证信息写入 Session，后续由 Order(1) 链的
     * HttpSessionSecurityContextRepository 读取并识别已登录用户。</p>
     *
     * <p><b>路径在 /api/** 下，走 Order(2) STATELESS 链，</b>
     * BearerTokenAuthenticationFilter 已验证 JWT token 并将 Authentication 注入 SecurityContextHolder。</p>
     *
     * <p>前端使用:</p>
     * <pre>
     *   await axios.post('/auth-server/api/auth/init-session')  // Axios 自动带 Bearer token
     *   window.open('/auth-server/oauth2/authorize?...')        // 浏览器带 AUTH_SESSION cookie
     * </pre>
     */
    @PostMapping("/api/auth/init-session")
    @ResponseBody
    @Operation(summary = "初始化Session", description = "为已持有JWT Bearer token的用户创建HttpSession，供OAuth2授权码流程使用")
    public ApiResponse<java.util.Map<String, Object>> initSession(HttpServletRequest request) {
        // 1. 从 Order(2) STATELESS 链已注入的 SecurityContext 获取认证信息
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            log.warn("[initSession] 未认证或已过期: authentication={}", authentication);
            return ApiResponse.fail(401, "用户未认证，请重新登录");
        }

        // 2. 创建 HttpSession 并写入 SecurityContext（供 Order(1) 链使用）
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        HttpSession session = request.getSession(true);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        log.info("[initSession] Session 创建成功: sessionId={}, user={}",
                session.getId(), authentication.getName());
        return ApiResponse.success("Session 创建成功",
                java.util.Map.of("sessionId", session.getId(), "principal", authentication.getName()));
    }

    /**
     * Session 状态检查端点 —— 供前端在跳转 OAuth2 /oauth2/authorize 之前确认 session 是否有效。
     *
     * <p><b>注意：</b>此端点路径在 /api/** 下，走 Order(2) STATELESS 链，
     * 因此 Controller 手动从 HttpSession 读取认证信息而非依赖 SecurityContextHolder。</p>
     *
     * <p>前端使用:</p>
     * <pre>
     *   const res = await fetch('/auth-server/api/auth/session-status', { credentials: 'include' })
     *   if (res.data.valid) { window.location.href = '/auth-server/oauth2/authorize?...' }
     *   else { // session 已过期，需要重新登录 }
     * </pre>
     */
    @GetMapping("/api/auth/session-status")
    @ResponseBody
    @Operation(summary = "Session状态检查", description = "检查当前浏览器的Session中是否存在有效认证，用于OAuth2跳转前置判断")
    public ApiResponse<java.util.Map<String, Object>> sessionStatus(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            log.debug("[Auth] session-status: 无 Session");
            return ApiResponse.success("ok",
                    java.util.Map.of("valid", false, "principal", "", "message", "No session found"));
        }

        Object contextObj = session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        if (!(contextObj instanceof org.springframework.security.core.context.SecurityContext secCtx)) {
            log.debug("[Auth] session-status: Session 存在但无 SecurityContext");
            return ApiResponse.success("ok",
                    java.util.Map.of("valid", false, "principal", "", "message", "No SecurityContext in session"));
        }

        Authentication auth = secCtx.getAuthentication();
        boolean valid = auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal());
        String principal = valid ? auth.getName() : "";
        log.debug("[Auth] session-status: valid={}, principal={}, sessionId={}",
                valid, principal, session.getId());
        return ApiResponse.success("ok",
                java.util.Map.of("valid", valid, "principal", principal));
    }

    // ======================== OAuth2 流程续接 ========================

    /**
     * OAuth2 流程续接端点 —— REST 登录后回到此端点，由服务端决定下一步跳转
     *
     * <p><b>路径 /login/oauth2-continue 由 Order(1) 链处理（显式在 securityMatcher 中注册），
     * Order(1) 的 SecurityContextHolderFilter 通过 HttpSessionSecurityContextRepository 读取 Session 中的
     * SecurityContext，实现与 REST 登录 Session 的互认。</b></p>
     *
     * <p>设计意图:</p>
     * <ol>
     *   <li>若 Session 中存在已认证用户 + SAS 保存的 OAuth2 授权请求
     *       → 302 跳转到 /oauth2/authorize（SAS 识别已认证用户 → 签发授权码）</li>
     *   <li>若 Session 中有已认证用户但无 OAuth2 授权请求（普通登录）
     *       → 302 跳转到 frontendUrl/login/callback?login=success</li>
     * </ol>
     */
    @GetMapping("/login/oauth2-continue")
    public void continueOAuth2Flow(HttpServletRequest request, HttpServletResponse response,
                                    Authentication authentication) throws IOException {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            log.warn("[Auth] /login/oauth2-continue 未认证或 anonymousUser，重定向到登录页");
            response.sendRedirect("/login");
            return;
        }

        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        SavedRequest savedRequest = requestCache.getRequest(request, response);

        if (savedRequest != null) {
            String redirectUrl = savedRequest.getRedirectUrl();
            log.info("[Auth] OAuth2 流程续接: 已认证用户={}, 跳转到={}", authentication.getName(), redirectUrl);
            response.sendRedirect(redirectUrl);
        } else {
            log.info("[Auth] 普通登录完成, 用户={}, 跳转到登录回调", authentication.getName());
            response.sendRedirect("/login/callback?login=success");
        }
    }

    // ======================== SSO 登录页面 ========================

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    /**
     * 登录成功后回调，记录登录日志
     */
    @GetMapping("/login-success")
    @ResponseBody
    public ApiResponse<String> loginSuccess(HttpServletRequest request, Authentication authentication) {
        String ip = getClientIp(request);
        if (authentication != null && authentication.getName() != null) {
            userRepository.updateLoginInfoByPhone(authentication.getName(), ip);
            log.info("[Auth] 用户登录成功: phone={}, ip={}", authentication.getName(), ip);
        }
        return ApiResponse.success("登录成功", null);
    }

    // ======================== 用户注册(SSO入口) ========================

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/api/register")
    @ResponseBody
    @Operation(summary = "用户自助注册", description = "创建新用户账号，密码自动BCrypt编码存储，注册后可使用SSO登录")
    public ApiResponse<UserResponse> register(
            @Parameter(description = "注册信息") @Valid @RequestBody UserRegisterRequest request) {
        try {
            UserResponse user = userService.register(request);
            log.info("[Auth] 新用户注册: username={}", request.getUsername());
            return ApiResponse.success("注册成功", user);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    // ======================== 辅助 ========================

    /**
     * REST 登录成功后创建 Session，确保后续 OAuth2 授权码流程可用
     *
     * <p>背景: REST API 登录返回 JWT Token，Order(2) 链配置为 STATELESS 不会自动创建 Session。
     * 但 OAuth2 授权码模式（/oauth2/authorize，由 Order(1) 链处理）依赖 Session 判断用户是否已认证。
     * 此方法通过 AuthenticationManager 触发一次认证并将 SecurityContext 写入 HttpSession，
     * 使得后续 SAS 流程可以通过 HttpSessionSecurityContextRepository 识别已登录用户，无需重新输入密码。</p>
     *
     * <p>Session 存储的 key: {@link HttpSessionSecurityContextRepository#SPRING_SECURITY_CONTEXT_KEY}，
     * Order(1) 链通过 .securityContext(securityContextRepository) 显式使用
     * HttpSessionSecurityContextRepository 来读取。</p>
     */
    private void createSessionAfterRestLogin(String principalName, String rawPassword, HttpServletRequest request) {
        if (rawPassword == null) {
            log.debug("[Auth] 短信登录跳过 Session 创建: principal={}", principalName);
            return;
        }
        try {
            String authPrincipal = resolveUsernameIfPhone(principalName);

            UsernamePasswordAuthenticationToken authRequest =
                    UsernamePasswordAuthenticationToken.unauthenticated(authPrincipal, rawPassword);
            Authentication authResult = authenticationManager.authenticate(authRequest);

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authResult);
            SecurityContextHolder.setContext(context);

            HttpSession session = request.getSession(true);
            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            log.info("[Auth] Session 已创建: principal={}, sessionId={}, 可供 OAuth2 授权码流程使用",
                    authPrincipal, session.getId());
        } catch (Exception e) {
            log.error("[Auth] Session 创建失败(不影响 REST 登录本身): principal={}, error={}",
                    principalName, e.getMessage());
        }
    }

    /**
     * 若传入值匹配手机号格式，则从 DB 解析出用户名；否则原样返回。
     */
    private String resolveUsernameIfPhone(String principalName) {
        if (principalName == null || !principalName.matches("^1[3-9]\\d{9}$")) {
            return principalName;
        }
        return userRepository.findByPhoneIncludeDisabled(principalName)
                .map(UserEntity::getUsername)
                .orElse(principalName);
    }

    /**
     * 解密前端传输的加密密码。
     */
    private String decryptPassword(String encryptedPassword) {
        try {
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedPassword);
            String plaintext = tryDecrypt(encryptedBytes, "RSA/ECB/PKCS1Padding");
            if (plaintext == null) {
                plaintext = tryDecrypt(encryptedBytes, "RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            }
            if (plaintext != null) {
                log.debug("[Auth] 密码解密成功: 前端使用RSA加密传输");
                return plaintext;
            }
            log.debug("[Auth] 密码解密失败，按明文处理");
        } catch (IllegalArgumentException e) {
            log.debug("[Auth] 密码非Base64格式，按明文处理");
        } catch (Exception e) {
            log.debug("[Auth] 密码解密异常，按明文处理: {}", e.getMessage());
        }
        return encryptedPassword;
    }

    private String tryDecrypt(byte[] encryptedBytes, String algorithm) {
        try {
            Cipher cipher = Cipher.getInstance(algorithm);
            cipher.init(Cipher.DECRYPT_MODE, cryptoConfig.getPrivateKey());
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
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
