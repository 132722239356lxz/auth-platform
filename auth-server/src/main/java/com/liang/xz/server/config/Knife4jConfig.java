package com.liang.xz.server.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * <p>Knife4j / SpringDoc OpenAPI 配置</p>
 *
 * <p>功能:</p>
 * <ul>
 *   <li>生成OAuth2授权服务器的API文档</li>
 *   <li>配置Bearer Token认证方式</li>
 *   <li>支持 OAuth2 Authorization Code 和 Client Credentials 流程</li>
 *   <li>通过Knife4j UI可以在线调试API</li>
 * </ul>
 *
 * <p>访问地址:</p>
 * <ul>
 *   <li>Knife4j UI: http://localhost:9000/doc.html</li>
 *   <li>OpenAPI JSON: http://localhost:9000/v3/api-docs</li>
 *   <li>Swagger UI: http://localhost:9000/swagger-ui/index.html</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("授权门户中台 - 认证模块 API文档")
                        .description("""
                                ## OAuth2 授权门户中台 - 认证模块
                                
                                ### 核心功能
                                - **OAuth2端点**: 标准的OAuth2/OpenID Connect协议端点
                                - **REST认证**: 前后分离架构下的JWT密码登录和Token刷新
                                - **用户注册**: 用户自助注册
                                - **用户信息**: OIDC标准用户信息端点及扩展权限查询
                                - **企业端认证**: 企业端接入认证
                                
                                ### 认证方式
                                - Bearer Token (JWT)
                                - 支持 RS256 和 HS256 签名算法
                                - 自定义Token字段: tenant_id, user_type, client_type
                                
                                ### 多租户
                                - 通过 JWT 中的 tenant_id 实现租户隔离
                                - 请求头 X-Tenant-Id 可指定租户
                                
                                ### 子系统接入流程
                                1. 用户在授权中台登录
                                2. 获取 authorization_code
                                3. 子系统用 code 换 access_token (POST /oauth2/token)
                                4. 子系统用 access_token 获取用户信息 (GET /userinfo)
                                5. 子系统签发自己的 JWT Token
                                
                                ### 注意事项
                                - 客户端管理、授权审计、Token管理等业务API已迁移至 system-server 模块
                                - CORS和IP白名单已在Gateway层统一处理
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("授权平台团队")
                                .email("auth-team@example.com")
                                .url("https://github.com/auth-platform"))
                        .license(new License()
                                .name("Apache License 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server()
                                .url("http://127.0.0.1:9000")
                                .description("本地开发环境")))
                // 安全方案配置
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("输入JWT Token (从 /oauth2/token 获取)"))
                        .addSecuritySchemes("oauth2", new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .flows(new OAuthFlows()
                                        .authorizationCode(new OAuthFlow()
                                                .authorizationUrl("/oauth2/authorize")
                                                .tokenUrl("/oauth2/token")
                                                .scopes(new Scopes()
                                                        .addString("openid", "OpenID Connect")
                                                        .addString("profile", "用户信息")
                                                        .addString("read", "读取权限")
                                                        .addString("write", "写入权限")))
                                        .clientCredentials(new OAuthFlow()
                                                .tokenUrl("/oauth2/token")
                                                .scopes(new Scopes()
                                                        .addString("read", "读取权限")
                                                        .addString("write", "写入权限"))))))
                // 全局安全要求(可选，各接口可单独覆盖)
                .addSecurityItem(new SecurityRequirement()
                        .addList("bearerAuth"));
    }
}
