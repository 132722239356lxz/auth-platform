package com.liang.xz.aiagent.repository;

import com.liang.xz.aiagent.entity.KnowledgeBase;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * <p>知识库 Repository</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Repository
public class KnowledgeBaseRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public KnowledgeBaseRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public KnowledgeBase save(KnowledgeBase kb) {
        if (kb.getId() == null) {
            String sql = """
                    INSERT INTO ai_knowledge_base (name, description, status)
                    VALUES (:name, :description, :status)""";
            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(sql, kbParams(kb), keyHolder);
            kb.setId(keyHolder.getKey().longValue());
        } else {
            String sql = """
                    UPDATE ai_knowledge_base SET name=:name, description=:description,
                    status=:status WHERE id=:id""";
            MapSqlParameterSource p = kbParams(kb);
            p.addValue("id", kb.getId());
            jdbc.update(sql, p);
        }
        return kb;
    }

    public Optional<KnowledgeBase> findById(Long id) {
        List<KnowledgeBase> list = jdbc.query(
                "SELECT * FROM ai_knowledge_base WHERE id = :id",
                Map.of("id", id), new KnowledgeBaseRowMapper());
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<KnowledgeBase> findByName(String name) {
        List<KnowledgeBase> list = jdbc.query(
                "SELECT * FROM ai_knowledge_base WHERE name = :name",
                Map.of("name", name), new KnowledgeBaseRowMapper());
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<KnowledgeBase> listAll() {
        return jdbc.query(
                """
                        SELECT b.id, b.name, b.description, b.status, b.created_at, b.updated_at,
                               COALESCE(d.cnt, 0) AS doc_count
                        FROM ai_knowledge_base b
                        LEFT JOIN (SELECT kb_name, COUNT(*) AS cnt FROM ai_knowledge_doc GROUP BY kb_name) d
                            ON b.name = d.kb_name
                        ORDER BY b.created_at DESC
                        """,
                new KnowledgeBaseRowMapper());
    }

    public void deleteById(Long id) {
        jdbc.update("DELETE FROM ai_knowledge_base WHERE id = :id", Map.of("id", id));
    }

    public boolean existsByName(String name) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_knowledge_base WHERE name = :name",
                Map.of("name", name), Integer.class);
        return count != null && count > 0;
    }

    private MapSqlParameterSource kbParams(KnowledgeBase kb) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("name", kb.getName());
        p.addValue("description", kb.getDescription());
        p.addValue("status", kb.getStatus() != null ? kb.getStatus() : "ACTIVE");
        return p;
    }

    private static class KnowledgeBaseRowMapper implements RowMapper<KnowledgeBase> {
        @Override
        public KnowledgeBase mapRow(ResultSet rs, int rowNum) throws SQLException {
            return KnowledgeBase.builder()
                    .id(rs.getLong("id"))
                    .name(rs.getString("name"))
                    .description(rs.getString("description"))
                    .docCount(rs.getInt("doc_count"))
                    .status(rs.getString("status"))
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                    .build();
        }
    }
}
