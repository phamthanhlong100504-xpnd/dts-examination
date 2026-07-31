package dts.com.examination.domain.repository;

import dts.com.examination.domain.entity.ExamVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ExamVersionRepository extends JpaRepository<ExamVersion, UUID> {
    boolean existsByExamStructureIdAndStatus(UUID examStructureId, String status);
    boolean existsByExamStructureIdAndDeletedAtIsNull(UUID examStructureId);
}
