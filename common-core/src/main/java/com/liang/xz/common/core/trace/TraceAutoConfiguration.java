package com.liang.xz.common.core.trace;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * <p>Trace 链路追踪自动配置</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnWebApplication
public class TraceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TraceFilter traceFilter() {
        return new TraceFilter();
    }

    @Bean
    public FilterRegistrationBean<TraceFilter> traceFilterRegistration(TraceFilter traceFilter) {
        FilterRegistrationBean<TraceFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(traceFilter);
        registration.addUrlPatterns("/*");
        // 最高优先级，确保 traceId 最先设置
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
