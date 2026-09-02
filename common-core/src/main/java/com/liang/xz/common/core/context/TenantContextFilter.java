package com.liang.xz.common.core.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * <p>租户上下文过滤器 —— 从请求头提取租户ID并设置到 ThreadLocal</p>
 *
 * <p>提取规则:</p>
 * <ol>
 *   <li>从请求头 X-Tenant-Id 中提取租户ID</li>
 *   <li>如果请求头不存在，从 JWT Token 的 tenant_id claim 中提取</li>
 *   <li>请求结束后自动清理 ThreadLocal</li>
 * </ol>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
public class TenantContextFilter extends OncePerRequestFilter {

    /** 租户ID的请求头名称 */
    public static final String TENANT_HEADER = "X-Tenant-Id";

    /** 默认租户ID(当无法从请求中获取时使用) */
    public static final String DEFAULT_TENANT = "default";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String tenantId = extractTenantId(request);
        TenantContext.setTenantId(tenantId);
        log.debug("[TenantContext] 设置租户: {}", tenantId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            log.debug("[TenantContext] 清理租户上下文");
        }
    }

    /**
     * 从请求中提取租户ID
     * 优先级: X-Tenant-Id请求头 > JWT Token claim > 默认值
     */
    private String extractTenantId(HttpServletRequest request) {
        // 1. 从请求头获取
        String headerTenant = request.getHeader(TENANT_HEADER);
        if (headerTenant != null && !headerTenant.isBlank()) {
            return headerTenant.trim();
        }

        // 2. 从 JWT Token 的 tenant_id claim 获取(由 Resource Server Filter 解析后存入 request attribute)
        Object jwtTenant = request.getAttribute("jwt_tenant_id");
        if (jwtTenant instanceof String tenant && !tenant.isBlank()) {
            return tenant;
        }

        // 3. 返回默认租户
        return DEFAULT_TENANT;
    }
}
