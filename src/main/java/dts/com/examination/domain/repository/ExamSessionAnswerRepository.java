package dts.com.examination.domain.repository;

import dts.com.examination.domain.entity.ExamSessionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ExamSessionAnswerRepository extends JpaRepository<ExamSessionAnswer, UUID> {
}
