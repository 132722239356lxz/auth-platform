package com.liang.xz.knife4j.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * <p>Knife4j聚合配置 —— 基于 springdoc.swagger-ui.urls 的API文档聚合</p>
 *
 * <p>工作模式:</p>
 * <ol>
 *   <li>使用 springdoc-openapi + Knife4j Jakarta Starter（Spring Boot 3 完全兼容）</li>
 *   <li>通过 springdoc.swagger-ui.urls 配置各服务的 /v3/api-docs 地址</li>
 *   <li>浏览器端直接向 Gateway 网关请求各服务 OpenAPI 文档</li>
 *   <li>在 doc.html 页面通过顶部下拉菜单自由切换服务文档</li>
 * </ol>
 *
 * <p>访问地址:</p>
 * <pre>
 *   聚合文档首页: http://localhost:10909/doc.html
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class Knife4jAggregationConfig {

    @PostConstruct
    public void init() {
        log.info("========================================");
        log.info("[Knife4jAggregation] API文档聚合服务已启动");
        log.info("[Knife4jAggregation] 访问地址: http://localhost:10909/doc.html");
        log.info("[Knife4jAggregation] 通过 Gateway 网关聚合以下服务文档:");
        log.info("[Knife4jAggregation]   - 授权服务   (/auth-server)");
        log.info("[Knife4jAggregation]   - 审批流服务 (/auth-flow)");
        log.info("[Knife4jAggregation]   - 消息服务   (/auth-message)");
        log.info("[Knife4jAggregation]   - AI智能体   (/ai-agent-server)");
        log.info("[Knife4jAggregation]   - 系统管理   (/system-server)");
        log.info("[Knife4jAggregation]   - 日志服务   (/log-server)");
        log.info("========================================");
    }

}
