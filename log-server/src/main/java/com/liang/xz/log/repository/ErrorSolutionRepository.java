package com.liang.xz.log.repository;

import com.liang.xz.log.entity.ErrorSolution;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 错误解决方案知识库持久层
 *
 * @author liang
 */
@Repository
public interface ErrorSolutionRepository extends CrudRepository<ErrorSolution, Long> {

    @Query("SELECT * FROM sys_error_solution WHERE error_fingerprint = :fingerprint AND status = 'PUBLISHED'")
    Optional<ErrorSolution> findByFingerprint(@Param("fingerprint") String fingerprint);

    @Query("SELECT * FROM sys_error_solution WHERE error_fingerprint IN (:fingerprints)")
    List<ErrorSolution> findByFingerprints(@Param("fingerprints") List<String> fingerprints);

    @Query("SELECT * FROM sys_error_solution WHERE status = 'PUBLISHED' ORDER BY resolve_count DESC LIMIT :limit")
    List<ErrorSolution> findTopSolutions(@Param("limit") int limit);

    @Modifying
    @Query("UPDATE sys_error_solution SET resolve_count = resolve_count + 1 WHERE id = :id")
    int incrementResolveCount(@Param("id") Long id);
}
