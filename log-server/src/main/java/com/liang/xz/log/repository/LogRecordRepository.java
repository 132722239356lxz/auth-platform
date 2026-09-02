package com.liang.xz.log.repository;

import com.liang.xz.log.entity.LogRecord;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日志记录持久层
 *
 * @author liang
 */
@Repository
public interface LogRecordRepository extends CrudRepository<LogRecord, Long> {

    // ======================== 按 traceId 追踪 ========================

    @Query("SELECT * FROM sys_log_record WHERE trace_id = :traceId ORDER BY log_time ASC")
    List<LogRecord> findByTraceId(@Param("traceId") String traceId);

    // ======================== 相似错误指纹查询 (AI分析核心) ========================

    @Query("SELECT * FROM sys_log_record WHERE error_fingerprint = :fingerprint " +
           "AND level = 'ERROR' ORDER BY log_time DESC LIMIT :limit")
    List<LogRecord> findByErrorFingerprint(@Param("fingerprint") String fingerprint,
                                           @Param("limit") int limit);

    @Query("SELECT error_fingerprint AS fingerprint, COUNT(1) AS cnt " +
           "FROM sys_log_record WHERE level = 'ERROR' " +
           "AND log_time BETWEEN :startTime AND :endTime " +
           "GROUP BY error_fingerprint ORDER BY cnt DESC LIMIT :limit")
    List<FingerprintCount> findTopErrorFingerprints(@Param("startTime") LocalDateTime startTime,
                                                    @Param("endTime") LocalDateTime endTime,
                                                    @Param("limit") int limit);

    // ======================== 统计查询 ========================

    @Query("SELECT COUNT(1) FROM sys_log_record WHERE log_time BETWEEN :startTime AND :endTime")
    Long countByTimeRange(@Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COUNT(1) FROM sys_log_record WHERE level = 'ERROR' " +
           "AND log_time BETWEEN :startTime AND :endTime")
    Long countErrorByTimeRange(@Param("startTime") LocalDateTime startTime,
                               @Param("endTime") LocalDateTime endTime);

    @Query("SELECT level, COUNT(1) AS cnt FROM sys_log_record " +
           "WHERE log_time BETWEEN :startTime AND :endTime GROUP BY level")
    List<LevelCount> countGroupByLevel(@Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime);

    @Query("SELECT module, COUNT(1) AS cnt FROM sys_log_record " +
           "WHERE log_time BETWEEN :startTime AND :endTime GROUP BY module")
    List<ModuleCount> countGroupByModule(@Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime);

    @Query("SELECT category, COUNT(1) AS cnt FROM sys_log_record " +
           "WHERE log_time BETWEEN :startTime AND :endTime GROUP BY category")
    List<CategoryCount> countGroupByCategory(@Param("startTime") LocalDateTime startTime,
                                             @Param("endTime") LocalDateTime endTime);

    @Query("SELECT AVG(cost_time) FROM sys_log_record WHERE cost_time IS NOT NULL " +
           "AND log_time BETWEEN :startTime AND :endTime")
    Double avgCostTime(@Param("startTime") LocalDateTime startTime,
                       @Param("endTime") LocalDateTime endTime);

    // ======================== 清理过期日志 ========================

    @Query("DELETE FROM sys_log_record WHERE log_time < :beforeTime LIMIT :batchSize")
    int deleteExpired(@Param("beforeTime") LocalDateTime beforeTime,
                      @Param("batchSize") int batchSize);

    // ======================== 内嵌映射接口 ========================

    interface FingerprintCount {
        String getFingerprint();
        Long getCnt();
    }

    interface LevelCount {
        String getLevel();
        Long getCnt();
    }

    interface ModuleCount {
        String getModule();
        Long getCnt();
    }

    interface CategoryCount {
        String getCategory();
        Long getCnt();
    }
}
