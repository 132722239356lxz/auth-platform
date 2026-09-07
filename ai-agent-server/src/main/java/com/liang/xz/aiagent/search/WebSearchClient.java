package com.liang.xz.aiagent.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.aiagent.config.AiProperties;
import com.liang.xz.aiagent.service.AiInvokeLogService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p>联网搜索客户端 — 整合 Brave / SerpAPI / Bing 等提供商的网页搜索</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
public class WebSearchClient {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AiProperties.WebSearchConfig config;
    private final AiInvokeLogService aiInvokeLogService;

    public WebSearchClient(AiProperties aiProperties, ObjectMapper objectMapper,
                            AiInvokeLogService aiInvokeLogService) {
        this.config = aiProperties.getWebSearch();
        this.objectMapper = objectMapper;
        this.aiInvokeLogService = aiInvokeLogService;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.getTimeout(), TimeUnit.SECONDS)
                .readTimeout(config.getTimeout(), TimeUnit.SECONDS)
                .build();
    }

    /**
     * 统一搜索入口，根据 provider 路由
     */
    public List<WebSearchResult> search(String query) {
        return search(query, config.getMaxResults());
    }

    public List<WebSearchResult> search(String query, int maxResults) {
        return switch (config.getProvider().toUpperCase()) {
            case "BRAVE"  -> braveSearch(query, maxResults);
            case "SERPAPI" -> serpApiSearch(query, maxResults);
            case "BING"   -> bingSearch(query, maxResults);
            case "SEARCHPIN" -> searchpinSearch(query, maxResults);
            default -> {
                log.warn("Unknown search provider: {}, fallback to Brave", config.getProvider());
                yield braveSearch(query, maxResults);
            }
        };
    }

    // ==================== Brave Search ====================

    private List<WebSearchResult> braveSearch(String query, int maxResults) {
        try {
            String url = config.getEndpoints().getOrDefault("BRAVE",
                    "https://api.search.brave.com/res/v1/web/search");
            HttpUrl httpUrl = HttpUrl.parse(url).newBuilder()
                    .addQueryParameter("q", query)
                    .addQueryParameter("count", String.valueOf(maxResults))
                    .build();

            Request request = new Request.Builder()
                    .url(httpUrl)
                    .header("Accept", "application/json")
                    .header("Accept-Encoding", "gzip")
                    .header("X-Subscription-Token", config.getApiKey())
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.error("Brave Search error: {} {}", response.code(), body);
                    return List.of();
                }
                return parseBraveResults(body);
            }
        } catch (IOException e) {
            log.error("Brave Search failed", e);
            return List.of();
        }
    }

    private List<WebSearchResult> parseBraveResults(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        List<WebSearchResult> results = new ArrayList<>();

        JsonNode web = root.path("web").path("results");
        if (web.isArray()) {
            for (JsonNode item : web) {
                results.add(WebSearchResult.builder()
                        .title(item.path("title").asText())
                        .url(item.path("url").asText())
                        .snippet(item.path("description").asText(""))
                        .source("BRAVE")
                        .build());
            }
        }
        return results;
    }

    // ==================== SerpAPI (Google) ====================

    private List<WebSearchResult> serpApiSearch(String query, int maxResults) {
        try {
            String url = config.getEndpoints().getOrDefault("SERPAPI",
                    "https://serpapi.com/search");
            HttpUrl httpUrl = HttpUrl.parse(url).newBuilder()
                    .addQueryParameter("q", query)
                    .addQueryParameter("api_key", config.getApiKey())
                    .addQueryParameter("engine", "google")
                    .addQueryParameter("num", String.valueOf(maxResults))
                    .build();

            Request request = new Request.Builder().url(httpUrl).build();

            try (Response response = httpClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.error("SerpAPI error: {} {}", response.code(), body);
                    return List.of();
                }
                return parseSerpApiResults(body);
            }
        } catch (IOException e) {
            log.error("SerpAPI search failed", e);
            return List.of();
        }
    }

    private List<WebSearchResult> parseSerpApiResults(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        List<WebSearchResult> results = new ArrayList<>();
        JsonNode organic = root.path("organic_results");
        if (organic.isArray()) {
            for (JsonNode item : organic) {
                results.add(WebSearchResult.builder()
                        .title(item.path("title").asText())
                        .url(item.path("link").asText())
                        .snippet(item.path("snippet").asText(""))
                        .source("SERPAPI")
                        .build());
            }
        }
        return results;
    }

    // ==================== Bing Search ====================

    private List<WebSearchResult> bingSearch(String query, int maxResults) {
        try {
            String url = config.getEndpoints().getOrDefault("BING",
                    "https://api.bing.microsoft.com/v7.0/search");
            HttpUrl httpUrl = HttpUrl.parse(url).newBuilder()
                    .addQueryParameter("q", query)
                    .addQueryParameter("count", String.valueOf(maxResults))
                    .addQueryParameter("mkt", "zh-CN")
                    .build();

            Request request = new Request.Builder()
                    .url(httpUrl)
                    .header("Ocp-Apim-Subscription-Key", config.getApiKey())
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.error("Bing Search error: {} {}", response.code(), body);
                    return List.of();
                }
                return parseBingResults(body);
            }
        } catch (IOException e) {
            log.error("Bing Search failed", e);
            return List.of();
        }
    }

    private List<WebSearchResult> parseBingResults(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        List<WebSearchResult> results = new ArrayList<>();
        JsonNode pages = root.path("webPages").path("value");
        if (pages.isArray()) {
            for (JsonNode item : pages) {
                results.add(WebSearchResult.builder()
                        .title(item.path("name").asText())
                        .url(item.path("url").asText())
                        .snippet(item.path("snippet").asText(""))
                        .source("BING")
                        .build());
            }
        }
        return results;
    }

    // ==================== Searchpin (MCP 自托管联网搜索) ====================

    /**
     * 通过 Searchpin（stdio MCP server）执行联网搜索：零 API Key，并行查询多引擎并语义重排。
     * 失败（未安装/启动超时/解析异常）时返回空列表，不阻断调用方。
     */
    private List<WebSearchResult> searchpinSearch(String query, int maxResults) {
        if (!config.isSearchpinEnabled()) {
            log.warn("Searchpin 未启用（searchpinEnabled=false）");
            return List.of();
        }
        int limit = Math.min(maxResults > 0 ? maxResults : config.getSearchpinMaxResults(),
                config.getSearchpinMaxResults());
        long start = System.currentTimeMillis();
        try (SearchpinMcpClient client = new SearchpinMcpClient(config.getSearchpinCommand(),
                config.getSearchpinTimeout(), objectMapper)) {
            List<SearchpinResult> raw = client.webSearch(query, limit);
            List<WebSearchResult> results = new ArrayList<>(raw.size());
            for (SearchpinResult r : raw) {
                if (r.url == null || r.url.isBlank()) {
                    continue;
                }
                results.add(WebSearchResult.builder()
                        .title(r.title == null ? r.url : r.title)
                        .url(r.url)
                        .snippet(r.snippet == null ? "" : r.snippet)
                        .source("SEARCHPIN")
                        .build());
            }
            aiInvokeLogService.logSearch(null, "Searchpin", query, results.size(),
                    System.currentTimeMillis() - start, true, null);
            return results;
        } catch (IOException e) {
            log.warn("[Searchpin] 联网搜索失败：{}", e.getMessage());
            aiInvokeLogService.logSearch(null, "Searchpin", query, 0,
                    System.currentTimeMillis() - start, false, e.getMessage());
            return List.of();
        }
    }

    /**
     * 极简 MCP（stdio）客户端：以子进程方式启动 searchpin-server，进行 JSON-RPC 握手并调用
     * {@code web_search} 工具。不依赖任何 MCP SDK，仅使用 JDK 标准库。
     */
    private static class SearchpinMcpClient implements AutoCloseable {
        private static final int ID_INIT = 1;
        private static final int ID_CALL = 2;

        private final Process process;
        private final BufferedReader reader;
        private final OutputStream stdin;
        private final ObjectMapper mapper;
        private final int timeoutSec;

        SearchpinMcpClient(String command, int timeoutSec, ObjectMapper mapper) throws IOException {
            this.timeoutSec = timeoutSec;
            this.mapper = mapper;
            ProcessBuilder pb = new ProcessBuilder(command.split("\\s+"));
            pb.redirectErrorStream(false);
            this.process = pb.start();
            this.reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8));
            this.stdin = process.getOutputStream();
        }

        List<SearchpinResult> webSearch(String query, int maxResults) throws IOException {
            // 1) initialize
            send(Map.of("jsonrpc", "2.0", "id", ID_INIT, "method", "initialize",
                    "params", Map.of("protocolVersion", "2024-11-05",
                            "capabilities", Map.of(), "clientInfo", Map.of("name", "ai-agent", "version", "1.0"))));
            // 2) initialized 通知（无 id）
            send(Map.of("jsonrpc", "2.0", "method", "notifications/initialized"));
            // 3) tools/call: web_search
            Map<String, Object> args = new java.util.LinkedHashMap<>();
            args.put("query", query);
            if (maxResults > 0) {
                args.put("max_results", maxResults);
            }
            send(Map.of("jsonrpc", "2.0", "id", ID_CALL, "method", "tools/call",
                    "params", Map.of("name", "web_search", "arguments", args)));

            // 读取响应，按 id 匹配；同时忽略 initialize 响应与服务器主动推送
            CompletableFuture<String> callFuture = new CompletableFuture<>();
            AtomicReference<String> callResultRef = new AtomicReference<>();
            Thread readerThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank()) {
                            continue;
                        }
                        JsonNode msg = mapper.readTree(line);
                        JsonNode idNode = msg.get("id");
                        if (idNode != null && idNode.asInt() == ID_CALL) {
                            JsonNode result = msg.get("result");
                            if (result != null) {
                                callResultRef.set(result.toString());
                            }
                            callFuture.complete("done");
                            return;
                        }
                    }
                    callFuture.complete("eof");
                } catch (Exception e) {
                    callFuture.completeExceptionally(e);
                }
            }, "searchpin-mcp-reader");
            readerThread.setDaemon(true);
            readerThread.start();

            try {
                callFuture.get(timeoutSec, TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                throw new IOException("Searchpin 调用超时（" + timeoutSec + "s），可能首次运行需下载重排模型");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Searchpin 调用被中断");
            } catch (Exception e) {
                throw new IOException("Searchpin 读取响应失败：" + e.getMessage());
            }

            String resultJson = callResultRef.get();
            if (resultJson == null) {
                return List.of();
            }
            return parseSearchpinResult(resultJson);
        }

        private List<SearchpinResult> parseSearchpinResult(String resultJson) throws IOException {
            JsonNode result = mapper.readTree(resultJson);
            JsonNode content = result.get("content");
            if (content == null || !content.isArray() || content.isEmpty()) {
                return List.of();
            }
            // content[].text 为 JSON 字符串：{"results":[{title,url,snippet,...}]}
            String text = content.get(0).path("text").asText();
            if (text == null || text.isBlank()) {
                return List.of();
            }
            JsonNode payload = mapper.readTree(text);
            JsonNode results = payload.get("results");
            List<SearchpinResult> list = new ArrayList<>();
            if (results != null && results.isArray()) {
                for (JsonNode item : results) {
                    SearchpinResult r = new SearchpinResult();
                    r.title = item.path("title").asText("");
                    r.url = item.path("url").asText("");
                    r.snippet = item.path("snippet").asText("");
                    list.add(r);
                }
            }
            return list;
        }

        private void send(Map<String, Object> msg) throws IOException {
            stdin.write(mapper.writeValueAsString(msg).getBytes(StandardCharsets.UTF_8));
            stdin.write('\n');
            stdin.flush();
        }

        @Override
        public void close() {
            try {
                stdin.close();
            } catch (IOException ignored) {
                // ignore
            }
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /** Searchpin web_search 单条结果 */
    private static class SearchpinResult {
        String title;
        String url;
        String snippet;
    }

    // ==================== 数据类 ====================

    @Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class WebSearchResult {
        private String title;
        private String url;
        private String snippet;
        private String source;
    }
}
