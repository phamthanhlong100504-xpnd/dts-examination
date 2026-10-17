package dts.com.examination.domain.repository;

import dts.com.examination.domain.entity.ExamVersion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.UUID;
import java.time.Instant;
import java.util.List;

public interface ExamVersionRepository extends JpaRepository<ExamVersion, UUID> {
    boolean existsByIdAndExamType(UUID id, String examType);
    boolean existsByExamIdAndExamType(UUID examId, String examType);
    @Query(value = """
            SELECT v.* FROM exam_versions v JOIN exams e ON e.id = v.exam_id
            WHERE v.deleted_at IS NULL AND e.deleted_at IS NULL AND v.exam_type = 'OFFICIAL'
              AND v.configs ->> 'officialConfigVersion' = '1'
              AND (
                (:tab = 'OPEN' AND e.status = 'PUBLISHED' AND v.status = 'PUBLISHED'
                  AND v.configs ->> 'licenseClass' = :license
                  AND v.started_at <= :now AND v.ended_at > :now)
                OR (:tab = 'UPCOMING' AND e.status = 'PUBLISHED' AND v.status = 'PUBLISHED'
                  AND v.configs ->> 'licenseClass' = :license AND v.started_at > :now)
                OR (:tab = 'PARTICIPATED' AND e.status IN ('PUBLISHED', 'HIDDEN', 'ARCHIVED')
                  AND v.status IN ('PUBLISHED', 'ARCHIVED') AND EXISTS (
                    SELECT 1 FROM exam_sessions s WHERE s.exam_version_id = v.id
                      AND s.user_id = :userId AND s.deleted_at IS NULL))
              )
            ORDER BY v.started_at ASC, v.id ASC
            """, countQuery = """
            SELECT COUNT(*) FROM exam_versions v JOIN exams e ON e.id = v.exam_id
            WHERE v.deleted_at IS NULL AND e.deleted_at IS NULL AND v.exam_type = 'OFFICIAL'
              AND v.configs ->> 'officialConfigVersion' = '1'
              AND (
                (:tab = 'OPEN' AND e.status = 'PUBLISHED' AND v.status = 'PUBLISHED'
                  AND v.configs ->> 'licenseClass' = :license
                  AND v.started_at <= :now AND v.ended_at > :now)
                OR (:tab = 'UPCOMING' AND e.status = 'PUBLISHED' AND v.status = 'PUBLISHED'
                  AND v.configs ->> 'licenseClass' = :license AND v.started_at > :now)
                OR (:tab = 'PARTICIPATED' AND e.status IN ('PUBLISHED', 'HIDDEN', 'ARCHIVED')
                  AND v.status IN ('PUBLISHED', 'ARCHIVED') AND EXISTS (
                    SELECT 1 FROM exam_sessions s WHERE s.exam_version_id = v.id
                      AND s.user_id = :userId AND s.deleted_at IS NULL))
              )
            """, nativeQuery = true)
    Page<ExamVersion> findStudentVersions(@Param("userId") UUID userId, @Param("license") String license,
            @Param("tab") String tab, @Param("now") Instant now, Pageable pageable);

    @Query(value = """
            SELECT v.* FROM exam_versions v JOIN exams e ON e.id = v.exam_id
            WHERE v.exam_id = :examId AND (CAST(:versionId AS uuid) IS NULL OR v.id = CAST(:versionId AS uuid))
              AND v.deleted_at IS NULL AND e.deleted_at IS NULL AND v.exam_type = 'OFFICIAL'
              AND v.configs ->> 'officialConfigVersion' = '1' AND v.status IN ('PUBLISHED', 'ARCHIVED')
              AND e.status IN ('PUBLISHED', 'HIDDEN', 'ARCHIVED')
              AND ((e.status = 'PUBLISHED' AND v.status = 'PUBLISHED' AND v.configs ->> 'licenseClass' = :license)
                OR EXISTS (SELECT 1 FROM exam_sessions s WHERE s.exam_version_id = v.id
                  AND s.user_id = :userId AND s.deleted_at IS NULL))
            ORDER BY CASE WHEN e.status = 'PUBLISHED' AND v.status = 'PUBLISHED'
              AND v.configs ->> 'licenseClass' = :license THEN 0 ELSE 1 END,
              v.version_no DESC, v.id ASC
            LIMIT 1
            """, nativeQuery = true)
    List<ExamVersion> findStudentDetailVersions(@Param("examId") UUID examId, @Param("versionId") UUID versionId,
            @Param("userId") UUID userId, @Param("license") String license);

    @Query("SELECT v.id FROM ExamVersion v, Exam e WHERE v.examId = e.id AND v.id IN :ids "
            + "AND v.deletedAt IS NULL AND e.deletedAt IS NULL AND e.status = 'PUBLISHED' AND v.status = 'PUBLISHED'")
    List<UUID> findStudentPublicVersionIds(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM exam_versions v JOIN exams e ON e.id = v.exam_id "
            + "WHERE v.id = :id AND v.deleted_at IS NULL AND e.deleted_at IS NULL "
            + "AND e.status = 'PUBLISHED' AND v.status = 'PUBLISHED')", nativeQuery = true)
    boolean isStudentVersionPublic(@Param("id") UUID id);

    // Scalar lookup avoids putting stale version state in the persistence context before waiting for the parent lock.
    @Query("SELECT e.examId FROM ExamVersion e WHERE e.id = :id AND e.deletedAt IS NULL")
    Optional<UUID> findExamIdByVersionId(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM ExamVersion e WHERE e.id = :id AND e.deletedAt IS NULL")
    Optional<ExamVersion> findByIdForUpdate(@Param("id") UUID id);

    
    boolean existsByExamIdAndStatusAndDeletedAtIsNull(UUID examId, String status);

    boolean existsByExamStructureIdAndStatus(UUID structureId, String status);

    boolean existsByExamStructureIdAndDeletedAtIsNull(UUID structureId);

    boolean existsByExamRuleId(UUID ruleId);
    boolean existsByExamRuleIdAndStatusIn(UUID ruleId, Collection<String> statuses);
    boolean existsByExamCriteriaId(UUID criteriaId);
    boolean existsByExamCriteriaIdAndStatusIn(UUID criteriaId, Collection<String> statuses);

    // History remains protected even when a referenced version was soft-deleted.
    boolean existsByExamStructureId(UUID structureId);

    boolean existsByExamStructureIdAndStatusIn(UUID structureId, Collection<String> statuses);

    @Query("SELECT COALESCE(MAX(v.versionNo), 0) FROM ExamVersion v WHERE v.examId = :examId")
    Integer findMaxVersionNoByExamId(@Param("examId") UUID examId);

    @Query("SELECT v FROM ExamVersion v WHERE v.examId = :examId AND v.deletedAt IS NULL AND (:status IS NULL OR v.status = :status)")
    Page<ExamVersion> findByExamIdAndStatus(@Param("examId") UUID examId, @Param("status") String status, Pageable pageable);

    Optional<ExamVersion> findByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT v FROM ExamVersion v WHERE v.examId = :examId AND v.status = :status AND v.deletedAt IS NULL")
    java.util.List<ExamVersion> findByExamIdAndStatusAndDeletedAtIsNullList(@Param("examId") UUID examId, @Param("status") String status);
}
