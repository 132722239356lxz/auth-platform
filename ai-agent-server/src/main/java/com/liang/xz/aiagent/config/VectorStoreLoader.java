package com.liang.xz.aiagent.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.aiagent.entity.KnowledgeChunk;
import com.liang.xz.aiagent.repository.KnowledgeDocRepository;
import com.liang.xz.aiagent.vector.VectorDocument;
import com.liang.xz.aiagent.vector.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>向量存储启动加载器 — 应用启动时从MySQL恢复长期记忆到内存向量存储</p>
 *
 * <p>实现短期记忆(内存)和长期记忆(MySQL)的桥接:</p>
 * <ul>
 *   <li><b>短期记忆:</b> InMemoryVectorStore (快速检索，重启丢失)</li>
 *   <li><b>长期记忆:</b> MySQL ai_knowledge_chunk 表(持久化存储，重启恢复)</li>
 *   <li><b>启动恢复:</b> 每次启动时从DB重新加载所有向量到内存</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai-agent.vector.type", havingValue = "MEMORY", matchIfMissing = true)
public class VectorStoreLoader {

    private final VectorStore vectorStore;
    private final KnowledgeDocRepository docRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public VectorStoreLoader(VectorStore vectorStore,
                             KnowledgeDocRepository docRepository,
                             NamedParameterJdbcTemplate jdbc,
                             ObjectMapper objectMapper) {
        this.vectorStore = vectorStore;
        this.docRepository = docRepository;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * 应用启动完成后，从MySQL恢复所有向量到内存
     */
    @EventListener(ApplicationReadyEvent.class)
    public void reloadVectors() {
        log.info("[VectorStoreLoader] ========== 开始从MySQL恢复长期记忆到内存向量库 ==========");
        long start = System.currentTimeMillis();

        try {
            // 查询所有已索引的文档的chunks(含向量JSON)
            String sql = """
                    SELECT c.id, c.doc_id, c.chunk_index, c.chunk_text, c.vector_json, c.token_count,
                           d.kb_name, d.title
                    FROM ai_knowledge_chunk c
                    JOIN ai_knowledge_doc d ON c.doc_id = d.id
                    WHERE d.status = 'INDEXED' AND c.vector_json IS NOT NULL
                    ORDER BY c.doc_id, c.chunk_index
                    """;

            List<Map<String, Object>> rows = jdbc.getJdbcTemplate().queryForList(sql);

            if (rows.isEmpty()) {
                log.info("[VectorStoreLoader] MySQL中无持久化向量数据，内存向量库保持清空状态");
                return;
            }

            int loaded = 0;
            int skipped = 0;

            for (Map<String, Object> row : rows) {
                try {
                    Long chunkId = ((Number) row.get("id")).longValue();
                    Long docId = ((Number) row.get("doc_id")).longValue();
                    String chunkText = (String) row.get("chunk_text");
                    String vectorJson = (String) row.get("vector_json");
                    String kbName = (String) row.get("kb_name");
                    String title = (String) row.get("title");

                    if (vectorJson == null || vectorJson.isEmpty()) {
                        skipped++;
                        continue;
                    }

                    // 解析向量JSON
                    List<Double> embedding = objectMapper.readValue(vectorJson,
                            new TypeReference<List<Double>>() {});

                    if (embedding == null || embedding.isEmpty()) {
                        skipped++;
                        continue;
                    }

                    // 构建元数据
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("kbName", kbName);
                    metadata.put("docId", String.valueOf(docId));
                    metadata.put("title", title != null ? title : "");
                    metadata.put("chunkIndex", String.valueOf(row.get("chunk_index")));

                    // 构建向量文档并添加到内存存储
                    VectorDocument doc = VectorDocument.builder()
                            .id("db:" + chunkId)
                            .text(chunkText)
                            .embedding(embedding)
                            .metadata(metadata)
                            .build();

                    vectorStore.add(doc);
                    loaded++;

                } catch (Exception e) {
                    skipped++;
                    log.debug("[VectorStoreLoader] 跳过一条chunk: {}", e.getMessage());
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            log.info("[VectorStoreLoader] ========== 向量恢复完成: 加载{}条, 跳过{}条, 耗时{}ms ==========",
                    loaded, skipped, elapsed);

        } catch (Exception e) {
            log.error("[VectorStoreLoader] 向量恢复失败", e);
        }
    }
}
