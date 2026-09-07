package com.liang.xz.message.repository;

import com.liang.xz.message.entity.MessageRecord;
import com.liang.xz.message.entity.MessageTemplate;
import com.liang.xz.message.entity.MessageTemplateStats;
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
 * 消息 Repository
 */
@Repository
public class MessageRepository {

    private final JdbcTemplate jdbcTemplate;

    public MessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ======================== MessageRecord ========================

    private final RowMapper<MessageRecord> msgMapper = (rs, rn) -> MessageRecord.builder()
            .id(rs.getLong("id"))
            .messageId(rs.getString("message_id"))
            .messageType(rs.getString("message_type"))
            .title(rs.getString("title"))
            .content(rs.getString("content"))
            .channels(rs.getString("channels"))
            .status(rs.getString("status"))
            .sourceSystem(rs.getString("source_system"))
            .sender(rs.getString("sender"))
            .receivers(rs.getString("receivers"))
            .targetSubsystems(rs.getString("target_subsystems"))
            .businessId(rs.getString("business_id"))
            .isRead(rs.getObject("is_read") != null && rs.getBoolean("is_read"))
            .failReason(rs.getString("fail_reason"))
            .retryCount(rs.getObject("retry_count") != null ? rs.getInt("retry_count") : 0)
            .templateCode(rs.getString("template_code"))
            .createTime(toLdt(rs.getTimestamp("create_time")))
            .sendTime(toLdt(rs.getTimestamp("send_time")))
            .readTime(toLdt(rs.getTimestamp("read_time")))
            .build();

