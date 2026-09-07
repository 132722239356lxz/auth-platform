package com.liang.xz.aiagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.aiagent.llm.LlmClient;
import com.liang.xz.common.core.annotation.PublicApi;
import com.liang.xz.common.core.model.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>子系统可见性 AI 决策工具（内部端点）</p>
 *
 * <p>审批流通过审批后由 auth-flow 调用本端点：根据申请人填写的目标用户、申请理由，
 * 以及候选子系统清单，由 LLM 推理出"应当对该用户可见的子系统 clientId 列表"。</p>
 *
 * <p>该端点仅做决策，不直接写库；写库职责由 auth-flow 的赋权处理器完成，保证各模块边界清晰。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@PublicApi
@Tag(name = "子系统可见性AI决策", description = "审批通过后调用：根据申请信息推理应可见的子系统清单")
@RestController
@RequestMapping("/api/ai/agent/tool")
public class SubsystemDecisionController {

    private static final Pattern CLIENT_ID_PATTERN = Pattern.compile("\"clientId\"\\s*:\\s*\"([^\"]+)\"");

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public SubsystemDecisionController(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 决策请求。
     */
    public static class SubsystemDecisionRequest {
        /** 目标用户ID（申请人本人） */
        public Long userId;
        /** 目标用户名 */
        public String username;
        /** 申请理由 */
        public String reason;
        /** 候选子系统清单：由 auth-flow 从 system-server 拉取后传入 */
        public List<SubsystemCandidate> candidates;

        public static class SubsystemCandidate {
            public String clientId;
            public String name;
            public String description;
        }
    }

    /**
     * 决策响应。
     */
    public static class SubsystemDecisionResponse {
        /** LLM 建议可见的 clientId 列表 */
        public List<String> visibleClientIds = new ArrayList<>();
        /** LLM 给出的决策说明 */
        public String rationale;
    }

    @Operation(summary = "根据申请信息决策应可见的子系统")
    @PostMapping("/subsystem-visibility")
    @PublicApi
    public R<SubsystemDecisionResponse> decideSubsystemVisibility(@RequestBody SubsystemDecisionRequest request) {
        SubsystemDecisionResponse response = new SubsystemDecisionResponse();
        try {
            String systemPrompt = buildSystemPrompt(request);
            String userMessage = buildUserMessage(request);
            String raw = llmClient.chat(systemPrompt, userMessage);
            if (raw == null || raw.startsWith("[LLM")) {
                log.warn("[子系统可见性AI决策] LLM 调用失败或未配置，返回空结果。raw={}", raw);
                return R.ok(response);
            }
            extractResult(raw, response);
            log.info("[子系统可见性AI决策] userId={} 建议可见={} 说明={}",
                    request.userId, response.visibleClientIds, response.rationale);
            return R.ok(response);
        } catch (Exception e) {
            log.error("[子系统可见性AI决策] 决策异常 userId={}", request.userId, e);
            return R.ok(response);
        }
    }

    private String buildSystemPrompt(SubsystemDecisionRequest request) {
        return "你是一个企业权限分配助手。需要根据用户的角色、部门以及申请理由，"
                + "从给定的候选子系统清单中，判断哪些子系统应当对该用户可见（开通可见权限）。\n"
                + "判断原则：\n"
                + "1. 与用户职责、部门业务强相关的子系统应可见；\n"
                + "2. 申请理由中明确要求或隐含需要的子系统应可见；\n"
                + "3. 与用户职责无关、且理由未提及的通用管理后台（如系统管理、网关管理）默认不可见，除非理由充分；\n"
                + "4. 不确定时保守处理，少开而非多开。\n"
                + "请只输出一个 JSON，格式："
                + "{\"visibleClientIds\":[\"clientId1\",\"clientId2\"],\"rationale\":\"一句话说明决策依据\"}。"
                + "不要输出任何额外文字、markdown 代码块标记。";
    }

    private String buildUserMessage(SubsystemDecisionRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("目标用户：").append(request.username).append("(id=").append(request.userId).append(")\n");
        sb.append("申请理由：").append(request.reason == null ? "" : request.reason).append("\n");
        sb.append("候选子系统清单（clientId | 名称 | 说明）：\n");
        if (request.candidates != null) {
            for (SubsystemDecisionRequest.SubsystemCandidate c : request.candidates) {
                sb.append("- ").append(c.clientId).append(" | ").append(c.name == null ? "" : c.name)
                        .append(" | ").append(c.description == null ? "" : c.description).append("\n");
            }
        }
        sb.append("请返回应当对该用户可见的 clientId 列表。");
        return sb.toString();
    }

    private void extractResult(String raw, SubsystemDecisionResponse response) {
        try {
            String json = raw.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```[a-zA-Z]*", "").replaceAll("```$", "").trim();
            }
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(json);
            com.fasterxml.jackson.databind.JsonNode ids = node.get("visibleClientIds");
            if (ids != null && ids.isArray()) {
                Set<String> dedup = new LinkedHashSet<>();
                for (com.fasterxml.jackson.databind.JsonNode id : ids) {
                    if (!id.isNull()) {
                        dedup.add(id.asText());
                    }
                }
                response.visibleClientIds = new ArrayList<>(dedup);
            }
            com.fasterxml.jackson.databind.JsonNode rationale = node.get("rationale");
            if (rationale != null && !rationale.isNull()) {
                response.rationale = rationale.asText();
            }
        } catch (Exception e) {
            // LLM 未严格返回 JSON，退化为正则提取 clientId
            log.warn("[子系统可见性AI决策] 解析JSON失败，尝试正则提取。raw={}", raw);
            Matcher m = CLIENT_ID_PATTERN.matcher(raw);
            Set<String> dedup = new LinkedHashSet<>();
            while (m.find()) {
                dedup.add(m.group(1));
            }
            response.visibleClientIds = new ArrayList<>(dedup);
            response.rationale = "AI 返回格式异常，已按 clientId 提取";
        }
    }
}
