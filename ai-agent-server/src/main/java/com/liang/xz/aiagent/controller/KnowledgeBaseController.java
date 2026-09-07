package com.liang.xz.aiagent.controller;

import com.liang.xz.aiagent.dto.*;
import com.liang.xz.aiagent.entity.KnowledgeBase;
import com.liang.xz.aiagent.entity.KnowledgeDoc;
import com.liang.xz.aiagent.rag.TextSplitter;
import com.liang.xz.aiagent.config.AiProperties;
import com.liang.xz.aiagent.repository.DocumentElementRepository;
import com.liang.xz.aiagent.service.FileParserService;
import com.liang.xz.aiagent.service.KnowledgeBaseService;
import com.liang.xz.common.core.model.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * <p>知识库管理 API</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "知识库管理", description = "知识库文档的增删改查、文件上传、向量化入库")
@RestController
@RequestMapping("/api/ai/knowledge")
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;
    private final FileParserService fileParserService;
    private final AiProperties aiProperties;
    private final DocumentElementRepository documentElementRepository;

    public KnowledgeBaseController(KnowledgeBaseService kbService, FileParserService fileParserService,
                                   AiProperties aiProperties,
                                   DocumentElementRepository documentElementRepository) {
        this.kbService = kbService;
        this.fileParserService = fileParserService;
        this.aiProperties = aiProperties;
        this.documentElementRepository = documentElementRepository;
    }

    // ==================== 知识库 CRUD ====================

    @Operation(summary = "创建知识库")
    @PostMapping("/base")
    public R<KnowledgeBaseVO> createKnowledgeBase(@RequestBody Map<String, String> req) {
        KnowledgeBase kb = kbService.createKnowledgeBase(req.get("name"), req.get("description"));
        return R.ok(KnowledgeBaseVO.from(kb));
    }

    @Operation(summary = "修改知识库")
    @PutMapping("/base/{id}")
    public R<KnowledgeBaseVO> updateKnowledgeBase(@PathVariable Long id, @RequestBody Map<String, String> req) {
        KnowledgeBase kb = kbService.updateKnowledgeBase(id, req.get("name"), req.get("description"));
        return R.ok(KnowledgeBaseVO.from(kb));
    }

    @Operation(summary = "删除知识库")
    @DeleteMapping("/base/{id}")
    public R<Void> deleteKnowledgeBase(@PathVariable Long id) {
        kbService.deleteKnowledgeBase(id);
        return R.ok(null);
    }

    @Operation(summary = "获取知识库列表")
    @GetMapping("/base")
    public R<List<KnowledgeBaseVO>> listKnowledgeBases() {
        return R.ok(kbService.listKnowledgeBases());
    }

    @Operation(summary = "获取单个知识库")
    @GetMapping("/base/{id}")
    public R<KnowledgeBaseVO> getKnowledgeBase(@PathVariable Long id) {
        KnowledgeBaseVO vo = kbService.getKnowledgeBase(id);
        return vo == null ? R.fail("知识库不存在") : R.ok(vo);
    }

    // ==================== 文档管理 ====================

    @Operation(summary = "添加文档到知识库")
    @PostMapping("/doc")
    public R<KnowledgeDocVO> addDocument(@RequestBody KnowledgeDocRequest req) {
        TextSplitter.SplitStrategy strategy = parseStrategy(req.getSplitterType(), req.getContentType());
        KnowledgeDoc doc = kbService.addDocument(
                req.getKbName(), req.getTitle(), req.getContent(),
                req.getContentType(), req.getFileName(),
                strategy, req.getChunkSize(), req.getOverlap());
        return R.ok(KnowledgeDocVO.from(doc));
    }

    @Operation(summary = "编辑文档并重新索引")
    @PutMapping("/doc/{id}")
    public R<KnowledgeDocVO> updateDocument(@PathVariable Long id, @RequestBody KnowledgeDocRequest req) {
        TextSplitter.SplitStrategy strategy = parseStrategy(req.getSplitterType(), req.getContentType());
        KnowledgeDoc doc = kbService.updateDocument(
                id, req.getKbName(), req.getTitle(), req.getContent(),
                req.getContentType(), req.getFileName(),
                strategy, req.getChunkSize(), req.getOverlap());
        return R.ok(KnowledgeDocVO.from(doc));
    }

    @Operation(summary = "获取指定知识库的所有文档")
    @GetMapping("/kb/{kbName}/docs")
    public R<List<KnowledgeDocVO>> listDocuments(@PathVariable String kbName) {
        List<KnowledgeDocVO> docs = kbService.listDocuments(kbName).stream()
                .map(KnowledgeDocVO::from).toList();
        return R.ok(docs);
    }

    @Operation(summary = "获取所有文档")
    @GetMapping("/docs")
    public R<List<KnowledgeDocVO>> listAllDocuments() {
        List<KnowledgeDocVO> docs = kbService.listAllDocuments().stream()
                .map(KnowledgeDocVO::from).toList();
        return R.ok(docs);
    }

    @Operation(summary = "获取单个文档详情")
    @GetMapping("/doc/{id}")
    public R<KnowledgeDocVO> getDocument(@PathVariable Long id) {
        KnowledgeDoc doc = kbService.getDocument(id);
        if (doc == null) {
            return R.fail("文档不存在");
        }
        return R.ok(KnowledgeDocVO.from(doc));
    }

    @Operation(summary = "删除文档")
    @DeleteMapping("/doc/{id}")
    public R<Void> deleteDocument(@PathVariable Long id) {
        kbService.deleteDocument(id);
        return R.ok(null);
    }

    @Operation(summary = "知识库统计")
    @GetMapping("/stats")
    public R<Map<String, Object>> getStats() {
        return R.ok(kbService.getStats());
    }

    // ==================== 文件上传 ====================

    @Operation(summary = "上传文件到知识库", description = "支持PDF/DOCX/XLSX/TXT/MD/HTML/CSV/JSON/图片/音频/视频等，自动解析并向量化入库；支持单文件或多文件")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<List<Map<String, Object>>> uploadFile(
            @Parameter(description = "文件") @RequestParam("files") MultipartFile[] files,
            @Parameter(description = "知识库名称") @RequestParam("kbName") String kbName,
            @Parameter(description = "文档标题(可选，默认使用文件名)") @RequestParam(value = "title", required = false) String title,
            @Parameter(description = "文档类型(可选，如PDF/IMAGE/AUDIO/VIDEO，默认根据文件扩展名推断)") @RequestParam(value = "contentType", required = false) String contentType,
            @Parameter(description = "切片策略(PARAGRAPH/MARKDOWN/HTML/CODE/JSON/CSV/CHARACTER)")
            @RequestParam(value = "splitterType", required = false) String splitterType,
            @Parameter(description = "切片大小") @RequestParam(value = "chunkSize", required = false) Integer chunkSize,
            @Parameter(description = "重叠字符数") @RequestParam(value = "overlap", required = false) Integer overlap) {

        if (files == null || files.length == 0 || Arrays.stream(files).allMatch(MultipartFile::isEmpty)) {
            return R.fail("文件为空");
        }

        // 单次上传文件数量上限校验
        int maxFileCount = aiProperties.getFileUpload().getMaxFileCount();
        if (files.length > maxFileCount) {
            return R.fail("单次最多上传 " + maxFileCount + " 个文件，当前收到 " + files.length + " 个");
        }

        TextSplitter.SplitStrategy strategy = parseStrategy(splitterType, contentType);
        List<Map<String, Object>> results = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            KnowledgeBaseService.UploadResult result = kbService.addDocumentFromFile(
                    file, kbName, title, contentType, strategy, chunkSize, overlap);
            results.add(Map.of(
                    "success", result.isSuccess(),
                    "taskId", result.getTaskId() != null ? result.getTaskId() : "",
                    "docId", result.getDocId() != null ? result.getDocId() : 0,
                    "error", result.getError() != null ? result.getError() : "",
                    "parsedContentType", result.getParsedContentType() != null ? result.getParsedContentType() : "",
                    "parseTimeMs", result.getParseTimeMs()
            ));
            if (!result.isSuccess() && result.getTaskId() == null) {
                return R.fail(result.getError());
            }
        }
        return R.ok(results);
    }

    @Operation(summary = "获取上传任务状态")
    @GetMapping("/task/{taskId}")
    public R<UploadTaskVO> getUploadTask(@PathVariable String taskId) {
        UploadTaskVO task = kbService.getUploadTask(taskId);
        return task == null ? R.fail("任务不存在") : R.ok(task);
    }

    @Operation(summary = "获取支持的文件格式")
    @GetMapping("/supported-formats")
    public R<Map<String, Object>> getSupportedFormats() {
        return R.ok(Map.of(
                "success", true,
                "data", fileParserService.getSupportedFormats()
        ));
    }

    // ==================== 切片查看 ====================

    @Operation(summary = "获取文档切片详情")
    @GetMapping("/doc/{id}/chunks")
    public R<KnowledgeDocDetailVO> getDocumentChunks(@PathVariable Long id) {
        KnowledgeDocDetailVO detail = kbService.getDocumentDetail(id);
        return detail == null ? R.fail("文档不存在") : R.ok(detail);
    }

    // ==================== 文档元素元数据（深度分析产物） ====================

    @Operation(summary = "获取文档元素元数据",
            description = "返回PDF/Word深度分析识别出的元素：表格、图片、图表、扫描页、页眉页脚等，"
                    + "含页码、位置坐标、处理方式与置信度，用于确认文档解析质量")
    @GetMapping("/doc/{id}/elements")
    public R<List<Map<String, Object>>> getDocumentElements(
            @PathVariable Long id,
            @RequestParam(required = false) String type) {
        List<Map<String, Object>> elements = documentElementRepository.findByDocId(id);
        // 按类型过滤：便于前端只查看表格或只查看图片
        if (type != null && !type.isBlank()) {
            String wanted = type.trim().toUpperCase();
            elements = elements.stream()
                    .filter(row -> wanted.equals(String.valueOf(row.get("element_type")))
                            || ("IMAGE".equals(wanted)
                                && "CHART".equals(String.valueOf(row.get("element_type")))))
                    .toList();
        }
        return R.ok(elements);
    }

    @Operation(summary = "获取文档元素统计",
            description = "按元素类型与处理方式汇总，快速判断文档构成（如扫描件占比、表格数量）")
    @GetMapping("/doc/{id}/elements/stat")
    public R<Map<String, Object>> getDocumentElementStat(@PathVariable Long id) {
        List<Map<String, Object>> elements = documentElementRepository.findByDocId(id);
        Map<String, Object> stat = new java.util.LinkedHashMap<>();
        stat.put("docId", id);
        stat.put("totalElements", elements.size());

        Map<String, Integer> byType = new java.util.LinkedHashMap<>();
        Map<String, Integer> byMethod = new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : elements) {
            String elementType = String.valueOf(row.get("element_type"));
            String method = String.valueOf(row.get("processing_method"));
            byType.merge(elementType, 1, Integer::sum);
            byMethod.merge(method, 1, Integer::sum);
        }
        stat.put("byType", byType);
        stat.put("byProcessingMethod", byMethod);
        return R.ok(stat);
    }

    private TextSplitter.SplitStrategy parseStrategy(String splitterType, String contentType) {
        if (splitterType != null && !splitterType.isEmpty()) {
            try {
                return TextSplitter.SplitStrategy.valueOf(splitterType.toUpperCase());
            } catch (IllegalArgumentException e) {
                return TextSplitter.inferStrategy(contentType);
            }
        }
        return TextSplitter.inferStrategy(contentType);
    }
}
