package com.liang.xz.message.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * <p>消息服务 - Knife4j / SpringDoc 文档配置</p>
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
                        .title("消息服务 - API文档")
                        .description("消息服务，提供短信、邮件、站内信等消息推送功能")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("授权平台团队")
                                .email("auth-team@example.com"))
                        .license(new License()
                                .name("Apache License 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server()
                                .url("http://127.0.0.1:9002")
                                .description("本地开发环境")));
    }
}
