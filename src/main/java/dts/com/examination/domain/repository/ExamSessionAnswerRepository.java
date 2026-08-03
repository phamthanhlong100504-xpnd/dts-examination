package dts.com.examination.domain.repository;

import dts.com.examination.domain.entity.ExamSessionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExamSessionAnswerRepository extends JpaRepository<ExamSessionAnswer, UUID> {
    long countByExamSessionId(UUID examSessionId);
    
    long countByExamSessionIdAndSelectedAnswerIsNotNull(UUID examSessionId);

    List<ExamSessionAnswer> findByExamSessionId(UUID examSessionId);

    List<ExamSessionAnswer> findByExamSessionIdAndQuestionIdIn(UUID examSessionId, List<UUID> questionIds);
}
