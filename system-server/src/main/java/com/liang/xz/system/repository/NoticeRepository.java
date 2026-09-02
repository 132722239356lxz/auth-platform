package com.liang.xz.system.repository;

import com.liang.xz.system.dto.NoticePageQuery;
import com.liang.xz.system.entity.SysNoticeEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 系统公告 Repository
 */
@Repository
public class NoticeRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<SysNoticeEntity> rowMapper = (rs, rn) -> SysNoticeEntity.builder()
            .id(rs.getLong("id"))
            .title(rs.getString("title"))
            .content(rs.getString("content"))
            .noticeType(rs.getString("notice_type"))
            .priority(rs.getObject("priority") != null ? rs.getInt("priority") : null)
            .publisherId(rs.getObject("publisher_id") != null ? rs.getLong("publisher_id") : null)
            .publisherName(rs.getString("publisher_name"))
            .top(rs.getBoolean("top"))
            .publishTime(toLocal(rs.getTimestamp("publish_time")))
            .expireTime(toLocal(rs.getTimestamp("expire_time")))
            .enabled(rs.getBoolean("enabled"))
            .readCount(rs.getObject("read_count") != null ? rs.getLong("read_count") : 0L)
            .createTime(toLocal(rs.getTimestamp("create_time")))
            .updateTime(toLocal(rs.getTimestamp("update_time")))
            .build();

    public NoticeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long insert(SysNoticeEntity entity) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO sys_notice (title, content, notice_type, priority, publisher_id, publisher_name, " +
                    "top, publish_time, expire_time, enabled, read_count, create_time, update_time) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, entity.getTitle());
            ps.setString(2, entity.getContent());
            ps.setString(3, entity.getNoticeType());
            ps.setObject(4, entity.getPriority());
            ps.setObject(5, entity.getPublisherId());
            ps.setString(6, entity.getPublisherName());
            ps.setBoolean(7, entity.getTop() != null ? entity.getTop() : false);
            ps.setTimestamp(8, toTimestamp(entity.getPublishTime()));
            ps.setTimestamp(9, toTimestamp(entity.getExpireTime()));
            ps.setBoolean(10, entity.getEnabled() != null ? entity.getEnabled() : true);
            ps.setLong(11, entity.getReadCount() != null ? entity.getReadCount() : 0L);
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key != null ? key.longValue() : null;
    }

    public int update(SysNoticeEntity entity) {
        return jdbcTemplate.update(
                "UPDATE sys_notice SET title=?, content=?, notice_type=?, priority=?, publisher_id=?, " +
                "publisher_name=?, top=?, publish_time=?, expire_time=?, enabled=?, update_time=NOW() WHERE id=?",
                entity.getTitle(), entity.getContent(), entity.getNoticeType(), entity.getPriority(),
                entity.getPublisherId(), entity.getPublisherName(), entity.getTop(),
                toTimestamp(entity.getPublishTime()), toTimestamp(entity.getExpireTime()),
                entity.getEnabled(), entity.getId());
    }

    public int deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM sys_notice WHERE id = ?", id);
    }

    public Optional<SysNoticeEntity> findById(Long id) {
        List<SysNoticeEntity> list = jdbcTemplate.query(
                "SELECT * FROM sys_notice WHERE id = ?", rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<SysNoticeEntity> findPublished(int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM sys_notice WHERE enabled = true " +
                "AND (publish_time IS NULL OR publish_time <= NOW()) " +
                "AND (expire_time IS NULL OR expire_time > NOW()) " +
                "ORDER BY top DESC, publish_time DESC, create_time DESC LIMIT ?",
                rowMapper, limit);
    }

    public List<SysNoticeEntity> findPage(NoticePageQuery query) {
        StringBuilder sql = new StringBuilder("SELECT * FROM sys_notice WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (query.getKeyword() != null && !query.getKeyword().trim().isEmpty()) {
            sql.append(" AND (title LIKE ? OR content LIKE ?)");
            params.add("%" + query.getKeyword().trim() + "%");
            params.add("%" + query.getKeyword().trim() + "%");
        }
        if (query.getNoticeType() != null && !query.getNoticeType().trim().isEmpty()) {
            sql.append(" AND notice_type = ?");
            params.add(query.getNoticeType().trim());
        }
        if (query.getEnabled() != null) {
            sql.append(" AND enabled = ?");
            params.add(query.getEnabled());
        }

        sql.append(" ORDER BY top DESC, create_time DESC LIMIT ? OFFSET ?");
        int offset = (query.getPage() - 1) * query.getPageSize();
        params.add(query.getPageSize());
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), rowMapper, params.toArray());
    }

    public long count(NoticePageQuery query) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM sys_notice WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (query.getKeyword() != null && !query.getKeyword().trim().isEmpty()) {
            sql.append(" AND (title LIKE ? OR content LIKE ?)");
            params.add("%" + query.getKeyword().trim() + "%");
            params.add("%" + query.getKeyword().trim() + "%");
        }
        if (query.getNoticeType() != null && !query.getNoticeType().trim().isEmpty()) {
            sql.append(" AND notice_type = ?");
            params.add(query.getNoticeType().trim());
        }
        if (query.getEnabled() != null) {
            sql.append(" AND enabled = ?");
            params.add(query.getEnabled());
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0;
    }

    private LocalDateTime toLocal(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }

    private Timestamp toTimestamp(LocalDateTime ldt) {
        return ldt != null ? Timestamp.valueOf(ldt) : null;
    }
}
