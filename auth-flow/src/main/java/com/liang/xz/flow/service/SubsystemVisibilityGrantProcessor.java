package com.liang.xz.flow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.common.core.model.R;
import com.liang.xz.flow.dto.PermissionApplyContent;
import com.liang.xz.flow.entity.WorkflowInstance;
import com.liang.xz.flow.enums.ApplyType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>子系统可见权限申请场景 —— 审批通过后调用 AI 决策并赋权</p>
 *
 * <p>处理顺序:
 * 1. 解析申请内容(目标用户即申请人本人、申请理由);
 * 2. 从本库 oauth2_registered_client 拉取候选子系统清单;
 * 3. 调用 ai-agent-server 的子系统可见性 AI 决策端点, 得到建议可见的 clientId 列表;
 * 4. 写入 sys_user_subsystem (可见=1), 门户据此在下一次拉取导航时生效。</p>
 *
 * <p>注意: auth-flow 与 system-server 共享 auth_platform 库, 故可直接写 sys_user_subsystem,
 * 无需跨服务调用。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubsystemVisibilityGrantProcessor implements ApplyPostApproveProcessor {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final LoadBalancerClient loadBalancerClient;

    /**
     * ai-agent-server 调用地址。
     * <p>默认走服务发现直连 ai-agent-server (由 LoadBalancerClient 解析实例, 绕过网关),
     * 避免内部调用被网关鉴权拦截(401)且链路更短。
     * 可配置为服务名(http://ai-agent-server)或服务发现直连, 也可覆盖为完整地址做本地调试,
     * 例如: ai-agent.base-url=http://localhost:9003/ai-agent-server</p>
     */
    @Value("${ai-agent.base-url:http://ai-agent-server}")
    private String aiAgentBaseUrl;

    /**
     * 解析真实请求地址: 若配置为服务名(http://<serviceId>)则经 LoadBalancerClient 发现实例,
     * 否则当完整地址(如 http://127.0.0.1:9003/ai-agent-server)直接使用。
     */
    private String resolveBaseUrl() {
        if (aiAgentBaseUrl.startsWith("http://") || aiAgentBaseUrl.startsWith("https://")) {
            URI uri = URI.create(aiAgentBaseUrl);
            String host = uri.getHost();
            // 服务名为 Nacos 注册的服务ID(非 IP), 走负载均衡解析
            if (host != null && !host.matches("^\\d{1,3}(\\.\\d{1,3}){3}$") && !host.equals("localhost")) {
                ServiceInstance instance = loadBalancerClient.choose(host);
                if (instance == null) {
                    throw new com.liang.xz.flow.exception.GrantFailedException(
                            String.format("未找到 ai-agent-server 可用实例, 无法完成子系统可见赋权: baseUrl=%s",
                                    aiAgentBaseUrl));
                }
                String scheme = instance.getScheme() != null ? instance.getScheme() : "http";
                return String.format("%s://%s:%d%s", scheme, instance.getHost(), instance.getPort(),
                        uri.getPath() == null ? "" : uri.getPath());
            }
        }
        return aiAgentBaseUrl;
    }

    private RestClient restClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        return RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public ApplyType supportedType() {
        return ApplyType.SUBSYSTEM_VISIBILITY;
    }

    @Override
    public void onApproved(WorkflowInstance instance) {
        PermissionApplyContent content;
        try {
            content = objectMapper.readValue(instance.getApplyContent(), PermissionApplyContent.class);
        } catch (Exception e) {
            log.error("[SubsystemVisibilityGrant] applyContent 解析失败, 跳过: instanceId={}", instance.getId(), e);
            return;
        }
        Long userId = content.getTargetUserId();
        if (userId == null) {
            userId = resolveUserIdByName(content.getTargetUsername());
        }
        if (userId == null) {
            throw new com.liang.xz.flow.exception.GrantFailedException(
                    String.format("目标用户不存在, 无法完成子系统可见赋权: instanceId=%d, content=%s",
                            instance.getId(), instance.getApplyContent()));
        }
        String reason = content.getRemark() == null ? "" : content.getRemark();
        String username = content.getTargetUsername();

        List<Map<String, String>> candidates = loadCandidates();
        List<String> visibleClientIds = decide(userId, username, reason, candidates);
        if (visibleClientIds.isEmpty()) {
            log.warn("[SubsystemVisibilityGrant] AI 未给出可见子系统, 审批通过但不写库: instanceId={}, userId={}",
                    instance.getId(), userId);
            return;
        }
        grantSubsystems(userId, visibleClientIds);
        log.info("[SubsystemVisibilityGrant] 审批通过已赋权可见子系统: instanceId={}, userId={}, clients={}",
                instance.getId(), userId, visibleClientIds);
    }

    /**
     * 解析目标用户ID(按用户名兜底)。
     */
    private Long resolveUserIdByName(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM sys_user WHERE username = ?", Long.class, username);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * 拉取候选子系统清单(client_id/名称/描述)。
     */
    private List<Map<String, String>> loadCandidates() {
        String sql = "SELECT client_id, client_name FROM oauth2_registered_client";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, String> m = new java.util.HashMap<>(3);
            m.put("clientId", rs.getString("client_id"));
            m.put("name", rs.getString("client_name"));
            m.put("description", rs.getString("client_name"));
            return m;
        });
    }

    /**
     * 调用 ai-agent-server 决策端点, 返回建议可见的 clientId 列表。
     */
    private List<String> decide(Long userId, String username, String reason,
                                List<Map<String, String>> candidates) {
        Map<String, Object> body = new java.util.HashMap<>(4);
        body.put("userId", userId);
        body.put("username", username);
        body.put("reason", reason);
        body.put("candidates", candidates);
        String baseUrl = resolveBaseUrl();
        String url = baseUrl.replaceAll("/+$", "") + "/api/ai/agent/tool/subsystem-visibility";
        log.info("[SubsystemVisibilityGrant] 调用 AI 决策端点: url={}", url);
        try {
            R<DecisionResponse> resp = restClient().post()
                    .uri(url)
                    .header("X-Internal-Call", "auth-flow")
                    .body(body)
                    .retrieve()
                    .onStatus(status -> status.isError(),
                            (req, res) -> {
                                String respBody = readErrorBody(res);
                                log.error("[SubsystemVisibilityGrant] AI 决策端点返回错误: url={}, status={}, body={}",
                                        url, res.getStatusCode(), respBody);
                                throw new com.liang.xz.flow.exception.GrantFailedException(
                                        String.format("AI 决策端点返回错误状态码: %d, userId=%s, url=%s, resp=%s",
                                                res.getStatusCode().value(), userId, url, respBody));
                            }).body(new ParameterizedTypeReference<R<DecisionResponse>>() {});
            if (resp == null || resp.getCode() != 200 || resp.getData() == null
                    || resp.getData().visibleClientIds == null) {
                // AI 未给出有效可见结果视为赋权失败, 向上抛出以触发 @Transactional 回滚,
                // 避免出现"审批通过但子系统可见未开通"的不一致状态
                log.error("[SubsystemVisibilityGrant] AI 决策未返回有效结果: url={}, resp={}", url, resp);
                throw new com.liang.xz.flow.exception.GrantFailedException(
                        String.format("AI 决策未返回有效可见子系统结果, 无法完成赋权: userId=%s, url=%s, resp=%s",
                                userId, url, resp));
            }
            return resp.getData().visibleClientIds;
        } catch (com.liang.xz.flow.exception.GrantFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new com.liang.xz.flow.exception.GrantFailedException(
                    String.format("调用 AI 决策端点失败, 无法完成子系统可见赋权: userId=%s, url=%s",
                            userId, url), e);
        }
    }

    /**
     * 安全读取错误响应体, 便于排查(避免直接 toString 抛异常掩盖原始错误)。
     */
    private String readErrorBody(org.springframework.http.client.ClientHttpResponse res) {
        try {
            return new String(res.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "<无法读取响应体: " + e.getMessage() + ">";
        }
    }

    /**
     * 写 sys_user_subsystem(去重 + 可见=1)。
     */
    private void grantSubsystems(Long userId, List<String> clientIds) {
        Set<String> dedup = new LinkedHashSet<>(clientIds);
        for (String clientId : dedup) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM sys_user_subsystem WHERE user_id = ? AND client_id = ?",
                    Integer.class, userId, clientId);
            if (exists != null && exists > 0) {
                jdbcTemplate.update(
                        "UPDATE sys_user_subsystem SET visible = 1 WHERE user_id = ? AND client_id = ?",
                        userId, clientId);
            } else {
                jdbcTemplate.update(
                        "INSERT INTO sys_user_subsystem (user_id, client_id, visible, granted_time) "
                                + "VALUES (?, ?, 1, NOW())", userId, clientId);
            }
        }
    }

    /** AI 决策端点返回结构(与 ai-agent-server SubsystemDecisionController 对齐) */
    public static class DecisionResponse {
        public List<String> visibleClientIds = new ArrayList<>();
        public String rationale;
    }
}
