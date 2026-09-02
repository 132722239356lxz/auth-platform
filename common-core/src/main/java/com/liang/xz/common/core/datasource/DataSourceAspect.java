package com.liang.xz.common.core.datasource;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * <p>数据源切换切面 —— 根据 @TargetDataSource 注解自动切换</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
public class DataSourceAspect {

    private static final Logger log = LoggerFactory.getLogger(DataSourceAspect.class);

    @Pointcut("@annotation(com.liang.xz.common.core.datasource.TargetDataSource)")
    public void dataSourcePointCut() {
    }

    @Around("dataSourcePointCut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        TargetDataSource annotation = signature.getMethod().getAnnotation(TargetDataSource.class);
        String dataSourceName = annotation.value();

        // 切换数据源
        String previous = DataSourceContext.get();
        DataSourceContext.set(dataSourceName);

        log.debug("Switch datasource: {} -> {}", previous, dataSourceName);

        try {
            return point.proceed();
        } finally {
            // 恢复之前的数据源
            DataSourceContext.set(previous);
        }
    }
}
