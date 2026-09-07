package com.liang.xz.common.core.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.common.core.trace.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.env.Environment;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统一请求日志切面
 * <p>
 * 位于 common-core 层, 自动拦截所有 Spring MVC 控制器的请求,
 * 将请求信息(URI、方法、IP、参数、响应状态、耗时、异常等)上报到 log-server。
 * 上报动作异步批量进行, 对业务主流程无阻塞影响。
 * <p>
 * 启用条件:
 * <ul>
 *   <li>log.client.enabled=true</li>
 *   <li>log.client.request-log.enabled=true (默认启用)</li>
 *   <li>存在 LogReportClient Bean</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({ProceedingJoinPoint.class, HttpServletRequest.class})
@ConditionalOnBean(LogReportClient.class)
@ConditionalOnProperty(value = "log.client.request-log.enabled", havingValue = "true", matchIfMissing = true)
public class RequestLogAspect {

    private final LogReportClient logReportClient;
    private final LogClientProperties properties;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final String applicationName;

    /** 需要脱敏的敏感字段名 */
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "secret", "clientSecret", "newSecret",
            "token", "accessToken", "refreshToken", "access_token",
            "refresh_token", "client_secret", "authorization",
            "masterKey", "secretKey", "privateKey", "credential"
    );

    /** 响应体最大长度 */
    private static final int MAX_RESPONSE_LENGTH = 2000;

    /** 请求参数最大长度 */
    private static final int MAX_PARAMS_LENGTH = 2000;

    @Pointcut("@within(org.springframework.web.bind.annotation.RestController)"
            + " || @within(org.springframework.stereotype.Controller)")
    public void controllerPointcut() {
    }

    @Around("controllerPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;
        HttpServletResponse response = attributes != null ? attributes.getResponse() : null;

        if (request != null && shouldIgnore(request.getRequestURI())) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        String requestUri = request != null ? request.getRequestURI() : null;
        String httpMethod = request != null ? request.getMethod() : null;
        String clientIp = request != null ? resolveClientIp(request) : null;
        String username = resolveUsername(request);
        String className = method.getDeclaringClass().getName();
        String methodName = method.getName();
        String params = extractParams(joinPoint.getArgs());
        String module = resolveModule();
        String traceId = TraceContext.getTraceId();

        Object result = null;
        Throwable error = null;
        int httpStatus = 200;
        boolean success = true;

        try {
            result = joinPoint.proceed();
            if (response != null) {
                httpStatus = response.getStatus();
            }
            return result;
        } catch (Throwable e) {
            error = e;
            success = false;
            if (response != null) {
                httpStatus = response.getStatus() != 200 ? response.getStatus() : 500;
            } else {
                httpStatus = 500;
            }
            throw e;
        } finally {
            long costTime = System.currentTimeMillis() - start;
            String responseBody = success ? summarizeResult(result) : null;

            LogReportClient.LogEntry entry = new LogReportClient.LogEntry();
            entry.setTraceId(traceId);
            entry.setModule(module);
            entry.setCategory("request");
            entry.setLevel(success ? "INFO" : "ERROR");
            entry.setClassName(className);
            entry.setMethodName(methodName);
            entry.setMessage(buildMessage(requestUri, httpMethod, httpStatus, success));
            entry.setFullMessage(buildFullMessage(requestUri, httpMethod, params, responseBody, success));
            entry.setRequestUri(requestUri);
            entry.setHttpMethod(httpMethod);
            entry.setHttpStatus(httpStatus);
            entry.setCostTime(costTime);
            entry.setClientIp(clientIp);
            entry.setUsername(username);

            if (!success && error != null) {
                entry.setExceptionType(error.getClass().getName());
                entry.setExceptionStack(stackTraceToString(error));
            }

            try {
                logReportClient.report(entry);
            } catch (Exception ex) {
                log.warn("[RequestLogAspect] 上报请求日志到 log-server 失败: {}", ex.getMessage());
            }

            log.info("[RequestLog] {} {} -> {} ({}ms) [{}]",
                    httpMethod, requestUri, httpStatus, costTime, traceId);
        }
    }

    /**
     * 判断请求路径是否忽略记录
     */
    private boolean shouldIgnore(String uri) {
        if (uri == null) {
            return false;
        }
        Set<String> ignorePaths = properties.getRequestLog() != null
                ? properties.getRequestLog().getIgnorePaths()
                : Set.of();
        for (String prefix : ignorePaths) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析模块名, 优先取 spring.application.name
     */
    private String resolveModule() {
        String name = environment.getProperty("spring.application.name");
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return applicationName;
    }

    /**
     * 解析当前用户名
     * <p>优先从 Spring Security 上下文获取, 其次尝试 X-User 请求头</p>
     */
    private String resolveUsername(HttpServletRequest request) {
        // 1. Spring Security 上下文
        try {
            Object authentication = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (authentication != null) {
                String name = invokeGetName(authentication);
                if (name != null && !name.isEmpty() && !"anonymousUser".equals(name)) {
                    return name;
                }
            }
        } catch (Exception ignored) {
            // 未引入 Spring Security 或获取失败时静默降级
        }

        // 2. 请求头
        if (request != null) {
            String user = request.getHeader("X-User");
            if (user != null && !user.isEmpty()) {
                return user;
            }
        }

        return null;
    }

    private String invokeGetName(Object authentication) {
        try {
            Method getNameMethod = authentication.getClass().getMethod("getName");
            Object result = getNameMethod.invoke(authentication);
            return result instanceof String ? (String) result : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析客户端真实 IP
     */
    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 提取请求参数并脱敏
     */
    private String extractParams(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        List<Map<String, Object>> paramList = new ArrayList<>();
        for (Object arg : args) {
            if (arg == null || isFrameworkObject(arg)) {
                continue;
            }
            Map<String, Object> paramMap = new LinkedHashMap<>();
            paramMap.put("type", arg.getClass().getSimpleName());
            paramMap.put("value", maskSensitive(toJson(arg)));
            paramList.add(paramMap);
        }
        return truncate(toJson(paramList), MAX_PARAMS_LENGTH);
    }

    private boolean isFrameworkObject(Object arg) {
        return arg instanceof HttpServletRequest
                || arg instanceof HttpServletResponse
                || arg instanceof java.io.InputStream
                || arg instanceof java.io.OutputStream
                || arg.getClass().getName().startsWith("org.springframework.web.multipart")
                || arg.getClass().getName().startsWith("jakarta.servlet");
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj instanceof String) {
            return (String) obj;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }

    /**
     * 对 JSON 字符串中的敏感字段进行脱敏
     */
    private String maskSensitive(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        String result = json;
        for (String field : SENSITIVE_FIELDS) {
            result = result.replaceAll(
                    "(?i)(\"" + field + "\"\\s*[:=]\\s*\"?)([^\"&,}\\s]+)(\"?)",
                    "$1****$3");
        }
        return result;
    }

    /**
     * 摘要化响应结果
     */
    private String summarizeResult(Object result) {
        if (result == null) {
            return null;
        }
        // 响应体中同样包含 accessToken/refreshToken 等凭证，必须与请求参数一样脱敏后再落日志，
        // 否则登录接口返回的 token 会以明文写入日志库，造成凭证泄露。
        return truncate(maskSensitive(toJson(result)), MAX_RESPONSE_LENGTH);
    }

    private String buildMessage(String uri, String method, int status, boolean success) {
        return String.format("%s %s -> %s (%s)", method, uri, status, success ? "OK" : "FAILED");
    }

    private String buildFullMessage(String uri, String method, String params, String response, boolean success) {
        Map<String, Object> full = new LinkedHashMap<>();
        full.put("uri", uri);
        full.put("method", method);
        full.put("success", success);
        full.put("params", params);
        full.put("response", response);
        return truncate(toJson(full), MAX_RESPONSE_LENGTH + MAX_PARAMS_LENGTH);
    }

    private String stackTraceToString(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        sb.append(ex.toString()).append("\n");
        for (StackTraceElement ste : ex.getStackTrace()) {
            sb.append("\tat ").append(ste.toString()).append("\n");
        }
        return sb.toString();
    }

    private String truncate(String str, int maxLen) {
        if (str == null) {
            return null;
        }
        return str.length() <= maxLen ? str : str.substring(0, maxLen) + "...[truncated]";
    }
}
