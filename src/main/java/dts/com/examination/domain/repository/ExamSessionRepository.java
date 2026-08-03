package dts.com.examination.domain.repository;

import dts.com.examination.domain.entity.ExamSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ExamSessionRepository extends JpaRepository<ExamSession, UUID> {
    
    int countByExamVersionIdAndUserId(UUID examVersionId, UUID userId);
    
    boolean existsByExamVersionIdAndUserIdAndStatus(UUID examVersionId, UUID userId, String status);
}
