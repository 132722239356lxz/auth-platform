package com.liang.xz.aiagent.repository;

import com.liang.xz.aiagent.entity.KnowledgeChunk;
import com.liang.xz.aiagent.entity.KnowledgeDoc;
import com.liang.xz.aiagent.entity.SearchRecord;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>知识库文档 + 搜索记录 Repository</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Repository
public class KnowledgeDocRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public KnowledgeDocRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ===================== 知识库文档 =====================

    public KnowledgeDoc save(KnowledgeDoc doc) {
        if (doc.getId() == null) {
            String sql = """
                    INSERT INTO ai_knowledge_doc (kb_name, title, content, content_type,
                      file_name, file_size, chunk_count, splitter_type, chunk_size, overlap, status)
                    VALUES (:kbName, :title, :content, :contentType, :fileName, :fileSize, :chunkCount,
                      :splitterType, :chunkSize, :overlap, :status)""";
            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(sql, docParams(doc), keyHolder);
            doc.setId(keyHolder.getKey().longValue());
        } else {
            String sql = """
                    UPDATE ai_knowledge_doc SET kb_name=:kbName, title=:title, content=:content,
                      content_type=:contentType, file_name=:fileName, file_size=:fileSize,
                      chunk_count=:chunkCount, splitter_type=:splitterType, chunk_size=:chunkSize,
                      overlap=:overlap, status=:status WHERE id=:id""";
            MapSqlParameterSource p = docParams(doc);
            p.addValue("id", doc.getId());
            jdbc.update(sql, p);
        }
        return doc;
    }

    public void updateStatus(Long id, String status, Integer chunkCount) {
        jdbc.update("""
                UPDATE ai_knowledge_doc SET status = :status, chunk_count = :chunkCount
                WHERE id = :id""",
                Map.of("id", id, "status", status, "chunkCount", chunkCount != null ? chunkCount : 0));
    }

    public KnowledgeDoc findById(Long id) {
        List<KnowledgeDoc> list = jdbc.query(
                "SELECT * FROM ai_knowledge_doc WHERE id = :id",
                Map.of("id", id), new KnowledgeDocRowMapper());
        return list.isEmpty() ? null : list.get(0);
    }

    public List<KnowledgeDoc> findByKbName(String kbName) {
        return jdbc.query(
                "SELECT * FROM ai_knowledge_doc WHERE kb_name = :kbName ORDER BY created_at DESC",
                Map.of("kbName", kbName), new KnowledgeDocRowMapper());
    }

    public List<KnowledgeDoc> listAll() {
        return jdbc.query(
                "SELECT * FROM ai_knowledge_doc ORDER BY created_at DESC",
                new KnowledgeDocRowMapper());
    }

    public List<String> distinctKbNames() {
        return jdbc.queryForList(
                "SELECT DISTINCT kb_name FROM ai_knowledge_doc ORDER BY kb_name",
                Map.of(), String.class);
    }

    public long countByKbName(String kbName) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_knowledge_doc WHERE kb_name = :kbName",
                Map.of("kbName", kbName), Long.class);
        return count != null ? count : 0;
    }

    public void deleteById(Long id) {
        jdbc.update("DELETE FROM ai_knowledge_doc WHERE id = :id", Map.of("id", id));
    }

    public void deleteByKbName(String kbName) {
        jdbc.update("DELETE FROM ai_knowledge_doc WHERE kb_name = :kbName", Map.of("kbName", kbName));
    }

    // ===================== Chunk =====================

    public void saveChunks(List<KnowledgeChunk> chunks) {
        String sql = """
                INSERT INTO ai_knowledge_chunk (doc_id, chunk_index, chunk_text, vector_json, token_count)
                VALUES (:docId, :chunkIndex, :chunkText, :vectorJson, :tokenCount)""";
        for (KnowledgeChunk chunk : chunks) {
            jdbc.update(sql, Map.of(
                    "docId", chunk.getDocId(),
                    "chunkIndex", chunk.getChunkIndex(),
                    "chunkText", chunk.getChunkText(),
                    "vectorJson", chunk.getVectorJson(),
                    "tokenCount", chunk.getTokenCount() != null ? chunk.getTokenCount() : 0
            ));
        }
    }

    public List<KnowledgeChunk> findChunksByDocId(Long docId) {
        return jdbc.query(
                "SELECT * FROM ai_knowledge_chunk WHERE doc_id = :docId ORDER BY chunk_index",
                Map.of("docId", docId), new ChunkRowMapper());
    }

    /**
     * 查询所有已索引文档的chunks(含向量JSON)，用于启动时恢复向量库
     */
    public List<Map<String, Object>> listAllIndexedChunks() {
        String sql = """
                SELECT c.id, c.doc_id, c.chunk_index, c.chunk_text, c.vector_json, c.token_count,
                       d.kb_name, d.title
                FROM ai_knowledge_chunk c
                JOIN ai_knowledge_doc d ON c.doc_id = d.id
                WHERE d.status = 'INDEXED' AND c.vector_json IS NOT NULL
                ORDER BY c.doc_id, c.chunk_index
                """;
        return jdbc.getJdbcTemplate().queryForList(sql);
    }

    public void deleteChunksByDocId(Long docId) {
        jdbc.update("DELETE FROM ai_knowledge_chunk WHERE doc_id = :docId", Map.of("docId", docId));
    }

    // ===================== 搜索记录 =====================

    public void saveSearchRecord(SearchRecord record) {
        String sql = """
                INSERT INTO ai_search_record (query_text, search_type, result_count,
                  results_json, user_id, ip_address, latency_ms)
                VALUES (:queryText, :searchType, :resultCount, :resultsJson,
                  :userId, :ipAddress, :latencyMs)""";
        Map<String, Object> params = new HashMap<>(8);
        params.put("queryText", record.getQueryText());
        params.put("searchType", record.getSearchType());
        params.put("resultCount", record.getResultCount());
        params.put("resultsJson", record.getResultsJson());
        params.put("userId", record.getUserId());
        params.put("ipAddress", record.getIpAddress());
        params.put("latencyMs", record.getLatencyMs());
        jdbc.update(sql, params);
    }

    public List<SearchRecord> findRecentSearches(int limit) {
        return jdbc.query(
                "SELECT * FROM ai_search_record ORDER BY created_at DESC LIMIT :limit",
                Map.of("limit", limit), new SearchRecordRowMapper());
    }

    // ===================== RowMapper =====================

    private MapSqlParameterSource docParams(KnowledgeDoc d) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("kbName", d.getKbName());
        p.addValue("title", d.getTitle());
        p.addValue("content", d.getContent());
        p.addValue("contentType", d.getContentType());
        p.addValue("fileName", d.getFileName());
        p.addValue("fileSize", d.getFileSize() != null ? d.getFileSize() : 0L);
        p.addValue("chunkCount", d.getChunkCount() != null ? d.getChunkCount() : 0);
        p.addValue("splitterType", d.getSplitterType() != null ? d.getSplitterType() : "PARAGRAPH");
        p.addValue("chunkSize", d.getChunkSize() != null ? d.getChunkSize() : 500);
        p.addValue("overlap", d.getOverlap() != null ? d.getOverlap() : 50);
        p.addValue("status", d.getStatus() != null ? d.getStatus() : "PENDING");
        return p;
    }

    private static class KnowledgeDocRowMapper implements RowMapper<KnowledgeDoc> {
        @Override
        public KnowledgeDoc mapRow(ResultSet rs, int rowNum) throws SQLException {
            return KnowledgeDoc.builder()
                    .id(rs.getLong("id"))
                    .kbName(rs.getString("kb_name"))
                    .title(rs.getString("title"))
                    .content(rs.getString("content"))
                    .contentType(rs.getString("content_type"))
                    .fileName(rs.getString("file_name"))
                    .fileSize(rs.getLong("file_size"))
                    .chunkCount(rs.getInt("chunk_count"))
                    .splitterType(rs.getString("splitter_type"))
                    .chunkSize(rs.getInt("chunk_size"))
                    .overlap(rs.getInt("overlap"))
                    .status(rs.getString("status"))
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                    .build();
        }
    }

    private static class ChunkRowMapper implements RowMapper<KnowledgeChunk> {
        @Override
        public KnowledgeChunk mapRow(ResultSet rs, int rowNum) throws SQLException {
            return KnowledgeChunk.builder()
                    .id(rs.getLong("id"))
                    .docId(rs.getLong("doc_id"))
                    .chunkIndex(rs.getInt("chunk_index"))
                    .chunkText(rs.getString("chunk_text"))
                    .vectorJson(rs.getString("vector_json"))
                    .tokenCount(rs.getInt("token_count"))
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .build();
        }
    }

    private static class SearchRecordRowMapper implements RowMapper<SearchRecord> {
        @Override
        public SearchRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return SearchRecord.builder()
                    .id(rs.getLong("id"))
                    .queryText(rs.getString("query_text"))
                    .searchType(rs.getString("search_type"))
                    .resultCount(rs.getInt("result_count"))
                    .resultsJson(rs.getString("results_json"))
                    .userId(rs.getString("user_id"))
                    .ipAddress(rs.getString("ip_address"))
                    .latencyMs(rs.getLong("latency_ms"))
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .build();
        }
    }
}
