package com.liang.xz.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>统一API响应体</p>
 *
 * <p>规范:</p>
 * <ul>
 *   <li>200: 操作成功</li>
 *   <li>400: 请求参数校验失败</li>
 *   <li>404: 资源不存在</li>
 *   <li>500: 服务端内部异常</li>
 * </ul>
 *
 * @param <T> 响应数据类型
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Schema(description = "统一API响应体")
public class ApiResponse<T> {

    @Schema(description = "业务状态码: 200成功 400参数错误 404不存在 500服务异常", example = "200")
    private int code;


    @Schema(description = "提示信息", example = "操作成功")
    private String message;

    @Schema(description = "响应数据")
    private T data;

    // ======================== 工厂方法 ========================

    /** 操作成功 */
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 200;
        r.message = "操作成功";
        r.data = data;
        return r;
    }

    /** 操作成功(自定义消息) */
    public static <T> ApiResponse<T> success(String message, T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 200;
        r.message = message;
        r.data = data;
        return r;
    }

    /** 操作失败 */
    public static <T> ApiResponse<T> fail(int code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
