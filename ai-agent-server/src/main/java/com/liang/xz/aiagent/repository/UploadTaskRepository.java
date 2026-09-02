package com.liang.xz.aiagent.repository;

import com.liang.xz.aiagent.entity.UploadTask;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * <p>上传任务 Repository</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Repository
public class UploadTaskRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public UploadTaskRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UploadTask save(UploadTask task) {
        String sql = """
                INSERT INTO ai_upload_task (id, doc_id, kb_name, file_name, file_size, status, progress, error_msg)
                VALUES (:id, :docId, :kbName, :fileName, :fileSize, :status, :progress, :errorMsg)
                ON DUPLICATE KEY UPDATE
                    doc_id = VALUES(doc_id),
                    kb_name = VALUES(kb_name),
                    file_name = VALUES(file_name),
                    file_size = VALUES(file_size),
                    status = VALUES(status),
                    progress = VALUES(progress),
                    error_msg = VALUES(error_msg)""";
        jdbc.update(sql, taskParams(task));
        return task;
    }

    public void updateProgress(String taskId, Integer progress, String status) {
        jdbc.update("""
                UPDATE ai_upload_task SET progress = :progress, status = :status WHERE id = :id""",
                Map.of("id", taskId, "progress", progress != null ? progress : 0, "status", status));
    }

    public void updateDocId(String taskId, Long docId) {
        jdbc.update("UPDATE ai_upload_task SET doc_id = :docId WHERE id = :id",
                Map.of("id", taskId, "docId", docId));
    }

    public void markFailed(String taskId, String errorMsg) {
        jdbc.update("""
                        UPDATE ai_upload_task SET status = 'FAILED', progress = 100, error_msg = :errorMsg
                        WHERE id = :id""",
                Map.of("id", taskId, "errorMsg", errorMsg != null ? errorMsg : ""));
    }

    public Optional<UploadTask> findById(String taskId) {
        List<UploadTask> list = jdbc.query(
                "SELECT * FROM ai_upload_task WHERE id = :id",
                Map.of("id", taskId), new UploadTaskRowMapper());
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<UploadTask> findByDocId(Long docId) {
        return jdbc.query(
                "SELECT * FROM ai_upload_task WHERE doc_id = :docId ORDER BY created_at DESC",
                Map.of("docId", docId), new UploadTaskRowMapper());
    }

    public List<UploadTask> findRecent(int limit) {
        return jdbc.query(
                "SELECT * FROM ai_upload_task ORDER BY created_at DESC LIMIT :limit",
                Map.of("limit", limit), new UploadTaskRowMapper());
    }

    public void deleteById(String taskId) {
        jdbc.update("DELETE FROM ai_upload_task WHERE id = :id", Map.of("id", taskId));
    }

    public void deleteByDocId(Long docId) {
        jdbc.update("DELETE FROM ai_upload_task WHERE doc_id = :docId", Map.of("docId", docId));
    }

    private MapSqlParameterSource taskParams(UploadTask task) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", task.getId());
        p.addValue("docId", task.getDocId());
        p.addValue("kbName", task.getKbName());
        p.addValue("fileName", task.getFileName());
        p.addValue("fileSize", task.getFileSize() != null ? task.getFileSize() : 0L);
        p.addValue("status", task.getStatus() != null ? task.getStatus() : "UPLOADING");
        p.addValue("progress", task.getProgress() != null ? task.getProgress() : 0);
        p.addValue("errorMsg", task.getErrorMsg());
        return p;
    }

    private static class UploadTaskRowMapper implements RowMapper<UploadTask> {
        @Override
        public UploadTask mapRow(ResultSet rs, int rowNum) throws SQLException {
            return UploadTask.builder()
                    .id(rs.getString("id"))
                    .docId(rs.getLong("doc_id"))
                    .kbName(rs.getString("kb_name"))
                    .fileName(rs.getString("file_name"))
                    .fileSize(rs.getLong("file_size"))
                    .status(rs.getString("status"))
                    .progress(rs.getInt("progress"))
                    .errorMsg(rs.getString("error_msg"))
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                    .build();
        }
    }
}
