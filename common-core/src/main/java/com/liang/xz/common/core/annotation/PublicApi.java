package com.liang.xz.common.core.annotation;

import java.lang.annotation.*;

/**
 * <p>公开API注解 —— 标注在Controller类或方法上，表示该接口无需认证即可被外部系统调用</p>
 *
 * <p>作用范围:</p>
 * <ul>
 *   <li><b>业务层 (resource-server-starter):</b> 自动扫描标注了 {@code @PublicApi} 的接口，
 *       将其路径注册到 Spring Security 的 permitAll，同时 PermissionAspect 跳过权限校验</li>
 *   <li><b>网关层 (gateway):</b> 需要将 {@code @PublicApi} 接口的路径配置到 Nacos 的
 *       {@code auth.gateway.public-api-paths} 中，
 *       网关 AuthGlobalFilter 会自动放行这些路径（启动时会打印所有 @PublicApi 路径，方便配置）</li>
 * </ul>
 *
 * <p>使用方式:</p>
 * <pre>
 *   // 类级别 —— 整个Controller的所有方法都对外公开
 *   {@code @PublicApi}
 *   {@code @RestController}
 *   public class CallbackController { ... }
 *
 *   // 方法级别 —— 仅指定方法对外公开
 *   {@code @RestController}
 *   public class DataController {
 *       {@code @PublicApi}
 *       {@code @GetMapping("/api/data/public-export")}
 *       public ApiResponse publicExport() { ... }
 *   }
 * </pre>
 *
 * <p>网关层配置示例 (Nacos 共享配置 {@code gateway-public-apis.yml}):</p>
 * <pre>
 *   auth:
 *     gateway:
 *       public-api-paths:
 *         - /api/data/public-export
 *         - /callback/wechat/**
 *         - /api/external/**
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublicApi {

    /**
     * 接口描述（可选，仅用于文档说明）
     */
    String value() default "";
}
