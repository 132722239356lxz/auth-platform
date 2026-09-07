package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * <p>AI 调用记录响应</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Schema(description = "AI 调用记录")
public class AiInvokeLogResponse {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "会话ID")
    private String sessionId;

    @Schema(description = "用户ID")
    private String userId;

    /**
     * 用户姓名（关联 sys_user.real_name/username/phone 后得到，找不到时与 userId 一致）
     */
    @Schema(description = "用户姓名")
    private String userName;

    @Schema(description = "实际使用的供应商编码")
    private String providerCode;

    @Schema(description = "供应商名称")
    private String providerName;

    @Schema(description = "供应商类型(openai/azure/ollama等)")
    private String providerType;

    @Schema(description = "实际使用的模型")
    private String modelName;

    @Schema(description = "复杂度路由结果(simple/normal/complex)")
    private String complexity;

    @Schema(description = "是否命中缓存")
    private Boolean cached;

    @Schema(description = "输入 token 数")
    private Integer promptTokens;

    @Schema(description = "输出 token 数")
    private Integer completionTokens;

    @Schema(description = "总 token 数")
    private Integer totalTokens;

    @Schema(description = "使用的工具名列表")
    private java.util.List<String> toolNames;

    @Schema(description = "工具数量")
    private Integer toolCount;

    @Schema(description = "RAG 引用片段列表")
    private java.util.List<String> ragReferences;

    @Schema(description = "上下文内容(超长截断)")
    private String contextContent;

    @Schema(description = "用户输入内容")
    private String userInput;

    @Schema(description = "AI 输出内容(超长截断)")
    private String aiOutput;

    @Schema(description = "调用耗时(毫秒)")
    private Integer elapsedMs;

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "错误信息(失败时使用)")
    private String errorMsg;

    @Schema(description = "调用时间")
    private LocalDateTime invokeTime;

    @Schema(description = "记录创建时间")
    private LocalDateTime createTime;
}
