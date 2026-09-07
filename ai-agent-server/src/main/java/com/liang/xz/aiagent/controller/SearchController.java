package com.liang.xz.aiagent.controller;

import com.liang.xz.aiagent.dto.SearchRequest;
import com.liang.xz.aiagent.service.SearchAggregationService;
import com.liang.xz.common.core.model.R;
import com.liang.xz.resource.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * <p>统一搜索 API — 本地离线 / 联网 / RAG / 混合</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Tag(name = "智能搜索", description = "本地离线检索 / 联网搜索 / RAG知识库检索 / 混合搜索")
@RestController
@RequestMapping("/api/ai/search")
public class SearchController {

    private final SearchAggregationService searchService;

    public SearchController(SearchAggregationService searchService) {
        this.searchService = searchService;
    }

    @Operation(summary = "统一搜索入口", description = "根据 searchType 路由到不同搜索引擎: LOCAL/INTERNET/RAG/HYBRID")
    @RequirePermission("ai:search:execute")
    @PostMapping
    public R<Map<String, Object>> search(@RequestBody SearchRequest req) {
        String type = req.getSearchType() != null ? req.getSearchType().toUpperCase() : "HYBRID";
        int maxResults = req.getMaxResults() != null ? req.getMaxResults() : 10;

        Map<String, Object> result = switch (type) {
            case "LOCAL" -> searchService.localSearch(req.getQuery(), maxResults);
            case "INTERNET" -> searchService.webSearch(req.getQuery(), maxResults);
            case "RAG" -> {
                if (req.getKbName() != null) {
                    yield searchService.ragSearchInKb(req.getKbName(), req.getQuery(), maxResults);
                }
                yield searchService.ragSearch(req.getQuery(), maxResults);
            }
            case "SIMILARITY" -> searchService.similaritySearch(req.getQuery(), req.getKbName(), maxResults);
            case "SIMILARITY_LOCAL" -> searchService.similarityLocalSearch(req.getQuery(), req.getKbName(), maxResults);
            case "HYBRID" -> searchService.hybridSearch(req.getQuery(), maxResults);
            default -> Map.of("success", false, "message", "不支持的搜索类型: " + type);
        };
        return R.ok(result);
    }

    @Operation(summary = "本地离线搜索")
    @RequirePermission("ai:search:execute")
    @GetMapping("/local")
    public R<Map<String, Object>> localSearch(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int n) {
        return R.ok(searchService.localSearch(q, n));
    }

    @Operation(summary = "搜索建议/前缀补全")
    @RequirePermission("ai:search:execute")
    @GetMapping("/suggest")
    public R<Map<String, Object>> suggest(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int n) {
        return R.ok(searchService.localSuggest(q, n));
    }

    @Operation(summary = "联网搜索")
    @RequirePermission("ai:search:execute")
    @GetMapping("/web")
    public R<Map<String, Object>> webSearch(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int n) {
        return R.ok(searchService.webSearch(q, n));
    }

    @Operation(summary = "相似度检索(向量检索)")
    @RequirePermission("ai:search:execute")
    @GetMapping("/similarity")
    public R<Map<String, Object>> similaritySearch(
            @RequestParam String q,
            @RequestParam(required = false) String kbName,
            @RequestParam(defaultValue = "5") int k) {
        return R.ok(searchService.similaritySearch(q, kbName, k));
    }

    @Operation(summary = "相似度 + 本地检索")
    @RequirePermission("ai:search:execute")
    @GetMapping("/similarity-local")
    public R<Map<String, Object>> similarityLocalSearch(
            @RequestParam String q,
            @RequestParam(required = false) String kbName,
            @RequestParam(defaultValue = "5") int k) {
        return R.ok(searchService.similarityLocalSearch(q, kbName, k));
    }

    @Operation(summary = "RAG知识库检索")
    @RequirePermission("ai:search:execute")
    @GetMapping("/rag")
    public R<Map<String, Object>> ragSearch(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int k) {
        return R.ok(searchService.ragSearch(q, k));
    }

    @Operation(summary = "混合搜索(本地+联网+RAG)")
    @RequirePermission("ai:search:execute")
    @GetMapping("/hybrid")
    public R<Map<String, Object>> hybridSearch(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int n) {
        return R.ok(searchService.hybridSearch(q, n));
    }

    @Operation(summary = "本地索引统计")
    @RequirePermission("ai:search:list")
    @GetMapping("/index-stats")
    public R<Map<String, Object>> indexStats() {
        return R.ok(Map.of("success", true, "indexCount", searchService.getLocalIndexCount()));
    }
}
