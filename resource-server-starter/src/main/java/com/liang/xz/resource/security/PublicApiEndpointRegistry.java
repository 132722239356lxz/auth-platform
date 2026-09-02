package com.liang.xz.resource.security;

import com.liang.xz.common.core.annotation.PublicApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * <p>公开API端点注册器 —— 在应用启动时扫描所有标注了 {@link PublicApi} 的接口路径</p>
 *
 * <p>扫描结果用于:</p>
 * <ul>
 *   <li>自动注册到 Spring Security 的 permitAll 白名单（业务层免认证）</li>
 *   <li>启动日志输出，方便开发者将路径添加到网关配置</li>
 * </ul>
 *
 * <p>实现 {@link ApplicationRunner} 而非 {@link org.springframework.beans.factory.InitializingBean}：
 * 由于 {@code RequestMappingHandlerMapping} 对各 Controller 的映射注册发生在其自身的
 * {@code afterPropertiesSet()} 中，若本注册器也用 {@code InitializingBean}，可能因初始化顺序早于映射注册
 * 而扫描到空映射（误报"未检测到 @PublicApi 注解"）。{@code ApplicationRunner} 在所有单例 bean
 * 完成初始化、映射注册就绪、Spring 容器刷新完成后才回调，从而保证扫描到的端点完整。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
public class PublicApiEndpointRegistry implements ApplicationContextAware, ApplicationRunner {

    private ApplicationContext applicationContext;

    /** 所有标注了 @PublicApi 的接口路径（Ant 风格，如 /api/user/list、/callback/**） */
    private volatile Set<String> publicApiPaths = Collections.emptySet();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        Set<String> paths = scanPublicApiEndpoints();
        this.publicApiPaths = Collections.unmodifiableSet(paths);

        if (paths.isEmpty()) {
            log.info("[PublicApi] 未检测到 @PublicApi 注解，无需注册公开端点");
        } else {
            log.info("[PublicApi] 扫描到 {} 个公开端点，已跳过认证:\n  {}",
                    paths.size(), String.join("\n  ", paths));
        }
    }

    /**
     * 获取所有公开API路径
     */
    public Set<String> getPublicApiPaths() {
        return publicApiPaths;
    }

    /**
     * 扫描所有标注了 @PublicApi 的 RequestMapping
     */
    private Set<String> scanPublicApiEndpoints() {
        Set<String> paths = new LinkedHashSet<>();

        try {
            RequestMappingHandlerMapping mapping =
                    applicationContext.getBean("requestMappingHandlerMapping",
                            RequestMappingHandlerMapping.class);

            Map<RequestMappingInfo, HandlerMethod> handlerMethods = mapping.getHandlerMethods();
            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
                RequestMappingInfo info = entry.getKey();
                HandlerMethod method = entry.getValue();

                boolean isPublicApi = method.hasMethodAnnotation(PublicApi.class)
                        || method.getBeanType().isAnnotationPresent(PublicApi.class);

                if (isPublicApi && info.getPatternsCondition() != null) {
                    paths.addAll(info.getPatternsCondition().getPatterns());
                }
            }
        } catch (Exception e) {
            log.warn("[PublicApi] 扫描公开端点时发生异常: {}", e.getMessage(), e);
        }

        return paths;
    }
}
