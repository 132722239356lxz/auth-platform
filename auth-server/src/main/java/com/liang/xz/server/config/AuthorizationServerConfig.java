
package com.liang.xz.server.config;

import com.liang.xz.common.core.properties.OAuth2TokenKeyConfig;
import com.liang.xz.common.core.token.CustomTokenEnhancer;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

/**
 * <p>授权服务器安全配置 —— 认证与授权分离架构</p>
 *
 * <p>架构设计:</p>
 * <pre>
 *   ┌─────────────────────────────────────────────────┐
 *   │              auth-server (授权服务器)              │
 *   │  ┌──────────────────┐  ┌──────────────────────┐ │
 *   │  │ 认证端点(Order=1) │  │ 认证API(Order=2)     │ │
 *   │  │ /oauth2/**       │  │ /api/auth/**         │ │
 *   │  │ /login           │  │ /api/register        │ │
 *   │  │ /.well-known/**  │  │ /api/userinfo/**     │ │
 *   │  └──────────────────┘  └──────────────────────┘ │
 *   └─────────────────────────────────────────────────┘
 *                           ↓ JWT Token
 *   ┌─────────────────────────────────────────────────┐
 *   │              system-server (业务管理)             │
 *   │  /api/clients/**  /api/audit/**                 │
 *   │  /api/subsystem/**  /api/logs/**                │
 *   │  /api/feedback/**  /api/user/profile/**         │
 *   └─────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>安全链路分层:</p>
 * <ol>
 *   <li><b>Order(1) - 授权服务器:</b> 处理OAuth2认证、Token签发</li>
 *   <li><b>Order(2) - 认证API:</b> auth-server自身的认证API(REST登录/注册/用户信息)</li>
 *   <li><b>Order(3) - 默认:</b> 其他请求，表单登录保护</li>
 * </ol>
 *
 * <p>注: 客户端管理、授权审计、Token管理等业务API已迁移至 system-server 模块，
 * 由Gateway统一路由分发。CORS和IP白名单已迁移至Gateway层。</p>
 *
 * <p>自定义Token:</p>
 * <ul>
 *   <li>通过 CustomTokenEnhancer 在JWT中添加 tenant_id 等自定义字段</li>
 *   <li>支持RS256和HS256两种签名算法</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Import(CustomTokenEnhancer.class)
public class AuthorizationServerConfig {

    private final OAuth2TokenKeyConfig tokenProp;

    /**
     * 前端登录页地址（如 http://localhost:5173/login）
     * <p>SSO 未认证时重定向到此地址，而非 Thymeleaf 模板页</p>
     */
    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    /**
     * Order(1) 授权服务器 SecurityFilterChain
     * <p>负责OAuth2协议端点: /oauth2/authorize, /oauth2/token, /oauth2/jwks等</p>
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("[SecurityConfig] 初始化授权服务器 SecurityFilterChain (Order=1)");

        // 应用Spring Authorization Server默认配置
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

        // 获取OAuth2AuthorizationServerConfigurer进行自定义配置
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                // OIDC配置
                .oidc(oidc -> oidc
                        .providerConfigurationEndpoint(provider -> {
                            log.debug("[SecurityConfig] OIDC Provider配置端点启用");
                        })
                )
                // ★ 授权端点 - 注册 redirect_uri 自动 URL 解码转换器
                //    解决前端 encodeURIComponent + 网关 WebClient 转发导致的双重编码问题
                .authorizationEndpoint(authorizationEndpoint -> authorizationEndpoint
                        .authorizationRequestConverters(converters -> {
                            // 插入到首位，优先于默认转换器执行
                            //converters.add(0, new DecodedRedirectUriConverter());
                        })
                )
                // Token端点配置 - 支持自定义参数(tenant_id等)
                .tokenEndpoint(token -> token
                        .accessTokenRequestConverter(new CustomTokenRequestConverter())
                );

        http
                // 限制此过滤器链仅处理 OAuth2 协议相关路径，
                // 避免拦截 /api/crypto/public-key 等非 OAuth2 请求导致 401
                // 注意：/login 不在此处拦截，交给 Order(3) 的 formLogin 处理；
                //      否则 SAS 拦截 /login 但没有 formLogin → 认证无法完成 → principal 始终 anonymousUser
                .securityMatcher(new OrRequestMatcher(
                        new AntPathRequestMatcher("/oauth2/**"),
                        new AntPathRequestMatcher("/.well-known/**"),
                        new AntPathRequestMatcher("/connect/**"),
                        new AntPathRequestMatcher("/logout"),
                        new AntPathRequestMatcher("/userinfo"),
                        // /login/oauth2-continue 是 REST 登录后的续接端点，由 Order(1) 处理
                        // 确保 SAS 可以读取同一请求链中 session 存储的 SecurityContext
                        new AntPathRequestMatcher("/login/oauth2-continue")))
                // ★ 关键：显式配置 SecurityContextRepository，让 SAS 可以从 Session 读取认证信息
                //    解决 REST API 登录创建的 Session 无法被 SAS 识别的问题
                .securityContext(securityContext -> securityContext
                        .securityContextRepository(httpSessionSecurityContextRepository()))
                // 未认证时跳转前端登录页，携带 sso=true 参数供前端识别 OAuth2 流程
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(
                                new AuthenticationEntryPoint() {
                                    @Override
                                    public void commence(HttpServletRequest request, HttpServletResponse response,
                                                         AuthenticationException authException) throws java.io.IOException {
                                        response.sendRedirect(frontendUrl + "/login?sso=true");
                                    }
                                }));
        // ★ 重要：Order(1) 链不配置 oauth2ResourceServer
        //    /oauth2/authorize 走 Session 认证（REST 登录后写入 HttpSession）
        //    /oauth2/token 走 client_secret 认证
        //    /oauth2/jwks、/.well-known/** 公开无需认证
        //    若在此配置 JWT 资源服务器，前端带 Bearer Token 请求 /oauth2/authorize 时
        //    Principal 会变成 JwtAuthenticationToken（无法被 Jackson 反序列化），
        //    导致 oauth2_authorization 表的 attributes 字段存储了不可逆的序列化数据，
        //    /oauth2/token 读取时抛出 InvalidDefinitionException

        return http.build();
    }

    /**
     * Order(2) 认证API SecurityFilterChain
     * <p>保护auth-server自身的认证相关API(/api/auth, /api/register, /api/userinfo, /api/enterprise)</p>
     */
    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("[SecurityConfig] 初始化认证API SecurityFilterChain (Order=2)");

        http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        // 用户自助注册API公开
                        .requestMatchers("/api/register").permitAll()
                        // REST登录/刷新API公开
                        .requestMatchers("/api/auth/**").permitAll()
                        //获取密钥公开
                        .requestMatchers("/api/crypto/public-key").permitAll()
                        // 通过授权码查询用户信息(调试用)
                        .requestMatchers("/api/userinfo/by-code").permitAll()
                        // Knife4j/Swagger文档相关路径放行
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/doc.html",
                                "/webjars/**").permitAll()
                        // 其他API需要认证
                        .requestMatchers("/api/**").authenticated()
                )
                // 无状态API，禁用CSRF
                .csrf(csrf -> csrf.disable())
                // 无状态会话
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // JWT资源服务器
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> {}));

        return http.build();
    }

    /**
     * Order(3) 默认 SecurityFilterChain
     * <p>保护其他路径，使用表单登录</p>
     */
    @Bean
    @Order(3)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("[SecurityConfig] 初始化默认 SecurityFilterChain (Order=3)");

        http
                // 资源服务器JWT解析（/userinfo 等端点依赖此配置解析 Bearer Token）
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> {}))
                .authorizeHttpRequests(auth -> auth
                        // JWK Set 端点和健康检查公开
                        .requestMatchers("/.well-known/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // 登录注册页面和REST登录API公开
                        .requestMatchers("/login", "/register", "/api/register").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        // OAuth2 流程续接端点公开（由 Controller 自身判断 session 状态）
                        .requestMatchers("/login/oauth2-continue").permitAll()
                        // 用户信息端点公开(由OAuth2资源服务器JWT保护)
                        .requestMatchers("/userinfo", "/api/userinfo/**").permitAll()
                        // 企业端认证API公开
                        .requestMatchers("/api/enterprise/**").permitAll()
                        // 静态资源公开
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        // Knife4j文档公开
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/doc.html",
                                "/webjars/**").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage(frontendUrl + "/login")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl(frontendUrl + "/login/callback?login=success", false)
                        .failureUrl(frontendUrl + "/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl(frontendUrl + "/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .csrf(csrf -> csrf.disable());

        return http.build();
    }

    // ======================== 基础设施Bean ========================

    /**
     * Session-based SecurityContextRepository —— 供 SAS /oauth2/authorize 流程读取 Session 中的认证信息。
     *
     * <p>背景: REST API 登录（Order(2) STATELESS 链）通过 createSessionAfterRestLogin() 手动将 SecurityContext
     * 写入 HttpSession。SAS 的 /oauth2/authorize 端点需要从此 Repository 恢复认证用户，避免 anonymousUser。</p>
     *
     * <p>Order(1) 链通过 .securityContext() 显式注入此 Bean，覆盖 Spring Security 6 的默认行为，
     * 确保 SAS OAuth2 端点可以与 REST 登录共享 Session。</p>
     */
    @Bean
    public SecurityContextRepository httpSessionSecurityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * OAuth2 授权记录持久化服务 —— 拦截 JwtAuthenticationToken 防止序列化入库。
     *
     * <p>核心策略：不用反射、不改 Jackson、不扩白名单。直接在 save() 前将
     * attributes 中的 JwtAuthenticationToken 替换为 UsernamePasswordAuthenticationToken，
     * 从源头杜绝不可反序列化的类型进入 oauth2_authorization 表。</p>
     *
     * <p>JwtAuthenticationToken 来源：当带 Bearer Token 的请求访问 SAS 端点时，
     * Spring Security 的 oauth2ResourceServer 过滤器会将 Principal 解析为
     * JwtAuthenticationToken。该类型依赖 OAuth2ResourceServerJackson2Module 的
     * @JsonCreator Mixin 才能反序列化，但 SAS 的 JdbcOAuth2AuthorizationService
     * 内建 ObjectMapper 的 AllowlistTypeIdResolver 白名单不包含它。</p>
     *
     * <p>注意：Order(1) 链已不配置 oauth2ResourceServer，正常流程不会产生新的
     * JwtAuthenticationToken。此包装器作为兜底保护。</p>
     */
    @Bean
    public OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate,
                                                            RegisteredClientRepository registeredClientRepository) {
        JdbcOAuth2AuthorizationService delegate =
                new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
        return new OAuth2AuthorizationService() {
            @Override
            public void save(OAuth2Authorization authorization) {
                delegate.save(sanitize(authorization));
            }

            @Override
            public void remove(OAuth2Authorization authorization) {
                delegate.remove(authorization);
            }

            @Override
            public OAuth2Authorization findById(String id) {
                return delegate.findById(id);
            }

            @Override
            public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
                return delegate.findByToken(token, tokenType);
            }
        };
    }

    /**
     * 将 OAuth2Authorization attributes 中的 JwtAuthenticationToken
     * 替换为 UsernamePasswordAuthenticationToken，后者在 SAS 的 ObjectMapper
     * 中可正常序列化/反序列化。
     */
    private OAuth2Authorization sanitize(OAuth2Authorization authorization) {
        Object principal = authorization.getAttribute(
                java.security.Principal.class.getName());
        if (principal instanceof JwtAuthenticationToken jwtToken) {
            UsernamePasswordAuthenticationToken safePrincipal =
                    new UsernamePasswordAuthenticationToken(
                            jwtToken.getName(), null, jwtToken.getAuthorities());
            OAuth2Authorization rebuilt = OAuth2Authorization.from(authorization)
                    .attributes(attrs -> attrs.put(
                            java.security.Principal.class.getName(), safePrincipal))
                    .build();
            log.debug("[Sanitize] JwtAuthenticationToken → UsernamePasswordAuthenticationToken: {}",
                    jwtToken.getName());
            return rebuilt;
        }
        return authorization;
    }

    /**
     * 认证管理器 —— 供 AuthController 手动认证使用
     * <p>REST 登录后通过此 Bean 创建 Session，使得 OAuth2 授权码流程可识别已登录用户</p>
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * 密码编码器 —— BCrypt(用于客户端密钥匹配)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 授权服务器设置
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer("http://127.0.0.1:9000")
                // OAuth2端点路径(使用默认值)
                .authorizationEndpoint("/oauth2/authorize")
                .tokenEndpoint("/oauth2/token")
                .jwkSetEndpoint("/oauth2/jwks")
                .tokenRevocationEndpoint("/oauth2/revoke")
                .tokenIntrospectionEndpoint("/oauth2/introspect")
                .oidcClientRegistrationEndpoint("/connect/register")
                // 将SAS内置的userinfo端点移到/connect/userinfo，让自定义Controller独占/userinfo
                .oidcUserInfoEndpoint("/connect/userinfo")
                .build();
    }

    /**
     * Token设置 —— 全局默认TTL，客户端可单独覆盖
     */
    @Bean
    public TokenSettings tokenSettings() {
        return TokenSettings.builder()
                .accessTokenTimeToLive(tokenProp.getAccessTokenTimeToLive())
                .refreshTokenTimeToLive(tokenProp.getRefreshTokenTimeToLive())
                .reuseRefreshTokens(false)
                .build();
    }

    /**
     * OAuth2 Token生成器 —— 组合JWT(含自定义Claims增强) + AccessToken + RefreshToken
     *
     * <p>由于手动定义了 AuthorizationServerSettings 和 TokenSettings，
     * SAS自动配置不会创建此Bean，需显式声明。</p>
     *
     * <p>Token生成链:
     *   JwtGenerator(CustomTokenEnhancer增强) → OAuth2AccessTokenGenerator → OAuth2RefreshTokenGenerator</p>
     */
    @Bean
    public OAuth2TokenGenerator<?> tokenGenerator(JWKSource<SecurityContext> jwkSource,
                                                   CustomTokenEnhancer tokenClaimsCustomizer) {
        NimbusJwtEncoder jwtEncoder = new NimbusJwtEncoder(jwkSource);
        JwtGenerator jwtGenerator = new JwtGenerator(jwtEncoder);

        // 将自定义Claims增强器直接作为 JwtCustomizer 委托。
        // JwtEncodingContext 是 OAuth2TokenContext 的子类型，复用同一套
        // tenant_id / user_id / nickname / permissions 注入逻辑，避免丢失授权信息。
        jwtGenerator.setJwtCustomizer(tokenClaimsCustomizer::customize);

        return new DelegatingOAuth2TokenGenerator(
                jwtGenerator,
                new OAuth2AccessTokenGenerator(),
                new OAuth2RefreshTokenGenerator()
        );
    }

}
