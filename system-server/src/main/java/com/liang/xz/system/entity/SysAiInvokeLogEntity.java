package com.liang.xz.system.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>AI 调用记录实体</p>
 *
 * <p>用于排查 AI 生成是否合理，记录每次调用的 token 用量、工具使用、RAG 引用、
 * 上下文与用户输入/输出等内容。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysAiInvokeLogEntity {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "会话ID")
    private String sessionId;

    @Schema(description = "用户ID")
    private String userId;

    /**
     * 关联 sys_user 得到的姓名（real_name 优先，缺则 username）。
     * 来自 LEFT JOIN sys_user，不是持久化字段，列表查询时由 SQL 填充。
     */
    @Schema(description = "用户姓名（关联 sys_user 得出）")
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

    @Schema(description = "是否命中缓存: 0=否 1=是")
    private Boolean cached;

    @Schema(description = "输入 token 数")
    private Integer promptTokens;

    @Schema(description = "输出 token 数")
    private Integer completionTokens;

    @Schema(description = "总 token 数")
    private Integer totalTokens;

    @Schema(description = "使用的工具名(JSON 数组)")
    private String toolNames;

    @Schema(description = "工具数量")
    private Integer toolCount;

    @Schema(description = "RAG 引用片段(JSON 数组)")
    private String ragReferences;

    @Schema(description = "上下文内容(系统提示/检索上下文摘要, 超长截断)")
    private String contextContent;

    @Schema(description = "用户输入内容")
    private String userInput;

    @Schema(description = "AI 输出内容(超长截断)")
    private String aiOutput;

    @Schema(description = "调用耗时(毫秒)")
    private Integer elapsedMs;

    @Schema(description = "是否成功: 0=失败 1=成功")
    private Boolean success;

    @Schema(description = "错误信息(失败时使用)")
    private String errorMsg;

    @Schema(description = "调用时间")
    private LocalDateTime invokeTime;

    @Schema(description = "记录创建时间")
    private LocalDateTime createTime;
}
