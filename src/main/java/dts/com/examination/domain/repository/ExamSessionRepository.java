package dts.com.examination.domain.repository;

import dts.com.examination.domain.entity.ExamSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Collection;
import java.util.List;

@Repository
public interface ExamSessionRepository extends JpaRepository<ExamSession, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ExamSession s WHERE s.id = :id")
    java.util.Optional<ExamSession> findByIdForUpdate(@Param("id") UUID id);

    // Hydrate history only after acquiring locks; do not reuse an earlier managed stale session.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ExamSession s WHERE s.examVersionId = :versionId AND s.userId = :userId ORDER BY s.id")
    List<ExamSession> findOfficialHistoryForUpdate(@Param("versionId") UUID versionId, @Param("userId") UUID userId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM exam_sessions s JOIN exam_versions v ON v.id = s.exam_version_id "
            + "WHERE s.id = :id AND v.exam_type = 'OFFICIAL')", nativeQuery = true)
    boolean isOfficialSession(@Param("id") UUID id);

    @Query(value = "SELECT s.id FROM exam_sessions s WHERE s.deleted_at IS NULL "
            + "AND s.metadata ->> 'officialSessionVersion' = '1' AND s.status IN ('IN_PROGRESS', 'READY') "
            + "AND CAST(s.metadata ->> 'nextTransitionAt' AS timestamptz) <= :now ORDER BY s.expired_at, s.id", nativeQuery = true)
    List<UUID> findOfficialExpiredIds(@Param("now") java.time.Instant now, Pageable pageable);

    @Query(value = "SELECT s.id FROM exam_sessions s WHERE s.deleted_at IS NULL "
            + "AND s.metadata ->> 'officialSessionVersion' = '1' AND s.metadata ->> 'outboxState' = 'PENDING' "
            + "AND CAST(s.metadata ->> 'outboxNextAttemptAt' AS timestamptz) <= :now ORDER BY s.submitted_at, s.id", nativeQuery = true)
    List<UUID> findOfficialOutboxIds(@Param("now") java.time.Instant now, Pageable pageable);
    @Query(value = "SELECT s.id FROM exam_sessions s WHERE s.deleted_at IS NULL AND s.status='SUBMITTED' "
            + "AND s.metadata ->> 'officialSessionVersion'='1' "
            + "AND (s.metadata ->> 'historySchemaVersion') IS DISTINCT FROM '2' "
            + "AND (s.metadata ->> 'historyBackfillState') IS DISTINCT FROM 'FAILED' ORDER BY s.submitted_at,s.id", nativeQuery=true)
    List<UUID> findHistoryBackfillIds(Pageable pageable);

    @Query(value="SELECT s.id FROM exam_sessions s WHERE s.deleted_at IS NULL AND s.status='SUBMITTED' "
            + "AND s.metadata ->> 'legacyHistoryOutboxState'='PENDING' "
            + "AND s.metadata ->> 'legacyResultReleaseMode' IN ('IMMEDIATE','IMMEDIATELY') "
            + "AND CAST(s.metadata ->> 'legacyHistoryNextAttemptAt' AS timestamptz)<=:now ORDER BY s.submitted_at,s.id",nativeQuery=true)
    List<UUID> findLegacyHistoryOutboxIds(@Param("now") java.time.Instant now,Pageable pageable);

    // Include soft-deleted attempts for quota accounting; the read policy excludes them from resume/history display.
    List<ExamSession> findByExamVersionIdInAndUserId(Collection<UUID> versionIds, UUID userId);

    // Include soft-deleted sessions: their attempt and grading history still matters.
    boolean existsByExamVersionId(UUID examVersionId);
    
    int countByExamVersionIdAndUserId(UUID examVersionId, UUID userId);
    
    boolean existsByExamVersionIdAndUserIdAndStatus(UUID examVersionId, UUID userId, String status);
    
    java.util.Optional<ExamSession> findFirstByExamVersionIdAndUserIdAndStatus(UUID examVersionId, UUID userId, String status);

    @Query("SELECT s FROM ExamSession s JOIN ExamVersion v ON s.examVersionId = v.id " +
           "WHERE v.examId = :examId AND s.userId = :userId AND s.deletedAt IS NULL")
    Page<ExamSession> findByExamIdAndUserId(@Param("examId") UUID examId, @Param("userId") UUID userId, Pageable pageable);
}
