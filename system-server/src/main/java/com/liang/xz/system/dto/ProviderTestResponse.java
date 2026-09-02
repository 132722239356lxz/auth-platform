package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * <p>供应商连通性探测结果</p>
 *
 * <p>探测失败属于业务结果而非接口错误，Controller 仍以 ApiResponse.success 返回，
 * 成败由本对象的 success 字段表达。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Schema(description = "供应商连通性探测结果")
public class ProviderTestResponse {

    @Schema(description = "探测是否成功")
    private Boolean success;

    @Schema(description = "HTTP 状态码，网络层失败时为 null")
    private Integer httpStatus;

    @Schema(description = "耗时(毫秒)")
    private Long latencyMs;

    @Schema(description = "结果说明")
    private String message;

    @Schema(description = "探测到的可用模型列表，供应商不支持模型列举时为空")
    private List<String> availableModels;

    public static ProviderTestResponse ok(Integer httpStatus, long latencyMs, String message, List<String> models) {
        ProviderTestResponse r = new ProviderTestResponse();
        r.success = true;
        r.httpStatus = httpStatus;
        r.latencyMs = latencyMs;
        r.message = message;
        r.availableModels = models;
        return r;
    }

    public static ProviderTestResponse fail(Integer httpStatus, long latencyMs, String message) {
        ProviderTestResponse r = new ProviderTestResponse();
        r.success = false;
        r.httpStatus = httpStatus;
        r.latencyMs = latencyMs;
        r.message = message;
        return r;
    }
}
