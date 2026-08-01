package dts.com.examination.domain.repository;

import dts.com.examination.domain.entity.ExamVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ExamVersionRepository extends JpaRepository<ExamVersion, UUID> {
    
    boolean existsByExamIdAndStatusAndDeletedAtIsNull(UUID examId, String status);

    boolean existsByExamStructureIdAndStatus(UUID structureId, String status);

    boolean existsByExamStructureIdAndDeletedAtIsNull(UUID structureId);

    @Query("SELECT COALESCE(MAX(v.versionNo), 0) FROM ExamVersion v WHERE v.examId = :examId")
    Integer findMaxVersionNoByExamId(@Param("examId") UUID examId);

    @Query("SELECT v FROM ExamVersion v WHERE v.examId = :examId AND v.deletedAt IS NULL AND (:status IS NULL OR v.status = :status)")
    Page<ExamVersion> findByExamIdAndStatus(@Param("examId") UUID examId, @Param("status") String status, Pageable pageable);

    Optional<ExamVersion> findByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT v FROM ExamVersion v WHERE v.examId = :examId AND v.status = :status AND v.deletedAt IS NULL")
    java.util.List<ExamVersion> findByExamIdAndStatusAndDeletedAtIsNullList(@Param("examId") UUID examId, @Param("status") String status);
}
