package com.liang.xz.aiagent.repository;

import com.liang.xz.aiagent.entity.AnalysisAlert;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * <p>分析预警记录 Repository</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Repository
public class AlertRecordRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public AlertRecordRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<AnalysisAlert> ROW_MAPPER = new AlertRowMapper();

    public AnalysisAlert save(AnalysisAlert alert) {
        if (alert.getId() == null) {
            return insert(alert);
        } else {
            return update(alert);
        }
    }

    private AnalysisAlert insert(AnalysisAlert alert) {
        String sql = """
                INSERT INTO ai_analysis_alert (alert_name, analysis_type, data_source,
                  alert_level, alert_content, analysis_detail, suggestion, metrics_json,
                  is_read, resolved)
                VALUES (:alertName, :analysisType, :dataSource, :alertLevel, :alertContent,
                  :analysisDetail, :suggestion, :metricsJson, :isRead, :resolved)""";

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, toParams(alert), keyHolder);
        alert.setId(keyHolder.getKey().longValue());
        return alert;
    }

    private AnalysisAlert update(AnalysisAlert alert) {
        String sql = """
                UPDATE ai_analysis_alert SET alert_name=:alertName, alert_level=:alertLevel,
                  alert_content=:alertContent, analysis_detail=:analysisDetail,
                  suggestion=:suggestion, is_read=:isRead, resolved=:resolved,
                  resolved_at=:resolvedAt, resolved_by=:resolvedBy
                WHERE id=:id""";
        jdbc.update(sql, toParams(alert));
        return alert;
    }

    public List<AnalysisAlert> findUnresolved() {
        return jdbc.query("SELECT * FROM ai_analysis_alert WHERE resolved = 0 ORDER BY created_at DESC",
                ROW_MAPPER);
    }

    public List<AnalysisAlert> findByAlertLevel(String level) {
        return jdbc.query("SELECT * FROM ai_analysis_alert WHERE alert_level = :level AND resolved = 0",
                Map.of("level", level), ROW_MAPPER);
    }

    public List<AnalysisAlert> findRecent(int limit) {
        return jdbc.query("SELECT * FROM ai_analysis_alert ORDER BY created_at DESC LIMIT :limit",
                Map.of("limit", limit), ROW_MAPPER);
    }

    public List<AnalysisAlert> findByType(String analysisType) {
        return jdbc.query("SELECT * FROM ai_analysis_alert WHERE analysis_type = :type ORDER BY created_at DESC",
                Map.of("type", analysisType), ROW_MAPPER);
    }

    public List<AnalysisAlert> queryAlerts(String keyword, String level, Boolean resolved, String analysisType,
                                           LocalDateTime startTime, LocalDateTime endTime, int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ai_analysis_alert WHERE 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (keyword != null && !keyword.isBlank()) {
            sql.append("AND (alert_name LIKE :keyword OR alert_content LIKE :keyword OR suggestion LIKE :keyword) ");
            params.addValue("keyword", "%" + keyword.trim() + "%");
        }
        if (level != null && !level.isBlank()) {
            sql.append("AND alert_level = :level ");
            params.addValue("level", level.trim().toUpperCase());
        }
        if (resolved != null) {
            sql.append("AND resolved = :resolved ");
            params.addValue("resolved", resolved);
        }
        if (analysisType != null && !analysisType.isBlank()) {
            sql.append("AND analysis_type = :analysisType ");
            params.addValue("analysisType", analysisType.trim().toUpperCase());
        }
        if (startTime != null) {
            sql.append("AND created_at >= :startTime ");
            params.addValue("startTime", Timestamp.valueOf(startTime));
        }
        if (endTime != null) {
            sql.append("AND created_at <= :endTime ");
            params.addValue("endTime", Timestamp.valueOf(endTime));
        }

        sql.append("ORDER BY created_at DESC LIMIT :offset, :limit");
        params.addValue("offset", (page - 1) * size);
        params.addValue("limit", size);
        return jdbc.query(sql.toString(), params, ROW_MAPPER);
    }

    public int countAlerts(String keyword, String level, Boolean resolved, String analysisType,
                           LocalDateTime startTime, LocalDateTime endTime) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ai_analysis_alert WHERE 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (keyword != null && !keyword.isBlank()) {
            sql.append("AND (alert_name LIKE :keyword OR alert_content LIKE :keyword OR suggestion LIKE :keyword) ");
            params.addValue("keyword", "%" + keyword.trim() + "%");
        }
        if (level != null && !level.isBlank()) {
            sql.append("AND alert_level = :level ");
            params.addValue("level", level.trim().toUpperCase());
        }
        if (resolved != null) {
            sql.append("AND resolved = :resolved ");
            params.addValue("resolved", resolved);
        }
        if (analysisType != null && !analysisType.isBlank()) {
            sql.append("AND analysis_type = :analysisType ");
            params.addValue("analysisType", analysisType.trim().toUpperCase());
        }
        if (startTime != null) {
            sql.append("AND created_at >= :startTime ");
            params.addValue("startTime", Timestamp.valueOf(startTime));
        }
        if (endTime != null) {
            sql.append("AND created_at <= :endTime ");
            params.addValue("endTime", Timestamp.valueOf(endTime));
        }

        Integer count = jdbc.queryForObject(sql.toString(), params, Integer.class);
        return count != null ? count : 0;
    }


    public int countUnresolved() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_analysis_alert WHERE resolved = 0",
                Map.of(), Integer.class);
        return count != null ? count : 0;
    }

    public int countUnresolvedByLevel(String level) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_analysis_alert WHERE resolved = 0 AND alert_level = :level",
                Map.of("level", level), Integer.class);
        return count != null ? count : 0;
    }

    public void resolve(Long id, String resolvedBy) {
        jdbc.update("""
                UPDATE ai_analysis_alert SET resolved = 1, resolved_at = NOW(),
                  resolved_by = :resolvedBy WHERE id = :id""",
                Map.of("id", id, "resolvedBy", resolvedBy));
    }

    public void markRead(Long id) {
        jdbc.update("UPDATE ai_analysis_alert SET is_read = 1 WHERE id = :id",
                Map.of("id", id));
    }

    private MapSqlParameterSource toParams(AnalysisAlert a) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (a.getId() != null) p.addValue("id", a.getId());
        p.addValue("alertName", a.getAlertName());
        p.addValue("analysisType", a.getAnalysisType());
        p.addValue("dataSource", a.getDataSource());
        p.addValue("alertLevel", a.getAlertLevel());
        p.addValue("alertContent", a.getAlertContent());
        p.addValue("analysisDetail", a.getAnalysisDetail());
        p.addValue("suggestion", a.getSuggestion());
        p.addValue("metricsJson", a.getMetricsJson());
        p.addValue("isRead", a.getIsRead() != null ? a.getIsRead() : false);
        p.addValue("resolved", a.getResolved() != null ? a.getResolved() : false);
        p.addValue("resolvedAt", a.getResolvedAt() != null ? Timestamp.valueOf(a.getResolvedAt()) : null);
        p.addValue("resolvedBy", a.getResolvedBy());
        return p;
    }

    private static class AlertRowMapper implements RowMapper<AnalysisAlert> {
        @Override
        public AnalysisAlert mapRow(ResultSet rs, int rowNum) throws SQLException {
            return AnalysisAlert.builder()
                    .id(rs.getLong("id"))
                    .alertName(rs.getString("alert_name"))
                    .analysisType(rs.getString("analysis_type"))
                    .dataSource(rs.getString("data_source"))
                    .alertLevel(rs.getString("alert_level"))
                    .alertContent(rs.getString("alert_content"))
                    .analysisDetail(rs.getString("analysis_detail"))
                    .suggestion(rs.getString("suggestion"))
                    .metricsJson(rs.getString("metrics_json"))
                    .isRead(rs.getBoolean("is_read"))
                    .resolved(rs.getBoolean("resolved"))
                    .resolvedAt(rs.getTimestamp("resolved_at") != null
                            ? rs.getTimestamp("resolved_at").toLocalDateTime() : null)
                    .resolvedBy(rs.getString("resolved_by"))
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .build();
        }
    }
}
