package dts.com.examination.domain.repository;

import dts.com.examination.domain.entity.ExamCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ExamCriteriaRepository extends JpaRepository<ExamCriteria, UUID> {

    @Query("SELECT e FROM ExamCriteria e WHERE e.id = :id AND e.deletedAt IS NULL")
    Optional<ExamCriteria> findByIdActive(@Param("id") UUID id);

    @Query("SELECT e FROM ExamCriteria e WHERE e.deletedAt IS NULL AND (:title IS NULL OR LOWER(e.title) LIKE LOWER(CONCAT('%', CAST(:title AS text), '%'))) AND (:status IS NULL OR e.status = CAST(:status AS text))")
    Page<ExamCriteria> searchCriterias(@Param("title") String title, @Param("status") String status, Pageable pageable);
}
