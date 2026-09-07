package com.liang.xz.resource.properties;

import com.liang.xz.resource.security.TokenValidationProperties;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;

/**
 * <p>资源服务器配置属性</p>
 *
 * <p>支持HS256和RS256两种JWT签名验证方式:</p>
 * <ul>
 *   <li><b>jwk-set-uri:</b> RS256模式，从授权服务器获取JWK公钥</li>
 *   <li><b>secret-key:</b> HS256模式，使用共享对称密钥</li>
 * </ul>
 *
 * <p>配置示例:</p>
 * <pre>
 *   # RS256模式(推荐生产使用)
 *   auth.resource.jwk-set-uri=http://auth-server:9000/oauth2/jwks
 *
 *   # HS256模式(适合内部服务间调用)
 *   auth.resource.secret-key=my-hs256-shared-secret-key
 *
 *   # Token验证配置
 *   auth.resource.token.check-user-status=true
 *   auth.resource.token.check-token-blacklist=false
 *   auth.resource.token.required-claims[0]=sub
 *   auth.resource.token.required-claims[1]=user_id
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "auth.resource")
public class ResourceServerProperties {

    /** 是否启用资源服务器模式(认证与授权分离) */
    private boolean enabled = true;

    /** JWK Set URI (RS256模式) */
    private String jwkSetUri;

    /** HS256共享密钥 (HS256模式) */
    private String secretKey;

    /** 默认签名算法: RS256 或 HS256 */
    private String algorithm = "RS256";

    /** 授权服务器Issuer URI */
    private String issuerUri;

    /** 允许放行的路径(不需要认证) */
    private List<String> permitUrls = List.of(
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/doc.html",
            "/webjars/**",
            "/actuator/health"
    );

    /** 是否启用租户隔离 */
    private boolean tenantEnabled = true;

    /**
     * 权限来源模式(已废弃)。
     * <p>权限一律从数据库查询({@code UserPermissionProvider})，不再从 JWT / GrantedAuthorities 获取，
     * 此配置项保留仅为向后兼容，不再影响权限解析逻辑。</p>
     */
    @Deprecated
    private String permissionSource = "jwt";

    /** Token验证配置 */
    @NestedConfigurationProperty
    private TokenValidationProperties token = new TokenValidationProperties();
}
