package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>统一API响应体(与auth-server保持一致)</p>
 *
 * @param <T> 响应数据类型
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Schema(description = "统一API响应体")
public class ApiResponse<T> {

    @Schema(description = "业务状态码: 200成功 400参数错误 404不存在 500服务异常")
    private int code;

    @Schema(description = "提示信息")
    private String message;

    @Schema(description = "响应数据")
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 200;
        r.message = "操作成功";
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 200;
        r.message = message;
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
