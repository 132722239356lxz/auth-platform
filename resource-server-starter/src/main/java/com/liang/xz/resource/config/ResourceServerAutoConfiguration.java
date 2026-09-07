package com.liang.xz.resource.config;

import com.liang.xz.common.core.context.TenantContextFilter;
import com.liang.xz.common.core.token.CustomTokenClaims;
import com.liang.xz.common.core.token.RedisTokenBlacklistService;
import com.liang.xz.common.core.token.TokenBlacklistService;
import com.liang.xz.resource.properties.ResourceServerProperties;
import com.liang.xz.resource.security.*;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

/**
 * <p>资源服务器自动配置 —— 业务模块仅需引入此Starter即可获得完整的资源认证+权限控制能力</p>
 *
 * <p>核心能力:</p>
 * <ul>
 *   <li><b>JWT认证:</b> 自动配置 SecurityFilterChain，保护 /api/** 路径</li>
 *   <li><b>Token验证:</b> 过期检查、用户状态检查(禁用/黑名单)、Token黑名单检查</li>
 *   <li><b>权限注解:</b> @RequirePermission 注解支持，用户→角色→菜单权限链</li>
 *   <li><b>全局异常:</b> 统一处理认证/授权异常，返回标准JSON</li>
 *   <li><b>租户隔离:</b> 自动提取JWT中的 tenant_id 到请求上下文</li>
 *   <li><b>双算法:</b> 支持 RS256(非对称) 和 HS256(对称) 两种JWT验证</li>
 * </ul>
 *
 * <p>业务模块使用方式:</p>
 * <pre>
 *   // pom.xml 引入
 *   &lt;dependency&gt;
 *       &lt;groupId&gt;com.liang&lt;/groupId&gt;
 *       &lt;artifactId&gt;resource-server-starter&lt;/artifactId&gt;
 *   &lt;/dependency&gt;
 *
 *   // application.yml 配置
 *   auth:
 *     resource:
 *       enabled: true
 *       jwk-set-uri: http://auth-server:9000/oauth2/jwks
 *       algorithm: RS256
 *       token:
 *         check-user-status: true
 *         check-token-blacklist: false
 *         required-claims:
 *           - sub
 *           - user_id
 *
 *   // 实现 UserPermissionProvider 接口(可选，用于数据库查询用户权限和状态)
 *   // Controller 方法上使用 @RequirePermission 注解
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@RequiredArgsConstructor
@EnableConfigurationProperties(ResourceServerProperties.class)
@ConditionalOnProperty(prefix = "auth.resource", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ResourceServerAutoConfiguration {

    private final ResourceServerProperties properties;

    // ======================== JWT 解码器 ========================

    /**
     * JWT 解码器 —— 根据配置自动选择 RS256 或 HS256
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder() {
        String algorithm = properties.getAlgorithm();
        log.info("[ResourceServer] 初始化JWT解码器: algorithm={}", algorithm);

        if ("HS256".equalsIgnoreCase(algorithm)) {
            // HS256 对称密钥模式
            if (properties.getSecretKey() == null || properties.getSecretKey().isEmpty()) {
                throw new IllegalStateException("HS256模式需要配置 auth.resource.secret-key");
            }
            byte[] keyBytes = properties.getSecretKey().getBytes(StandardCharsets.UTF_8);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
            log.info("[ResourceServer] HS256 JWT解码器初始化完成");
            return decoder;
        } else {
            // RS256 非对称密钥模式(默认)
            if (properties.getJwkSetUri() == null || properties.getJwkSetUri().isEmpty()) {
                throw new IllegalStateException("RS256模式需要配置 auth.resource.jwk-set-uri");
            }
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build();
            log.info("[ResourceServer] RS256 JWT解码器初始化完成: jwkSetUri={}", properties.getJwkSetUri());
            return decoder;
        }
    }

    // ======================== JWT 权限转换 ========================

    /**
     * JWT 认证转换器 —— 将JWT中的scope/authorities转换为Spring Security权限
     * <p>
     * 权限来源:
     * <ul>
     *   <li>scope → SCOPE_xxx (标准OAuth2 scope)</li>
     *   <li>user_type=admin → ROLE_ADMIN (管理员角色)</li>
     * </ul>
     * <p>注意: permissions 不再通过 JWT 传递，权限校验由 {@code PermissionAspect} 的 DB 兜底完成。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
        scopeConverter.setAuthorityPrefix("SCOPE_");
        scopeConverter.setAuthoritiesClaimName("scope");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            // 1. 标准OAuth2 scope权限
            Collection<GrantedAuthority> authorities = new ArrayList<>(
                    scopeConverter.convert(jwt) != null ? scopeConverter.convert(jwt) : Collections.emptyList());

            // 2. 管理员角色
            String userType = jwt.getClaimAsString(CustomTokenClaims.CLAIM_USER_TYPE);
            if ("admin".equalsIgnoreCase(userType)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }

            // 3. 用户ID(用于@PreAuthorize中的表达式)
            String userId = jwt.getClaimAsString(CustomTokenClaims.CLAIM_USER_ID);
            if (userId != null) {
                authorities.add(new SimpleGrantedAuthority("USER_ID_" + userId));
            }

            log.debug("[ResourceServer] JWT权限转换: user={}, totalAuthorities={}",
                    jwt.getSubject(), authorities.size());
            return authorities;
        });

        return converter;
    }

    // ======================== Token验证过滤器 ========================

    /**
     * Token验证过滤器 —— 检查Token过期、用户状态、Token黑名单
     */
    @Bean
    @ConditionalOnMissingBean
    public TokenValidationFilter tokenValidationFilter(ObjectProvider<UserPermissionProvider> permissionProvider,
                                                       ObjectProvider<TokenBlacklistService> blacklistServiceProvider) {
        UserPermissionProvider provider = permissionProvider.getIfAvailable();
        TokenValidationProperties tokenProps = properties.getToken();
        TokenBlacklistService blacklistService = blacklistServiceProvider.getIfAvailable();
        log.info("[ResourceServer] Token验证过滤器: checkExpiry={}, checkUserStatus={}, checkBlacklist={}, blacklistImpl={}",
                tokenProps.isCheckTokenExpiry(), tokenProps.isCheckUserStatus(),
                tokenProps.isCheckTokenBlacklist(),
                blacklistService != null ? blacklistService.getClass().getSimpleName() : "无(未接入Redis)");
        return new TokenValidationFilter(provider, tokenProps, blacklistService);
    }

    /**
     * Token 黑名单服务（Redis 实现）—— 支撑退出登录后 Token 立即失效。
     * <p>仅在应用引入了 Redis（存在 StringRedisTemplate）时生效；
     * 未引入 Redis 的服务降级为不校验黑名单，并在启动日志中提示。</p>
     */
    @Bean
    @ConditionalOnMissingBean(TokenBlacklistService.class)
    public TokenBlacklistService tokenBlacklistService(ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        StringRedisTemplate stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (stringRedisTemplate == null) {
            log.warn("[ResourceServer] 未检测到 StringRedisTemplate，Token 黑名单能力不可用，"
                    + "退出登录后旧 Token 在过期前仍可访问本服务");
            return null;
        }
        log.info("[ResourceServer] 启用 Redis Token 黑名单服务");
        return new RedisTokenBlacklistService(stringRedisTemplate);
    }

    // ======================== SecurityFilterChain ========================

    /**
     * 业务资源服务器 SecurityFilterChain
     * <p>集成 JWT认证 + Token验证 + 权限控制</p>
     */
    @Bean
    @Order(1)
    @ConditionalOnMissingBean(name = "resourceServerSecurityFilterChain")
    public SecurityFilterChain resourceServerSecurityFilterChain(HttpSecurity http,
                                                                   TokenValidationFilter tokenValidationFilter,
                                                                   PublicApiEndpointRegistry publicApiRegistry) throws Exception {
        log.info("[ResourceServer] 配置资源服务器SecurityFilterChain (含Token验证)");

        // 合并所有放行路径: 配置的permitUrls + @PublicApi 扫描的路径 + /api/public/**
        // 注意: @PublicApi 的扫描在 ApplicationRunner 阶段完成(晚于 SecurityFilterChain 构建),
        // 因此此处不把 scannedPublicPaths 当作静态数组固化到 FilterChain, 而是用动态 RequestMatcher 在
        // 每次请求时实时查询注册器的最新路径, 避免 FilterChain 构建时路径尚为空导致放行失效。
        List<String> configPermitUrls = properties.getPermitUrls();
        Set<String> scannedPublicPaths = publicApiRegistry.getPublicApiPaths();
        String[] staticPermitPaths = Stream.concat(
                configPermitUrls.stream(),
                scannedPublicPaths.stream()
        ).toArray(String[]::new);

        log.info("[ResourceServer] 放行路径: config={}, @PublicApi(已扫描)={}",
                configPermitUrls.size(), scannedPublicPaths.size());

        // 动态匹配 @PublicApi 路径: 运行时从注册器读取, 即使 SecurityFilterChain 先构建也能正确放行
        org.springframework.security.web.util.matcher.RequestMatcher publicApiMatcher =
                new org.springframework.security.web.util.matcher.RequestMatcher() {
                    @Override
                    public boolean matches(jakarta.servlet.http.HttpServletRequest request) {
                        String path = request.getServletPath();
                        for (String p : publicApiRegistry.getPublicApiPaths()) {
                            if (pathMatches(p, path)) {
                                return true;
                            }
                        }
                        return false;
                    }
                };

        http
                .securityMatcher("/**")
                // 在认证后、授权前插入Token验证过滤器
                .addFilterAfter(tokenValidationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(staticPermitPaths).permitAll()
                        .requestMatchers(publicApiMatcher).permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .bearerTokenResolver(this::resolveBearerToken));

        return http.build();
    }

    // ======================== 默认权限提供者 ========================

    /**
     * 默认数据库权限提供者 —— 当业务模块未自定义 {@link UserPermissionProvider} 时自动生效，
     * 基于模块自身数据源查询标准权限表(sys_menu/sys_role_menu/sys_user_role)，
     * 确保 {@link PermissionAspect} 始终能从数据库加载用户权限。
     * <p>若模块自行实现了 {@link UserPermissionProvider} 并注册为 Bean，则优先使用模块实现。</p>
     */
    @Bean
    @ConditionalOnMissingBean(UserPermissionProvider.class)
    public UserPermissionProvider defaultUserPermissionProvider(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            log.warn("[ResourceServer] 未找到 JdbcTemplate，无法注册默认数据库权限提供者，"
                    + "权限将从数据库加载失败(仅管理员可访问受保护接口)");
            return new UserPermissionProvider() {
                @Override
                public Set<String> getPermissions(String username, Long userId) {
                    return Collections.emptySet();
                }
            };
        }
        log.info("[ResourceServer] 注册默认数据库权限提供者(DefaultDbUserPermissionProvider)");
        return new DefaultDbUserPermissionProvider(jdbcTemplate);
    }

    // ======================== 权限切面 ========================

    /**
     * 权限校验AOP切面 —— 拦截 @RequirePermission 注解
     */
    @Bean
    @ConditionalOnMissingBean
    public PermissionAspect permissionAspect(ObjectProvider<UserPermissionProvider> permissionProvider) {
        UserPermissionProvider provider = permissionProvider.getIfAvailable();
        String permissionSource = properties.getPermissionSource();
        log.info("[ResourceServer] 权限校验AOP切面已启用: dbProvider={}, permissionSource={}",
                provider != null, permissionSource);
        return new PermissionAspect(provider, permissionSource);
    }

    // ======================== 全局异常处理器 ========================

    /**
     * 全局安全异常处理器 —— 统一处理认证/授权异常
     */
    @Bean
    @ConditionalOnMissingBean
    public GlobalSecurityExceptionHandler globalSecurityExceptionHandler() {
        log.info("[ResourceServer] 全局安全异常处理器已启用");
        return new GlobalSecurityExceptionHandler();
    }

    // ======================== 租户上下文过滤器 ========================

    /**
     * 租户上下文过滤器 —— 从JWT中提取tenant_id并设置到ThreadLocal
     */
    @Bean
    @ConditionalOnProperty(prefix = "auth.resource", name = "tenant-enabled", havingValue = "true", matchIfMissing = true)
    public TenantContextFilter tenantContextFilter() {
        log.info("[ResourceServer] 启用租户上下文过滤器");
        return new TenantContextFilter();
    }

    // ======================== Bearer Token 解析 ========================

    /**
     * Bearer Token 解析器 —— 从请求中提取JWT
     * 支持从 Header 和 Query Parameter 中获取
     */
    private String resolveBearerToken(HttpServletRequest request) {
        // 1. 优先从 Authorization Header 获取
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            // 从JWT中提取tenant_id存入request属性(供TenantContextFilter使用)
            extractTenantToRequest(request, token);
            return token;
        }

        // 2. 从 Query Parameter 获取(WebSocket等场景)
        String queryToken = request.getParameter("access_token");
        if (queryToken != null && !queryToken.isEmpty()) {
            extractTenantToRequest(request, queryToken);
            return queryToken;
        }

        return null;
    }

    /**
     * 从JWT Token中提取租户ID到请求属性
     */
    private void extractTenantToRequest(HttpServletRequest request, String token) {
        try {
            Jwt jwt = jwtDecoder().decode(token);
            String tenantId = jwt.getClaimAsString(CustomTokenClaims.CLAIM_TENANT_ID);
            if (tenantId != null) {
                request.setAttribute("jwt_tenant_id", tenantId);
            }
        } catch (Exception e) {
            log.debug("[ResourceServer] 无法解析JWT中的租户信息: {}", e.getMessage());
        }
    }

    // ======================== 跨域配置 ========================

    /**
     * 跨域配置
     */
    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Collections.singletonList("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    // ======================== @PublicApi 动态路径匹配 ========================

    private static final AntPathMatcher PUBLIC_API_PATH_MATCHER = new AntPathMatcher();

    /**
     * 判断请求路径是否匹配 @PublicApi 扫描出的 Ant 风格路径(支持 **、* 通配符)。
     *
     * @param pattern @PublicApi 注册的 Ant 风格路径，如 /api/ai/agent/tool/** 或 /api/user/list
     * @param path    实际请求路径
     * @return 匹配则返回 true
     */
    private boolean pathMatches(String pattern, String path) {
        if (pattern == null || path == null) {
            return false;
        }
        return PUBLIC_API_PATH_MATCHER.match(pattern, path);
    }
}
