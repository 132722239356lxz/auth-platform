package com.liang.xz.knife4j;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * <p>Knife4j API聚合服务 —— 统一API文档门户</p>
 *
 * <p>核心功能:</p>
 * <ul>
 *   <li>使用 springdoc.swagger-ui.urls 通过 Gateway 网关聚合各服务文档</li>
 *   <li>聚合所有服务的 Swagger/OAS3 API 文档</li>
 *   <li>提供统一的 Knife4j UI 界面(doc.html)，顶部下拉切换服务</li>
 * </ul>
 *
 * <p>访问地址:</p>
 * <pre>
 *   聚合文档首页: http://localhost:10909/doc.html
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@SpringBootApplication
public class Knife4jAggregationApplication {

    public static void main(String[] args) {
        SpringApplication.run(Knife4jAggregationApplication.class, args);
    }
}
