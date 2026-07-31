package dts.com.examination.domain.repository;

import dts.com.examination.domain.entity.ExamRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ExamRuleRepository extends JpaRepository<ExamRule, UUID> {

    @Query("SELECT e FROM ExamRule e WHERE e.id = :id AND e.deletedAt IS NULL")
    Optional<ExamRule> findByIdActive(@Param("id") UUID id);

    @Query("SELECT e FROM ExamRule e WHERE e.deletedAt IS NULL " +
           "AND (:title IS NULL OR LOWER(e.title) LIKE LOWER(CONCAT('%', CAST(:title AS text), '%'))) " +
           "AND (:status IS NULL OR e.status = CAST(:status AS text))")
    Page<ExamRule> searchRules(@Param("title") String title, @Param("status") String status, Pageable pageable);

    boolean existsByTitleAndDeletedAtIsNull(String title);
}
