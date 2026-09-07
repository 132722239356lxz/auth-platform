package com.liang.xz.resource.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>全局安全异常处理器 —— 统一处理认证和授权异常，返回标准JSON响应</p>
 *
 * <p>处理的异常类型:</p>
 * <ul>
 *   <li>AccessDeniedException: 权限不足(由 @RequirePermission 注解触发)</li>
 *   <li>AuthenticationException: 认证失败(Token无效/过期等)</li>
 *   <li>Exception: 其他未捕获异常</li>
 * </ul>
 *
 * <p>响应格式:</p>
 * <pre>
 * {
 *   "code": 403,
 *   "message": "权限不足: xxx",
 *   "data": null
 * }
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalSecurityExceptionHandler {

    /**
     * 权限不足异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleAccessDenied(AccessDeniedException e) {
        log.warn("[Security] 权限不足: {}", e.getMessage());
        return errorResponse(403, "权限不足: " + e.getMessage());
    }

    /**
     * 认证失败异常
     */
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> handleAuthentication(org.springframework.security.core.AuthenticationException e) {
        log.warn("[Security] 认证失败: {}", e.getMessage());
        return errorResponse(401, "认证失败，请先登录");
    }

    /**
     * 文件上传异常（非 multipart 请求、超过大小限制等）
     */
    @ExceptionHandler(MultipartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleMultipartException(MultipartException e) {
        if (e instanceof MaxUploadSizeExceededException) {
            log.warn("[Security] 文件上传超过大小限制: {}", e.getMessage());
            return errorResponse(400, "文件大小超过限制，单文件最大50MB");
        }
        log.warn("[Security] 文件上传格式错误: {}", e.getMessage());
        return errorResponse(400, "文件上传格式错误，请使用 multipart/form-data 格式上传");
    }

    /**
     * 请求 Content-Type 不支持（如用 application/json 调用只接受 multipart/form-data 的接口）
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public Map<String, Object> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        log.warn("[Security] 不支持的 Content-Type: {}", e.getContentType());
        return errorResponse(415, "不支持的请求格式: " + e.getContentType()
                + "，支持的格式: " + e.getSupportedMediaTypes());
    }

    /**
     * 其他未捕获异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleException(Exception e) {
        log.error("[Security] 系统异常: ", e);
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        return errorResponse(500, "系统异常: " + msg);
    }

    /**
     * 构造统一错误响应
     * <p>使用 HashMap 而非 Map.of()，因为 Map.of() 不允许 null value</p>
     */
    private Map<String, Object> errorResponse(int code, String message) {
        Map<String, Object> result = new HashMap<>(3);
        result.put("code", code);
        result.put("message", message);
        result.put("data", null);
        return result;
    }
}