    public Long saveRecord(MessageRecord record) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO msg_record(message_id,message_type,title,content,channels,status,source_system,sender,receivers,target_subsystems,business_id,is_read,fail_reason,retry_count,template_code,create_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, record.getMessageId());
            ps.setString(2, record.getMessageType());
            ps.setString(3, record.getTitle());
            ps.setString(4, record.getContent());
            ps.setString(5, record.getChannels());
            ps.setString(6, record.getStatus() != null ? record.getStatus() : "PENDING");
            ps.setString(7, record.getSourceSystem());
            ps.setString(8, record.getSender());
            ps.setString(9, record.getReceivers());
            ps.setString(10, record.getTargetSubsystems());
            ps.setString(11, record.getBusinessId());
            ps.setBoolean(12, record.getIsRead() != null && record.getIsRead());
            ps.setString(13, record.getFailReason());
            ps.setInt(14, record.getRetryCount() != null ? record.getRetryCount() : 0);
            ps.setString(15, record.getTemplateCode());
            ps.setTimestamp(16, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void updateStatus(Long id, String status, String failReason) {
        jdbcTemplate.update("UPDATE msg_record SET status=?, fail_reason=?, send_time=? WHERE id=?",
                status, failReason, Timestamp.valueOf(LocalDateTime.now()), id);
    }

    public void updateReadStatus(Long id, boolean isRead) {
        jdbcTemplate.update("UPDATE msg_record SET is_read=?, read_time=? WHERE id=?",
                isRead, isRead ? Timestamp.valueOf(LocalDateTime.now()) : null, id);
    }

    public void incrementRetryCount(Long id) {
        jdbcTemplate.update("UPDATE msg_record SET retry_count=retry_count+1 WHERE id=?", id);
    }

    public Optional<MessageRecord> findByMessageId(String messageId) {
        List<MessageRecord> list = jdbcTemplate.query(
                "SELECT * FROM msg_record WHERE message_id = ?", msgMapper, messageId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<MessageRecord> findById(Long id) {
        List<MessageRecord> list = jdbcTemplate.query(
                "SELECT * FROM msg_record WHERE id = ?", msgMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<MessageRecord> findByReceiver(String receiver, int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM msg_record WHERE receivers LIKE ? AND (channels IS NULL OR (channels NOT LIKE ? AND channels NOT LIKE ?)) ORDER BY create_time DESC LIMIT ?",
                msgMapper, "%" + receiver + "%", "%SMS%", "%EMAIL%", limit);
    }

    public List<MessageRecord> findByBusinessId(String businessId) {
        return jdbcTemplate.query(
                "SELECT * FROM msg_record WHERE business_id = ? ORDER BY create_time DESC", msgMapper, businessId);
    }

    public List<MessageRecord> findFailedMessages(int maxRetry) {
        return jdbcTemplate.query(
                "SELECT * FROM msg_record WHERE status IN('FAILED','PENDING') AND retry_count < ? ORDER BY create_time ASC LIMIT 100",
                msgMapper, maxRetry);
    }

    public List<MessageRecord> findBySubsystem(String subsystem, int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM msg_record WHERE target_subsystems LIKE ? ORDER BY create_time DESC LIMIT ?",
                msgMapper, "%" + subsystem + "%", limit);
    }

    // ======================== MessageTemplate ========================

    private final RowMapper<MessageTemplate> tmplMapper = (rs, rn) -> MessageTemplate.builder()
            .id(rs.getLong("id"))
            .templateCode(rs.getString("template_code"))
            .templateName(rs.getString("template_name"))
            .channel(rs.getString("channel"))
            .titleTemplate(rs.getString("title_template"))
            .contentTemplate(rs.getString("content_template"))
            .variables(rs.getString("variables"))
            .status(rs.getInt("status"))
            .createdBy(rs.getString("created_by"))
            .createTime(toLdt(rs.getTimestamp("create_time")))
            .updateTime(toLdt(rs.getTimestamp("update_time")))
            .build();

    public Long saveTemplate(MessageTemplate template) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO msg_template(template_code,template_name,channel,title_template,content_template,variables,status,created_by,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, template.getTemplateCode());
            ps.setString(2, template.getTemplateName());
            ps.setString(3, template.getChannel());
            ps.setString(4, template.getTitleTemplate());
            ps.setString(5, template.getContentTemplate());
            ps.setString(6, template.getVariables());
            ps.setInt(7, template.getStatus() != null ? template.getStatus() : 1);
            ps.setString(8, template.getCreatedBy());
            ps.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
            ps.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public Optional<MessageTemplate> findByTemplateCode(String templateCode) {
        List<MessageTemplate> list = jdbcTemplate.query(
                "SELECT * FROM msg_template WHERE template_code = ? AND status = 1", tmplMapper, templateCode);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<MessageTemplate> findAllTemplates() {
        return jdbcTemplate.query("SELECT * FROM msg_template ORDER BY create_time DESC", tmplMapper);
    }

    public void updateTemplate(MessageTemplate template) {
        jdbcTemplate.update(
                "UPDATE msg_template SET template_name=?, channel=?, title_template=?, content_template=?, variables=?, status=?, update_time=? WHERE template_code=?",
                template.getTemplateName(), template.getChannel(), template.getTitleTemplate(),
                template.getContentTemplate(), template.getVariables(), template.getStatus(),
                Timestamp.valueOf(LocalDateTime.now()), template.getTemplateCode());
    }

    public void deleteTemplate(String templateCode) {
        jdbcTemplate.update("DELETE FROM msg_template WHERE template_code = ?", templateCode);
    }

    // ======================== 模板分页条件查询（联表 msg_record） ========================

    private final RowMapper<MessageTemplateStats> tmplStatsMapper = (rs, rn) -> {
        MessageTemplateStats stats = new MessageTemplateStats();
        stats.setId(rs.getLong("id"));
        stats.setTemplateCode(rs.getString("template_code"));
        stats.setTemplateName(rs.getString("template_name"));
        stats.setChannel(rs.getString("channel"));
        stats.setTitleTemplate(rs.getString("title_template"));
        stats.setContentTemplate(rs.getString("content_template"));
        stats.setVariables(rs.getString("variables"));
        stats.setStatus(rs.getInt("status"));
        stats.setCreatedBy(rs.getString("created_by"));
        stats.setCreateTime(toLdt(rs.getTimestamp("create_time")));
        stats.setUpdateTime(toLdt(rs.getTimestamp("update_time")));
        stats.setUsageCount(rs.getObject("usage_count") != null ? rs.getLong("usage_count") : 0L);
        stats.setLastUsedTime(toLdt(rs.getTimestamp("last_used_time")));
        return stats;
    };

    public TemplateQueryResult queryTemplates(TemplateQueryParams params) {
        StringBuilder whereSql = new StringBuilder("WHERE 1=1");
        List<Object> countArgs = new ArrayList<>();
        List<Object> listArgs = new ArrayList<>();

        if (params.getTemplateCode() != null && !params.getTemplateCode().isEmpty()) {
            whereSql.append(" AND t.template_code LIKE ?");
            String code = "%" + params.getTemplateCode() + "%";
            countArgs.add(code);
            listArgs.add(code);
        }
        if (params.getKeyword() != null && !params.getKeyword().isEmpty()) {
            whereSql.append(" AND (t.template_code LIKE ? OR t.template_name LIKE ?)");
            String kw = "%" + params.getKeyword() + "%";
            countArgs.add(kw);
            countArgs.add(kw);
            listArgs.add(kw);
            listArgs.add(kw);
        }
        if (params.getChannel() != null && !params.getChannel().isEmpty()) {
            whereSql.append(" AND t.channel = ?");
            countArgs.add(params.getChannel());
            listArgs.add(params.getChannel());
        }
        if (params.getStatus() != null) {
            whereSql.append(" AND t.status = ?");
            countArgs.add(params.getStatus());
            listArgs.add(params.getStatus());
        }
        if (params.getCreatedBy() != null && !params.getCreatedBy().isEmpty()) {
            whereSql.append(" AND t.created_by LIKE ?");
            String cb = "%" + params.getCreatedBy() + "%";
            countArgs.add(cb);
            listArgs.add(cb);
        }
        if (params.getStartTime() != null) {
            whereSql.append(" AND t.create_time >= ?");
            countArgs.add(Timestamp.valueOf(params.getStartTime()));
            listArgs.add(Timestamp.valueOf(params.getStartTime()));
        }
        if (params.getEndTime() != null) {
            whereSql.append(" AND t.create_time <= ?");
            countArgs.add(Timestamp.valueOf(params.getEndTime()));
            listArgs.add(Timestamp.valueOf(params.getEndTime()));
        }

        // 使用 DISTINCT 避免 LEFT JOIN 导致计数膨胀
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT t.id) FROM msg_template t LEFT JOIN msg_record r ON r.template_code = t.template_code " + whereSql,
                Long.class, countArgs.toArray());

        int offset = Math.max(0, (params.getPage() - 1)) * params.getSize();
        List<MessageTemplateStats> list = jdbcTemplate.query(
                "SELECT t.*, COUNT(r.id) AS usage_count, MAX(r.create_time) AS last_used_time " +
                "FROM msg_template t LEFT JOIN msg_record r ON r.template_code = t.template_code " +
                whereSql + " GROUP BY t.id ORDER BY t.create_time DESC LIMIT ?, ?",
                tmplStatsMapper, appendParams(listArgs, offset, params.getSize()));

        return new TemplateQueryResult(total != null ? total : 0, list);
    }

    // ======================== 消息分页条件查询 ========================

    /**
     * 分页条件查询消息列表
     */
    public MessageQueryResult queryMessages(MessageQueryParams params) {
        StringBuilder whereSql = new StringBuilder("WHERE 1=1");
        List<Object> args = new ArrayList<>();

        if (params.getKeyword() != null && !params.getKeyword().isEmpty()) {
            whereSql.append(" AND (title LIKE ? OR content LIKE ?)");
            String kw = "%" + params.getKeyword() + "%";
            args.add(kw);
            args.add(kw);
        }
        if (params.getMessageType() != null && !params.getMessageType().isEmpty()) {
            whereSql.append(" AND message_type = ?");
            args.add(params.getMessageType());
        }
        if (params.getStatus() != null && !params.getStatus().isEmpty()) {
            whereSql.append(" AND status = ?");
            args.add(params.getStatus());
        }
        if (params.getChannels() != null && !params.getChannels().isEmpty()) {
            whereSql.append(" AND channels LIKE ?");
            args.add("%" + params.getChannels() + "%");
        }
        if (params.getReceiver() != null && !params.getReceiver().isEmpty()) {
            whereSql.append(" AND receivers LIKE ?");
            args.add("%" + params.getReceiver() + "%");
        }
        if (params.getExcludeChannels() != null && !params.getExcludeChannels().isEmpty()) {
            for (String ch : params.getExcludeChannels()) {
                whereSql.append(" AND channels NOT LIKE ?");
                args.add("%" + ch + "%");
            }
        }
        if (params.getIsRead() != null) {
            whereSql.append(" AND is_read = ?");
            args.add(params.getIsRead());
        }
        if (params.getSender() != null && !params.getSender().isEmpty()) {
            whereSql.append(" AND sender LIKE ?");
            args.add("%" + params.getSender() + "%");
        }
        if (params.getStartTime() != null) {
            whereSql.append(" AND create_time >= ?");
            args.add(Timestamp.valueOf(params.getStartTime()));
        }
        if (params.getEndTime() != null) {
            whereSql.append(" AND create_time <= ?");
            args.add(Timestamp.valueOf(params.getEndTime()));
        }

        // 总数
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM msg_record " + whereSql, Long.class, args.toArray());

        // 分页数据
        int offset = Math.max(0, (params.getPage() - 1)) * params.getSize();
        List<MessageRecord> list = jdbcTemplate.query(
                "SELECT * FROM msg_record " + whereSql + " ORDER BY create_time DESC LIMIT ?, ?",
                msgMapper, appendParams(args, offset, params.getSize()));

        return new MessageQueryResult(total != null ? total : 0, list);
    }

    public void deleteByMessageId(String messageId) {
        jdbcTemplate.update("DELETE FROM msg_record WHERE message_id = ?", messageId);
    }

    private Object[] appendParams(List<Object> args, Object... extra) {
        List<Object> all = new ArrayList<>(args);
        all.addAll(List.of(extra));
        return all.toArray();
    }

    // ======================== tool ========================
    private LocalDateTime toLdt(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }

    /**
     * 模板查询参数
     */
    @lombok.Data
    public static class TemplateQueryParams {
        private String templateCode;
        private String keyword;
        private String channel;
        private Integer status;
        private String createdBy;
        private java.time.LocalDateTime startTime;
        private java.time.LocalDateTime endTime;
        private int page = 1;
        private int size = 20;
    }

    /**
     * 模板查询结果
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class TemplateQueryResult {
        private long total;
        private List<MessageTemplateStats> list;
    }

    /**
     * 查询参数
     */
    @lombok.Data
    public static class MessageQueryParams {
        private String keyword;
        private String messageType;
        private String status;
        private String channels;
        private String receiver;
        private java.util.List<String> excludeChannels;
        private String sender;
        private Boolean isRead;
        private java.time.LocalDateTime startTime;
        private java.time.LocalDateTime endTime;
        private int page = 1;
        private int size = 20;
    }

    /**
     * 查询结果
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class MessageQueryResult {
        private long total;
        private List<MessageRecord> list;
    }
}
