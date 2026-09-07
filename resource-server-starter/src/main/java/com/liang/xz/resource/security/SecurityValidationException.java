package com.liang.xz.resource.security;

/**
 * <p>安全验证异常 —— Token验证过滤器专用异常</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public class SecurityValidationException extends RuntimeException {

    private final int httpStatus;

    public SecurityValidationException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
