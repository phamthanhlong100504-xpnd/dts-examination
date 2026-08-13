package dts.com.examination.domain.repository;

import dts.com.examination.domain.entity.ExamSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ExamSessionRepository extends JpaRepository<ExamSession, UUID> {
    
    int countByExamVersionIdAndUserId(UUID examVersionId, UUID userId);
    
    boolean existsByExamVersionIdAndUserIdAndStatus(UUID examVersionId, UUID userId, String status);
    
    java.util.Optional<ExamSession> findFirstByExamVersionIdAndUserIdAndStatus(UUID examVersionId, UUID userId, String status);

    @Query("SELECT s FROM ExamSession s JOIN ExamVersion v ON s.examVersionId = v.id " +
           "WHERE v.examId = :examId AND s.userId = :userId AND s.deletedAt IS NULL")
    Page<ExamSession> findByExamIdAndUserId(@Param("examId") UUID examId, @Param("userId") UUID userId, Pageable pageable);
}
