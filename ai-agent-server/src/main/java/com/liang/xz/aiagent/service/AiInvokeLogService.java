package com.liang.xz.aiagent.service;

import com.liang.xz.aiagent.config.AiProperties;
import com.liang.xz.aiagent.llm.ProviderFailoverService;
import com.liang.xz.aiagent.repository.AiInvokeLogRepository;
import com.liang.xz.aiagent.repository.AiInvokeLogRepository.AiInvokeLogParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * <p>AI 调用日志统一服务</p>
 *
 * <p>封装各类模型调用（LLM 生成 / Embedding / 重排序 / 联网搜索）的落库逻辑，
 * 与 system-server 共用 {@code sys_ai_invoke_log} 表，供「AI 调用日志」页面可视化观测。</p>
 *
 * <p>本服务只做「尽力记录」：任何异常均吞掉并打 warn，绝不因日志失败影响主业务。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class AiInvokeLogService {

    private final AiInvokeLogRepository repository;
    private final ProviderFailoverService providerFailoverService;
    private final AiProperties aiProperties;

    public AiInvokeLogService(AiInvokeLogRepository repository,
                              ProviderFailoverService providerFailoverService,
                              AiProperties aiProperties) {
        this.repository = repository;
        this.providerFailoverService = providerFailoverService;
        this.aiProperties = aiProperties;
    }

    /** 当前主供应商的编码/名称（用于日志展示） */
    private String providerCode() {
        try {
            var p = providerFailoverService.getPrimaryProvider();
            return p != null ? p.getProviderCode() : "UNKNOWN";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private String providerName() {
        try {
            var p = providerFailoverService.getPrimaryProvider();
            return p != null ? p.getProviderName() : "未知";
        } catch (Exception e) {
            return "未知";
        }
    }

    private String modelName() {
        try {
            var p = providerFailoverService.getPrimaryProvider();
            return p != null ? p.getDefaultModel() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 记录 LLM 生成类调用（RAG 智能回答、Agent 对话等）。
     */
    public void logLlm(String sessionId, String userId, String complexity,
                       boolean cached, Integer promptTokens, Integer completionTokens,
                       String toolNames, int toolCount, String ragReferences,
                       String userInput, String aiOutput, long elapsedMs,
                       boolean success, String errorMsg) {
        AiInvokeLogParams p = new AiInvokeLogParams();
        p.sessionId = sessionId;
        p.userId = userId;
        p.providerCode = providerCode();
        p.providerName = providerName();
        p.providerType = "LLM";
        p.modelName = modelName();
        p.complexity = complexity;
        p.cached = cached;
        p.promptTokens = promptTokens;
        p.completionTokens = completionTokens;
        p.totalTokens = (promptTokens != null && completionTokens != null)
                ? promptTokens + completionTokens : null;
        p.toolNames = toolNames;
        p.toolCount = toolCount;
        p.ragReferences = ragReferences;
        p.userInput = truncate(userInput, 4000);
        p.aiOutput = truncate(aiOutput, 4000);
        p.elapsedMs = (int) elapsedMs;
        p.success = success;
        p.errorMsg = truncate(errorMsg, 1000);
        p.invokeTime = LocalDateTime.now();
        save(p);
    }

    /**
     * 记录 Embedding 调用（语义缓存命中时 cached=true）。
     */
    public void logEmbedding(String userId, boolean cached, int tokens,
                             String userInput, long elapsedMs, boolean success, String errorMsg) {
        AiInvokeLogParams p = new AiInvokeLogParams();
        p.sessionId = "EMBEDDING";
        p.userId = userId;
        p.providerCode = providerCode();
        p.providerName = providerName();
        p.providerType = "EMBEDDING";
        p.modelName = modelName();
        p.complexity = null;
        p.cached = cached;
        p.promptTokens = tokens;
        p.totalTokens = tokens;
        p.userInput = truncate(userInput, 1000);
        p.aiOutput = null;
        p.elapsedMs = (int) elapsedMs;
        p.success = success;
        p.errorMsg = truncate(errorMsg, 1000);
        p.invokeTime = LocalDateTime.now();
        save(p);
    }

    /**
     * 记录重排序（Rerank）调用。
     */
    public void logRerank(String userId, String model, boolean used,
                          String query, long elapsedMs, boolean success, String errorMsg) {
        AiInvokeLogParams p = new AiInvokeLogParams();
        p.sessionId = "RERANK";
        p.userId = userId;
        p.providerCode = "SEARCHPIN-RERANK";
        p.providerName = "重排序模型";
        p.providerType = "RERANK";
        p.modelName = model;
        p.complexity = null;
        p.cached = !used; // 未真正调用重排序（降级）记为「命中缓存/免调用」
        p.userInput = truncate(query, 1000);
        p.elapsedMs = (int) elapsedMs;
        p.success = success;
        p.errorMsg = truncate(errorMsg, 1000);
        p.invokeTime = LocalDateTime.now();
        save(p);
    }

    /**
     * 记录联网搜索（Searchpin）调用。
     */
    public void logSearch(String userId, String engine, String query, int resultCount,
                          long elapsedMs, boolean success, String errorMsg) {
        AiInvokeLogParams p = new AiInvokeLogParams();
        p.sessionId = "WEB_SEARCH";
        p.userId = userId;
        p.providerCode = "SEARCHPIN";
        p.providerName = "联网搜索-" + (engine == null ? "Searchpin" : engine);
        p.providerType = "SEARCH";
        p.modelName = "searchpin";
        p.complexity = null;
        p.cached = false;
        p.userInput = truncate(query, 1000);
        p.aiOutput = "结果数=" + resultCount;
        p.elapsedMs = (int) elapsedMs;
        p.success = success;
        p.errorMsg = truncate(errorMsg, 1000);
        p.invokeTime = LocalDateTime.now();
        save(p);
    }

    private void save(AiInvokeLogParams params) {
        try {
            repository.insert(params);
        } catch (Exception e) {
            log.warn("[AiInvokeLog] 写入 AI 调用日志失败（已忽略）：{}", e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
