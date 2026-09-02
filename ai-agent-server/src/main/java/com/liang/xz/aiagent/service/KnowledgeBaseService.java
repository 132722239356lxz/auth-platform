package com.liang.xz.aiagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.aiagent.dto.KnowledgeBaseVO;
import com.liang.xz.aiagent.dto.KnowledgeDocDetailVO;
import com.liang.xz.aiagent.dto.UploadTaskVO;
import com.liang.xz.aiagent.config.AiProperties;
import com.liang.xz.aiagent.entity.KnowledgeBase;
import com.liang.xz.aiagent.entity.KnowledgeDoc;
import com.liang.xz.aiagent.entity.UploadTask;
import com.liang.xz.aiagent.rag.RetrievalPipeline;
import com.liang.xz.aiagent.rag.TextSplitter;
import com.liang.xz.aiagent.repository.KnowledgeBaseRepository;
import com.liang.xz.aiagent.repository.KnowledgeDocRepository;
import com.liang.xz.aiagent.repository.UploadTaskRepository;
import com.liang.xz.aiagent.search.LocalSearchEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * <p>知识库管理服务</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class KnowledgeBaseService {

    private final KnowledgeDocRepository docRepository;
    private final KnowledgeBaseRepository kbRepository;
    private final UploadTaskRepository uploadTaskRepository;
    private final RetrievalPipeline retrievalPipeline;
    private final FileParserService fileParserService;
    private final ObjectMapper objectMapper;
    private final LocalSearchEngine localSearchEngine;
    private final NamedParameterJdbcTemplate jdbc;
    private final Executor embeddingIndexExecutor;
    private final AiProperties aiProperties;

    /**
     * 并发索引信号量：限制同时进行的异步文档索引任务数，
     * 避免多文件并行时打爆数据库连接池 / 向量服务导致部分写入静默失败。
     */
    private final Semaphore indexSemaphore = new Semaphore(3);

    public KnowledgeBaseService(KnowledgeDocRepository docRepository,
                                KnowledgeBaseRepository kbRepository,
                                UploadTaskRepository uploadTaskRepository,
                                RetrievalPipeline retrievalPipeline,
                                FileParserService fileParserService,
                                ObjectMapper objectMapper,
                                LocalSearchEngine localSearchEngine,
                                NamedParameterJdbcTemplate jdbc,
                                @Qualifier("embeddingIndexExecutor") Executor embeddingIndexExecutor,
                                AiProperties aiProperties) {
        this.docRepository = docRepository;
        this.kbRepository = kbRepository;
        this.uploadTaskRepository = uploadTaskRepository;
        this.retrievalPipeline = retrievalPipeline;
        this.fileParserService = fileParserService;
        this.objectMapper = objectMapper;
        this.localSearchEngine = localSearchEngine;
        this.jdbc = jdbc;
        this.embeddingIndexExecutor = embeddingIndexExecutor;
        this.aiProperties = aiProperties;
    }

    // ==================== 知识库 CRUD ====================

    @Transactional
    public KnowledgeBase createKnowledgeBase(String name, String description) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("知识库名称不能为空");
        }
        String trimmed = name.trim();
        if (kbRepository.existsByName(trimmed)) {
            throw new IllegalArgumentException("知识库名称已存在: " + trimmed);
        }
        KnowledgeBase kb = KnowledgeBase.builder()
                .name(trimmed)
                .description(description)
                .status("ACTIVE")
                .build();
        return kbRepository.save(kb);
    }

    @Transactional
    public KnowledgeBase updateKnowledgeBase(Long id, String name, String description) {
        KnowledgeBase kb = kbRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在: " + id));
        if (name != null && !name.trim().isEmpty()) {
            String trimmed = name.trim();
            if (!trimmed.equals(kb.getName()) && kbRepository.existsByName(trimmed)) {
                throw new IllegalArgumentException("知识库名称已存在: " + trimmed);
            }
            kb.setName(trimmed);
        }
        if (description != null) {
            kb.setDescription(description);
        }
        return kbRepository.save(kb);
    }

    @Transactional
    public void deleteKnowledgeBase(Long id) {
        KnowledgeBase kb = kbRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在: " + id));
        // 删除向量 + 文档 + 任务
        retrievalPipeline.deleteKnowledgeBase(kb.getName());
        docRepository.deleteByKbName(kb.getName());
        kbRepository.deleteById(id);
    }

    public List<KnowledgeBaseVO> listKnowledgeBases() {
        return kbRepository.listAll().stream().map(KnowledgeBaseVO::from).toList();
    }

    public KnowledgeBaseVO getKnowledgeBase(Long id) {
        return kbRepository.findById(id).map(KnowledgeBaseVO::from).orElse(null);
    }

    // ==================== 文档管理 ====================

    /**
     * 添加文档到知识库(分割 → 向量化 → 存储 + Lucene索引)
     */
    @Transactional
    public KnowledgeDoc addDocument(String kbName, String title, String content,
                                    String contentType, String fileName,
                                    TextSplitter.SplitStrategy strategy, Integer chunkSize, Integer overlap) {
        if (kbName == null || kbName.trim().isEmpty()) {
            throw new IllegalArgumentException("知识库名称不能为空");
        }
        ensureKnowledgeBaseExists(kbName.trim());

        TextSplitter.SplitStrategy useStrategy = strategy != null ? strategy
                : TextSplitter.inferStrategy(contentType);
        int useChunkSize = chunkSize != null && chunkSize > 0 ? chunkSize : TextSplitter.DEFAULT_CHUNK_SIZE;
        int useOverlap = overlap != null && overlap >= 0 ? overlap : TextSplitter.DEFAULT_OVERLAP;

        // Step 1: 保存文档记录
        KnowledgeDoc doc = KnowledgeDoc.builder()
                .kbName(kbName.trim())
                .title(title != null && !title.isEmpty() ? title : "未命名文档")
                .content(content)
                .contentType(contentType != null ? contentType : "TEXT")
                .fileName(fileName)
                .chunkCount(0)
                .splitterType(useStrategy.name())
                .chunkSize(useChunkSize)
                .overlap(useOverlap)
                .status("CHUNKING")
                .build();
        docRepository.save(doc);

        // Step 2: 分割 + 向量化 + 存入向量库
        try {
            int chunkCount = retrievalPipeline.indexDocument(
                    doc.getKbName(), String.valueOf(doc.getId()), doc.getTitle(), doc.getContent(),
                    useStrategy, useChunkSize, useOverlap);
            docRepository.updateStatus(doc.getId(), "INDEXED", chunkCount);
            doc.setChunkCount(chunkCount);
            doc.setStatus("INDEXED");

            // Step 3: 同步到 Lucene 本地搜索引擎
            syncToLucene(String.valueOf(doc.getId()), doc.getTitle(), doc.getContent(), doc.getKbName());
        } catch (Exception e) {
            log.error("Document indexing failed: {}", doc.getId(), e);
            docRepository.updateStatus(doc.getId(), "FAILED", 0);
            doc.setStatus("FAILED");
        }

        return doc;
    }

    /**
     * 编辑文档并重新索引
     */
    @Transactional
    public KnowledgeDoc updateDocument(Long id, String kbName, String title, String content,
                                       String contentType, String fileName,
                                       TextSplitter.SplitStrategy strategy, Integer chunkSize, Integer overlap) {
        KnowledgeDoc doc = docRepository.findById(id);
        if (doc == null) throw new IllegalArgumentException("文档不存在: " + id);

        String newKbName = kbName != null && !kbName.trim().isEmpty() ? kbName.trim() : doc.getKbName();
        ensureKnowledgeBaseExists(newKbName);

        // 删除旧向量
        docRepository.deleteChunksByDocId(id);
        retrievalPipeline.deleteByDocId(id);
        try {
            localSearchEngine.delete(String.valueOf(id));
        } catch (Exception e) {
            log.warn("Lucene 删除旧索引失败: docId={}", id, e);
        }

        TextSplitter.SplitStrategy useStrategy = strategy != null ? strategy
                : TextSplitter.inferStrategy(contentType != null ? contentType : doc.getContentType());
        int useChunkSize = chunkSize != null && chunkSize > 0 ? chunkSize : TextSplitter.DEFAULT_CHUNK_SIZE;
        int useOverlap = overlap != null && overlap >= 0 ? overlap : TextSplitter.DEFAULT_OVERLAP;

        doc.setKbName(newKbName);
        doc.setTitle(title != null ? title : doc.getTitle());
        if (content != null) doc.setContent(content);
        if (contentType != null) doc.setContentType(contentType);
        if (fileName != null) doc.setFileName(fileName);
        doc.setSplitterType(useStrategy.name());
        doc.setChunkSize(useChunkSize);
        doc.setOverlap(useOverlap);
        doc.setChunkCount(0);
        doc.setStatus("CHUNKING");
        docRepository.save(doc);

        try {
            int chunkCount = retrievalPipeline.indexDocument(
                    doc.getKbName(), String.valueOf(doc.getId()), doc.getTitle(), doc.getContent(),
                    useStrategy, useChunkSize, useOverlap);
            docRepository.updateStatus(doc.getId(), "INDEXED", chunkCount);
            doc.setChunkCount(chunkCount);
            doc.setStatus("INDEXED");
            syncToLucene(String.valueOf(doc.getId()), doc.getTitle(), doc.getContent(), doc.getKbName());
        } catch (Exception e) {
            log.error("Document re-indexing failed: {}", doc.getId(), e);
            docRepository.updateStatus(doc.getId(), "FAILED", 0);
            doc.setStatus("FAILED");
        }
        return doc;
    }

    public List<KnowledgeDoc> listDocuments(String kbName) {
        return docRepository.findByKbName(kbName);
    }

    public List<KnowledgeDoc> listAllDocuments() {
        return docRepository.listAll();
    }

    public KnowledgeDoc getDocument(Long id) {
        return docRepository.findById(id);
    }

    /**
     * 删除文档
     */
    @Transactional
    public void deleteDocument(Long id) {
        KnowledgeDoc doc = docRepository.findById(id);
        if (doc == null) return;

        // 删除向量存储中的 chunks
        retrievalPipeline.deleteByDocId(id);
        // 删除 Lucene 索引
        try {
            localSearchEngine.delete(String.valueOf(id));
        } catch (Exception e) {
            log.warn("Lucene索引删除失败: docId={}", id, e);
        }
        // 删除任务记录
        uploadTaskRepository.deleteByDocId(id);
        // 删除 DB chunks
        docRepository.deleteChunksByDocId(id);
        // 删除 doc
        docRepository.deleteById(id);
    }

    public Map<String, Object> getStats() {
        Integer totalChunks = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_knowledge_chunk", Map.of(), Integer.class);
        return Map.of(
                "knowledgeBases", kbRepository.listAll().size(),
                "totalDocs", docRepository.listAll().size(),
                "totalChunks", totalChunks != null ? totalChunks : 0,
                "vectorStoreSize", retrievalPipeline.getStats().get("totalDocuments")
        );
    }

    // ==================== 文件上传 ====================

    /**
     * 文件上传结果
     */
    @lombok.Builder
    @lombok.Data
    public static class UploadResult {
        private boolean success;
        private String taskId;
        private Long docId;
        private KnowledgeDoc doc;
        private String error;
        private String parsedContentType;
        private long parseTimeMs;
    }

    /**
     * 上传文件并自动解析入库（异步处理）
     * <p>先保存上传任务和文档记录(状态PENDING)并立即返回任务ID，后台异步进行解析+分割+向量化。
     * 本方法不再持有长事务，避免异步索引/超时等待期间长时间占用 {@code ai_upload_task} 行锁，
     * 导致并发上传或清理时的 {@code Lock wait timeout}。</p>
     */
    public UploadResult addDocumentFromFile(MultipartFile file, String kbName, String title, String contentType,
                                            TextSplitter.SplitStrategy strategy, Integer chunkSize, Integer overlap) {
        String originalFileName = file.getOriginalFilename();
        long fileSize = file.getSize();

        log.info("[KnowledgeBase] 接收文件上传: {} ({} bytes) -> kb={}", originalFileName, fileSize, kbName);

        if (kbName == null || kbName.trim().isEmpty()) {
            return UploadResult.builder().success(false).error("知识库名称不能为空").build();
        }
        ensureKnowledgeBaseExists(kbName.trim());

        // 0. 校验文件大小（超过配置上限直接拒绝，不进入索引流程）
        long maxFileSizeBytes = parseSizeToBytes(aiProperties.getFileUpload().getMaxFileSize());
        if (fileSize > maxFileSizeBytes) {
            return UploadResult.builder()
                    .success(false)
                    .error(String.format("文件过大: %d 字节，超过上限 %s（%d 字节）",
                            fileSize, aiProperties.getFileUpload().getMaxFileSize(), maxFileSizeBytes))
                    .build();
        }

        // 1. 校验格式
        if (!fileParserService.isSupported(originalFileName)) {
            return UploadResult.builder()
                    .success(false)
                    .error("不支持的文件格式: " + originalFileName + "，支持: " + fileParserService.getSupportedFormats())
                    .build();
        }

        // 2. 创建上传任务
        String taskId = UUID.randomUUID().toString().replace("-", "");
        UploadTask task = UploadTask.builder()
                .id(taskId)
                .kbName(kbName.trim())
                .fileName(originalFileName)
                .fileSize(fileSize)
                .status("UPLOADING")
                .progress(0)
                .build();

        // 3. 读取文件数据
        byte[] fileData;
        try {
            fileData = file.getBytes();
        } catch (IOException e) {
            log.error("[KnowledgeBase] 读取文件失败", e);
            uploadTaskRepository.markFailed(taskId, "读取文件失败: " + e.getMessage());
            return UploadResult.builder().success(false).taskId(taskId).error("读取文件失败: " + e.getMessage()).build();
        }

        // 4. 更新任务为解析中
        uploadTaskRepository.updateProgress(taskId, 30, "PARSING");

        // 5. 解析文件内容
        FileParserService.ParseResult parseResult = fileParserService.parse(fileData, originalFileName);
        if (parseResult.getError() != null) {
            uploadTaskRepository.markFailed(taskId, parseResult.getError());
            return UploadResult.builder()
                    .success(false)
                    .taskId(taskId)
                    .error(parseResult.getError())
                    .parsedContentType(parseResult.getContentType())
                    .parseTimeMs(parseResult.getParseTimeMs())
                    .build();
        }

        // 6. 标题默认用文件名；文件名兜底用标题；类型优先使用传入值，否则用解析结果
        String docTitle = (title != null && !title.isEmpty()) ? title : originalFileName;
        String savedFileName = (originalFileName != null && !originalFileName.isEmpty()) ? originalFileName : docTitle;
        String docContentType = (contentType != null && !contentType.isEmpty()) ? contentType.toUpperCase()
                : parseResult.getContentType();

        TextSplitter.SplitStrategy useStrategy = strategy != null ? strategy
                : TextSplitter.inferStrategy(docContentType);
        int useChunkSize = chunkSize != null && chunkSize > 0 ? chunkSize : TextSplitter.DEFAULT_CHUNK_SIZE;
        int useOverlap = overlap != null && overlap >= 0 ? overlap : TextSplitter.DEFAULT_OVERLAP;

        // 7. 保存任务+文档记录（独立短事务，立即提交以释放行锁）
        KnowledgeDoc doc = saveUploadAndDoc(taskId, kbName.trim(), docTitle, savedFileName,
                docContentType, fileSize, useStrategy, useChunkSize, useOverlap, parseResult.getText());

        // 8. 异步执行分割+向量化+Lucene索引（自定义线程池调度 + 信号量限流，避免并发打爆连接池）
        CompletableFuture<Void> indexFuture = CompletableFuture.runAsync(
                () -> asyncIndexDocument(taskId, doc, parseResult, useStrategy, useChunkSize, useOverlap),
                embeddingIndexExecutor);
        // 单文件索引超时熔断：超过 perFileIndexTimeoutSeconds 视为失败，释放信号量并清理任务
        int timeoutSeconds = aiProperties.getFileUpload().getPerFileIndexTimeoutSeconds();
        try {
            indexFuture.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException tex) {
            log.error("[KnowledgeBase] 文件索引超时(>{}s): docId={}, fileName={}",
                    timeoutSeconds, doc.getId(), originalFileName);
            indexFuture.cancel(true);
            cleanupFailedDoc(doc.getId(), taskId, "索引超时（超过 " + timeoutSeconds + " 秒）");
        } catch (Exception ex) {
            log.error("[KnowledgeBase] 文件索引异常: docId={}", doc.getId(), ex);
        }

        return UploadResult.builder()
                .success(true)
                .taskId(taskId)
                .docId(doc.getId())
                .doc(doc)
                .parsedContentType(docContentType)
                .parseTimeMs(parseResult.getParseTimeMs())
                .build();
    }

    /**
     * 将带单位的大小字符串(如 20MB、500KB)解析为字节数。
     *
     * @param size 形如 {@code 20MB} / {@code 500KB} / {@code 1GB} 的大小配置
     * @return 对应的字节数；无法解析时返回 {@code Long.MAX_VALUE}(不限制)
     */
    private long parseSizeToBytes(String size) {
        if (size == null || size.isBlank()) {
            return Long.MAX_VALUE;
        }
        String upper = size.trim().toUpperCase();
        long multiplier = 1L;
        if (upper.endsWith("KB")) {
            multiplier = 1024L;
            upper = upper.substring(0, upper.length() - 2);
        } else if (upper.endsWith("MB")) {
            multiplier = 1024L * 1024L;
            upper = upper.substring(0, upper.length() - 2);
        } else if (upper.endsWith("GB")) {
            multiplier = 1024L * 1024L * 1024L;
            upper = upper.substring(0, upper.length() - 2);
        }
        try {
            return (long) (Double.parseDouble(upper.trim()) * multiplier);
        } catch (NumberFormatException e) {
            log.warn("[KnowledgeBase] 无法解析文件大小配置: {}，视为不限制", size);
            return Long.MAX_VALUE;
        }
    }

    /**
     * 异步索引文档（受信号量限流）
     */
    private void asyncIndexDocument(String taskId, KnowledgeDoc doc, FileParserService.ParseResult parseResult,
                                    TextSplitter.SplitStrategy strategy, int chunkSize, int overlap) {
        indexSemaphore.acquireUninterruptibly();
        try {
            uploadTaskRepository.updateProgress(taskId, 60, "EMBEDDING");
            docRepository.updateStatus(doc.getId(), "CHUNKING", 0);
            int chunkCount = retrievalPipeline.indexDocument(
                    doc.getKbName(), String.valueOf(doc.getId()), doc.getTitle(), parseResult.getText(),
                    strategy, chunkSize, overlap);
            docRepository.updateStatus(doc.getId(), "INDEXED", chunkCount);
            uploadTaskRepository.updateProgress(taskId, 100, "INDEXED");

            // 同步到 Lucene
            syncToLucene(String.valueOf(doc.getId()), doc.getTitle(), parseResult.getText(), doc.getKbName());

            log.info("[KnowledgeBase] 异步入库成功: docId={}, chunks={}", doc.getId(), chunkCount);
        } catch (Exception e) {
            log.error("[KnowledgeBase] 异步入库失败: docId={}", doc.getId(), e);
            // 一致性保证：索引失败则清理半成品文档，避免前端看到无效/半成品文档
            cleanupFailedDoc(doc.getId(), taskId, e.getMessage());
        } finally {
            indexSemaphore.release();
        }
    }

    /**
     * 索引失败时清理已创建的文档与分块，保证事务一致性（上传有误不保留文档）。
     * <p>使用 {@code REQUIRES_NEW} 独立短事务立即提交，避免被调用方长事务拖住而拿不到行锁。</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanupFailedDoc(Long docId, String taskId, String errorMsg) {
        try {
            log.warn("[KnowledgeBase] 清理索引失败的文档: docId={}", docId);
            docRepository.deleteChunksByDocId(docId);
            docRepository.deleteById(docId);
        } catch (Exception ex) {
            log.error("[KnowledgeBase] 清理失败文档异常: docId={}", docId, ex);
        }
        try {
            uploadTaskRepository.markFailed(taskId, errorMsg);
        } catch (Exception ex) {
            log.error("[KnowledgeBase] 更新任务失败状态异常: taskId={}", taskId, ex);
        }
    }

    /**
     * 保存上传任务与文档记录（独立短事务，立即提交，不覆盖后续异步索引窗口）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KnowledgeDoc saveUploadAndDoc(String taskId, String kbName, String docTitle, String savedFileName,
                                         String docContentType, long fileSize,
                                         TextSplitter.SplitStrategy useStrategy, int useChunkSize, int useOverlap,
                                         String text) {
        UploadTask task = UploadTask.builder()
                .id(taskId)
                .kbName(kbName)
                .fileName(savedFileName)
                .fileSize(fileSize)
                .status("UPLOADING")
                .progress(0)
                .build();
        uploadTaskRepository.save(task);
        KnowledgeDoc doc = KnowledgeDoc.builder()
                .kbName(kbName)
                .title(docTitle != null && !docTitle.isEmpty() ? docTitle : "未命名文档")
                .content(text)
                .contentType(docContentType)
                .fileName(savedFileName)
                .fileSize(fileSize)
                .chunkCount(0)
                .splitterType(useStrategy.name())
                .chunkSize(useChunkSize)
                .overlap(useOverlap)
                .status("PENDING")
                .build();
        docRepository.save(doc);
        uploadTaskRepository.updateDocId(taskId, doc.getId());
        uploadTaskRepository.updateProgress(taskId, 50, "CHUNKING");
        return doc;
    }

    /**
     * 将文档同步到 Lucene 本地搜索引擎
     */
    private void syncToLucene(String docId, String title, String content, String kbName) {
        try {
            Map<String, String> metadata = Map.of("kbName", kbName, "source", "knowledge_base");
            localSearchEngine.index(docId, title, content, metadata);
            log.debug("Lucene indexed: docId={}, kb={}", docId, kbName);
        } catch (Exception e) {
            log.warn("Lucene索引同步失败: docId={}", docId, e);
        }
    }

    private void ensureKnowledgeBaseExists(String kbName) {
        Optional<KnowledgeBase> existing = kbRepository.findByName(kbName);
        if (existing.isEmpty()) {
            KnowledgeBase kb = KnowledgeBase.builder()
                    .name(kbName)
                    .description("自动创建")
                    .status("ACTIVE")
                    .build();
            kbRepository.save(kb);
            log.info("自动创建知识库: {}", kbName);
        }
    }

    public KnowledgeDocDetailVO getDocumentDetail(Long id) {
        KnowledgeDoc doc = docRepository.findById(id);
        if (doc == null) return null;
        List<com.liang.xz.aiagent.entity.KnowledgeChunk> chunks = docRepository.findChunksByDocId(id);
        return KnowledgeDocDetailVO.from(doc, chunks);
    }

    // ==================== 上传任务查询 ====================

    public UploadTaskVO getUploadTask(String taskId) {
        return uploadTaskRepository.findById(taskId).map(UploadTaskVO::from).orElse(null);
    }
}
