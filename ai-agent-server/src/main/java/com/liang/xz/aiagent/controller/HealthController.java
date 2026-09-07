package com.liang.xz.aiagent.controller;

import com.liang.xz.aiagent.llm.LlmClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * <p>AI 健康检查 & 工具 API</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Tag(name = "AI健康检查", description = "LLM 连通性测试 / 服务状态")
@RestController
@RequestMapping("/api/ai/health")
public class HealthController {

    private final LlmClient llmClient;

    public HealthController(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Operation(summary = "LLM 连通性检查")
    @GetMapping("/llm")
    public Map<String, Object> checkLlm() {
        boolean ok = llmClient.healthCheck();
        return Map.of("success", ok, "status", ok ? "CONNECTED" : "DISCONNECTED");
    }

    @Operation(summary = "服务状态概览")
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "service", "ai-agent-server",
                "status", "UP",
                "features", Map.of(
                        "rag", true,
                        "localSearch", true,
                        "webSearch", true,
                        "analysisAgent", true,
                        "scheduledAnalysis", true
                )
        );
    }
}
