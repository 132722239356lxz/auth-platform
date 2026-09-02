package com.liang.xz.message.service;

import com.liang.xz.message.channel.RabbitMQSender;
import com.liang.xz.message.dto.SubsystemIncidentReport;
import com.liang.xz.message.entity.SubsystemIncident;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>子系统异常反馈服务</p>
 *
 * <p>职责:</p>
 * <ol>
 *   <li>接收子系统上报的异常(错误/健康/安全), 落库 {@code subsystem_incident}</li>
 *   <li>通过消息总线(RabbitMQ)发布 {@code SUBSYSTEM_INCIDENT} 事件, 反馈到门户</li>
 *   <li>提供查询/统计(门户红点)/处理接口</li>
 * </ol>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubsystemIncidentService {

    private final JdbcTemplate jdbcTemplate;
    private final RabbitMQSender rabbitMQSender;

    private static final RowMapper<SubsystemIncident> ROW_MAPPER = (ResultSet rs, int rowNum) -> {
        SubsystemIncident incident = new SubsystemIncident();
        incident.setId(rs.getLong("id"));
        incident.setSubsystem(rs.getString("subsystem"));
        incident.setSubsystemName(rs.getString("subsystem_name"));
        incident.setIncidentType(rs.getString("incident_type"));
        incident.setLevel(rs.getString("level"));
        incident.setTitle(rs.getString("title"));
        incident.setContent(rs.getString("content"));
        incident.setStackTrace(rs.getString("stack_trace"));
        incident.setTraceId(rs.getString("trace_id"));
        incident.setStatus(rs.getString("status"));
        incident.setReportedAt(toLdt(rs.getTimestamp("reported_at")));
        incident.setResolvedAt(toLdt(rs.getTimestamp("resolved_at")));
        incident.setResolver(rs.getString("resolver"));
        incident.setResolveNote(rs.getString("resolve_note"));
        incident.setCreateTime(toLdt(rs.getTimestamp("create_time")));
        return incident;
    };

    /**
     * 上报子系统异常, 落库并发布事件到门户。
     */
    public Long report(SubsystemIncidentReport report) {
        String level = StringUtils.hasText(report.getLevel()) ? report.getLevel().toUpperCase() : "ERROR";
        String incidentType = StringUtils.hasText(report.getIncidentType()) ? report.getIncidentType() : "ERROR";
        LocalDateTime now = LocalDateTime.now();

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO subsystem_incident (subsystem, subsystem_name, incident_type, level, "
                            + "title, content, stack_trace, trace_id, status, reported_at, create_time) "
                            + "VALUES (?,?,?,?,?,?,?,?, 'PENDING', ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, report.getSubsystem());
            ps.setString(2, report.getSubsystemName());
            ps.setString(3, incidentType);
            ps.setString(4, level);
            ps.setString(5, report.getTitle());
            ps.setString(6, report.getContent());
            ps.setString(7, report.getStackTrace());
            ps.setString(8, report.getTraceId());
            ps.setTimestamp(9, Timestamp.valueOf(now));
            ps.setTimestamp(10, Timestamp.valueOf(now));
            return ps;
        }, keyHolder);

        Long id = keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;

        // 反馈到门户: 通过消息总线发布子系统异常事件(复用 RabbitMQ)
        try {
            String payload = String.format(
                    "{\"incidentId\":%s,\"subsystem\":\"%s\",\"level\":\"%s\",\"title\":\"%s\"}",
                    id, report.getSubsystem(), level, report.getTitle());
            rabbitMQSender.publishEvent("SUBSYSTEM_INCIDENT", payload, List.of("auth-platform"));
        } catch (Exception e) {
            log.warn("[Incident] 消息总线发布失败(已落库): {}", e.getMessage());
        }
        log.info("[Incident] 子系统异常已上报: id={}, subsystem={}, level={}", id, report.getSubsystem(), level);
        return id;
    }

    /**
     * 查询异常列表(门户告警页) —— 支持分页。
     */
    public IncidentQueryResult list(String subsystem, String level, String status, int page, int size) {
        StringBuilder whereSql = new StringBuilder("WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(subsystem)) {
            whereSql.append(" AND subsystem = ?");
            args.add(subsystem);
        }
        if (StringUtils.hasText(level)) {
            whereSql.append(" AND level = ?");
            args.add(level.toUpperCase());
        }
        if (StringUtils.hasText(status)) {
            whereSql.append(" AND status = ?");
            args.add(status.toUpperCase());
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM subsystem_incident " + whereSql, Long.class, args.toArray());

        int offset = Math.max(0, page - 1) * size;
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(offset);
        queryArgs.add(size);
        List<SubsystemIncident> list = jdbcTemplate.query(
                "SELECT * FROM subsystem_incident " + whereSql + " ORDER BY create_time DESC LIMIT ?, ?",
                ROW_MAPPER, queryArgs.toArray());

        return new IncidentQueryResult(total != null ? total : 0, list);
    }

    /**
     * 统计(门户红点: 未处理数 / 紧急未处理数)。
     */
    public SubsystemIncidentStats stats() {
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM subsystem_incident", Integer.class);
        Integer pending = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM subsystem_incident WHERE status = 'PENDING'", Integer.class);
        Integer criticalPending = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM subsystem_incident WHERE status = 'PENDING' AND level = 'CRITICAL'",
                Integer.class);
        return new SubsystemIncidentStats(
                total != null ? total : 0,
                pending != null ? pending : 0,
                criticalPending != null ? criticalPending : 0);
    }

    /**
     * 标记已处理。
     */
    public void resolve(Long id, String resolver, String note) {
        jdbcTemplate.update(
                "UPDATE subsystem_incident SET status = 'RESOLVED', resolver = ?, resolve_note = ?, "
                        + "resolved_at = ? WHERE id = ?",
                resolver, note, Timestamp.valueOf(LocalDateTime.now()), id);
    }

    /**
     * 忽略异常。
     */
    public void ignore(Long id, String resolver, String note) {
        jdbcTemplate.update(
                "UPDATE subsystem_incident SET status = 'IGNORED', resolver = ?, resolve_note = ?, "
                        + "resolved_at = ? WHERE id = ?",
                resolver, note, Timestamp.valueOf(LocalDateTime.now()), id);
    }

    /**
     * 删除异常记录。
     */
    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM subsystem_incident WHERE id = ?", id);
    }

    private static LocalDateTime toLdt(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    /** 异常统计快照 */
    @lombok.Data
    public static class SubsystemIncidentStats {
        private final int total;
        private final int pending;
        private final int criticalPending;
    }

    /** 分页查询结果 */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class IncidentQueryResult {
        private long total;
        private List<SubsystemIncident> list;
    }
}
