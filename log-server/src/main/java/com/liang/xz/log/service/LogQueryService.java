package com.liang.xz.log.service;

import com.liang.xz.log.dto.LogQueryRequest;
import com.liang.xz.log.dto.LogStatsDTO;
import com.liang.xz.log.entity.LogRecord;
import com.liang.xz.log.repository.ErrorSolutionRepository;
import com.liang.xz.log.repository.LogRecordRepository;
import com.liang.xz.log.repository.LogRecordRepository.FingerprintCount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 日志查询与统计服务
 *
 * @author liang
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogQueryService {

    private final JdbcTemplate jdbcTemplate;
    private final LogRecordRepository logRecordRepository;
    private final ErrorSolutionRepository errorSolutionRepository;

    /** 将 sys_log_record 表字段映射到 LogRecord 实体 */
    private static final RowMapper<LogRecord> LOG_RECORD_ROW_MAPPER = new RowMapper<LogRecord>() {
        @Override
        public LogRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return LogRecord.builder()
                    .id(rs.getLong("id"))
                    .traceId(rs.getString("trace_id"))
                    .module(rs.getString("module"))
                    .category(rs.getString("category"))
                    .level(rs.getString("level"))
                    .className(rs.getString("class_name"))
                    .methodName(rs.getString("method_name"))
                    .message(rs.getString("message"))
                    .fullMessage(rs.getString("full_message"))
                    .exceptionStack(rs.getString("exception_stack"))
                    .exceptionType(rs.getString("exception_type"))
                    .errorFingerprint(rs.getString("error_fingerprint"))
                    .username(rs.getString("username"))
                    .clientIp(rs.getString("client_ip"))
                    .requestUri(rs.getString("request_uri"))
                    .httpMethod(rs.getString("http_method"))
                    .httpStatus(rs.getObject("http_status") != null ? rs.getInt("http_status") : null)
                    .costTime(rs.getObject("cost_time") != null ? rs.getLong("cost_time") : null)
                    .metadata(rs.getString("metadata"))
                    .logTime(toLocalDateTime(rs.getTimestamp("log_time")))
                    .createTime(toLocalDateTime(rs.getTimestamp("create_time")))
                    .build();
        }

        private LocalDateTime toLocalDateTime(Timestamp ts) {
            return ts != null ? ts.toLocalDateTime() : null;
        }
    };

    // ======================== 分页查询 ========================

    /**
     * 按条件分页查询日志
     */
    public Map<String, Object> queryPage(LogQueryRequest req) {
        int page = Math.max(1, Optional.ofNullable(req.getPage()).orElse(1));
        int size = Math.min(100, Math.max(1, Optional.ofNullable(req.getSize()).orElse(20)));

        // 动态拼接 SQL
        StringBuilder where = new StringBuilder("WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        appendCondition(where, params, "module", req.getModule());
        appendCondition(where, params, "category", req.getCategory());
        appendCondition(where, params, "level", req.getLevel());
        appendCondition(where, params, "exception_type", req.getExceptionType());
        appendCondition(where, params, "error_fingerprint", req.getErrorFingerprint());
        appendCondition(where, params, "username", req.getUsername());
        appendCondition(where, params, "request_uri", req.getRequestUri());

        if (req.getHttpStatus() != null) {
            where.append("AND http_status = ? ");
            params.add(req.getHttpStatus());
        }
        if (req.getMinCostTime() != null) {
            where.append("AND cost_time >= ? ");
            params.add(req.getMinCostTime());
        }
        if (req.getStartTime() != null) {
            where.append("AND log_time >= ? ");
            params.add(req.getStartTime());
        }
        if (req.getEndTime() != null) {
            where.append("AND log_time <= ? ");
            params.add(req.getEndTime());
        }
        if (req.getKeyword() != null && !req.getKeyword().isEmpty()) {
            where.append("AND (message LIKE ? OR trace_id = ? OR full_message LIKE ?) ");
            String like = "%" + req.getKeyword() + "%";
            params.add(like);
            params.add(req.getKeyword());
            params.add(like);
        }

        // 计数
        String countSql = "SELECT COUNT(1) FROM sys_log_record " + where;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());

        // 分页数据
        String dataSql = "SELECT * FROM sys_log_record " + where + "ORDER BY log_time DESC LIMIT ? OFFSET ?";
        params.add(size);
        params.add((long) (page - 1) * size);
        List<LogRecord> rows = jdbcTemplate.query(dataSql, LOG_RECORD_ROW_MAPPER, params.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("totalPages", total == null ? 0 : (total + size - 1) / size);
        result.put("records", rows);
        result.put("list", rows);
        return result;
    }

    // ======================== TraceId 追踪 ========================

    /**
     * 按 traceId 还原完整调用链
     */
    public List<LogRecord> traceByTraceId(String traceId) {
        return logRecordRepository.findByTraceId(traceId);
    }

    // ======================== 统计概览 ========================

    /**
     * 获取时间范围内的日志统计概览
     */
    public LogStatsDTO stats(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null) startTime = LocalDateTime.now().minusDays(1);
        if (endTime == null) endTime = LocalDateTime.now();

        Long total = logRecordRepository.countByTimeRange(startTime, endTime);
        Long errorCount = logRecordRepository.countErrorByTimeRange(startTime, endTime);

        // 级别分布
        Map<String, Long> levelDist = logRecordRepository.countGroupByLevel(startTime, endTime)
                .stream().collect(Collectors.toMap(
                        LogRecordRepository.LevelCount::getLevel,
                        LogRecordRepository.LevelCount::getCnt
                ));

        // 模块分布
        Map<String, Long> moduleDist = logRecordRepository.countGroupByModule(startTime, endTime)
                .stream().collect(Collectors.toMap(
                        LogRecordRepository.ModuleCount::getModule,
                        LogRecordRepository.ModuleCount::getCnt
                ));

        // 分类分布
        Map<String, Long> categoryDist = logRecordRepository.countGroupByCategory(startTime, endTime)
                .stream().collect(Collectors.toMap(
                        LogRecordRepository.CategoryCount::getCategory,
                        LogRecordRepository.CategoryCount::getCnt
                ));

        // TOP 错误指纹
        List<FingerprintCount> topFingerprints = logRecordRepository
                .findTopErrorFingerprints(startTime, endTime, 5);

        List<String> fingerprints = topFingerprints.stream()
                .map(FingerprintCount::getFingerprint).toList();

        // 批量查解决方案
        Set<String> solvedFingerprints = errorSolutionRepository.findByFingerprints(fingerprints)
                .stream().map(s -> s.getErrorFingerprint()).collect(Collectors.toSet());

        List<LogStatsDTO.ErrorFingerprintTop> topList = new ArrayList<>();
        for (FingerprintCount fc : topFingerprints) {
            topList.add(LogStatsDTO.ErrorFingerprintTop.builder()
                    .fingerprint(fc.getFingerprint())
                    .count(fc.getCnt())
                    .hasSolution(solvedFingerprints.contains(fc.getFingerprint()))
                    .build());
        }

        // 耗时
        Double avgCost = logRecordRepository.avgCostTime(startTime, endTime);

        Double errorRate = (total != null && total > 0)
                ? (double) (errorCount != null ? errorCount : 0) / total * 100
                : 0.0;

        return LogStatsDTO.builder()
                .totalCount(total)
                .levelDistribution(levelDist)
                .moduleDistribution(moduleDist)
                .categoryDistribution(categoryDist)
                .errorCount(errorCount)
                .errorRate(Math.round(errorRate * 100.0) / 100.0)
                .topErrorFingerprints(topList)
                .avgCostTime(avgCost != null ? Math.round(avgCost * 100.0) / 100.0 : null)
                .build();
    }

    // ======================== 清理 ========================

    /**
     * 清理指定天数前的日志
     */
    public int cleanExpired(int retentionDays, int batchSize) {
        LocalDateTime beforeTime = LocalDateTime.now().minusDays(retentionDays);
        int total = 0;
        int deleted;
        do {
            deleted = logRecordRepository.deleteExpired(beforeTime, batchSize);
            total += deleted;
        } while (deleted >= batchSize);
        log.info("[log-server] 清理过期日志完成, 共删除 {} 条 ({} 天前)", total, retentionDays);
        return total;
    }

    // ======================== 内部方法 ========================

    private void appendCondition(StringBuilder where, List<Object> params,
                                  String column, String value) {
        if (value != null && !value.isEmpty()) {
            where.append("AND ").append(column).append(" = ? ");
            params.add(value);
        }
    }
}
