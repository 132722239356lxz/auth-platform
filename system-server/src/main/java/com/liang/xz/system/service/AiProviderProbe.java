package com.liang.xz.system.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.system.dto.ProviderTestResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <p>AI 供应商连通性探测实现</p>
 *
 * <p>本模块未引入 RestTemplate / WebClient，直接使用 JDK17 的 HttpClient，
 * 客户端为进程级共享实例，避免每次探测都新建连接池。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiProviderProbe implements IAiProviderProbe {

    /** 默认探测超时 */
    private static final int DEFAULT_TIMEOUT_MS = 10000;

    /** 探测超时下限，防止填 0 导致立即失败 */
    private static final int MIN_TIMEOUT_MS = 1000;

    /** 探测超时上限，避免管理接口被长时间挂住 */
    private static final int MAX_TIMEOUT_MS = 60000;

    /** 响应体读取上限，防止恶意 base_url 返回超大响应撑爆内存 */
    private static final int MAX_BODY_CHARS = 64 * 1024;

    /** 最多回显的模型数量 */
    private static final int MAX_MODELS = 200;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final ObjectMapper objectMapper;

    @Override
    public ProviderTestResponse probe(String providerType, String baseUrl, String apiKey, Integer timeoutMs) {
        URI uri;
        try {
            uri = buildModelsUri(baseUrl);
        } catch (IllegalArgumentException e) {
            return ProviderTestResponse.fail(null, 0L, e.getMessage());
        }

        int timeout = normalizeTimeout(timeoutMs);
        log.info("探测 AI 供应商连通性: type={}, url={}", providerType, uri);

        long start = System.currentTimeMillis();
        try {
            HttpRequest request = buildRequest(uri, providerType, apiKey, timeout);
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;
            return interpret(response, latency);
        } catch (HttpTimeoutException e) {
            return ProviderTestResponse.fail(null, System.currentTimeMillis() - start,
                    "请求超时(" + timeout + "ms)，请检查 Base URL 是否可达或调大超时时间");
        } catch (UnknownHostException e) {
            return ProviderTestResponse.fail(null, System.currentTimeMillis() - start,
                    "域名解析失败: " + e.getMessage());
        } catch (ConnectException e) {
            return ProviderTestResponse.fail(null, System.currentTimeMillis() - start,
                    "无法建立连接，请检查 Base URL、端口与网络策略");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ProviderTestResponse.fail(null, System.currentTimeMillis() - start, "探测被中断");
        } catch (Exception e) {
            log.warn("AI 供应商连通性探测失败: url={}", uri, e);
            return ProviderTestResponse.fail(null, System.currentTimeMillis() - start,
                    "探测失败: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    /**
     * 由 baseUrl 推导模型列举地址，同时做 SSRF 基本防护：只放行 http/https。
     */
    private URI buildModelsUri(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Base URL 不能为空");
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        URI uri;
        try {
            uri = new URI(normalized + "/models");
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Base URL 格式不合法: " + baseUrl);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Base URL 仅支持 http/https 协议");
        }
        if (uri.getHost() == null || uri.getHost().isEmpty()) {
            throw new IllegalArgumentException("Base URL 缺少主机名");
        }
        return uri;
    }

    private int normalizeTimeout(Integer timeoutMs) {
        if (timeoutMs == null) {
            return DEFAULT_TIMEOUT_MS;
        }
        return Math.min(Math.max(timeoutMs, MIN_TIMEOUT_MS), MAX_TIMEOUT_MS);
    }

    /**
     * 不同供应商的鉴权头写法不同，按类型分支。
     */
    private HttpRequest buildRequest(URI uri, String providerType, String apiKey, int timeoutMs) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Accept", "application/json");

        String type = providerType == null ? "" : providerType.trim().toLowerCase(Locale.ROOT);
        String key = apiKey == null ? "" : apiKey.trim();
        if (key.isEmpty()) {
            return builder.build();
        }
        switch (type) {
            case "azure":
                builder.header("api-key", key);
                break;
            case "anthropic":
                builder.header("x-api-key", key);
                builder.header("anthropic-version", "2023-06-01");
                break;
            default:
                builder.header("Authorization", "Bearer " + key);
                break;
        }
        return builder.build();
    }

    private ProviderTestResponse interpret(HttpResponse<String> response, long latency) {
        int status = response.statusCode();
        String body = truncate(response.body());
        if (status >= 200 && status < 300) {
            List<String> models = parseModels(body);
            String message = models.isEmpty()
                    ? "连接成功，但该供应商未返回模型列表"
                    : "连接成功，探测到 " + models.size() + " 个可用模型";
            return ProviderTestResponse.ok(status, latency, message, models);
        }
        return ProviderTestResponse.fail(status, latency, describeError(status, body));
    }

    private String describeError(int status, String body) {
        String detail = body == null || body.isEmpty() ? "" : "，响应: " + truncateForMessage(body);
        return switch (status) {
            case 401 -> "鉴权失败(401)，API Key 无效或已过期" + detail;
            case 403 -> "无访问权限(403)，请检查 API Key 的授权范围或 IP 白名单" + detail;
            case 404 -> "接口不存在(404)，请确认 Base URL 是否包含正确的版本路径(如 /v1)" + detail;
            case 429 -> "请求被限流(429)，请稍后重试" + detail;
            default -> status >= 500
                    ? "供应商服务端异常(" + status + ")" + detail
                    : "探测失败(HTTP " + status + ")" + detail;
        };
    }

    /**
     * 兼容 OpenAI 风格 {"data":[{"id":"..."}]} 与直接返回数组两种结构。
     */
    private List<String> parseModels(String body) {
        List<String> models = new ArrayList<>();
        if (body == null || body.isEmpty()) {
            return models;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode array = root.isArray() ? root : root.path("data");
            if (!array.isArray()) {
                array = root.path("models");
            }
            if (!array.isArray()) {
                return models;
            }
            for (JsonNode node : array) {
                if (models.size() >= MAX_MODELS) {
                    break;
                }
                String id = node.isTextual() ? node.asText() : node.path("id").asText(null);
                if (id == null || id.isEmpty()) {
                    id = node.path("name").asText(null);
                }
                if (id != null && !id.isEmpty()) {
                    models.add(id);
                }
            }
        } catch (Exception e) {
            log.debug("解析模型列表失败，按无模型处理", e);
        }
        return models;
    }

    private String truncate(String body) {
        if (body == null) {
            return null;
        }
        return body.length() > MAX_BODY_CHARS ? body.substring(0, MAX_BODY_CHARS) : body;
    }

    private String truncateForMessage(String body) {
        String oneLine = body.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 200 ? oneLine.substring(0, 200) + "..." : oneLine;
    }
}
