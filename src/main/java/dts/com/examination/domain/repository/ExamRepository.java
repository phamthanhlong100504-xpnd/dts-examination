package dts.com.examination.domain.repository;

import dts.com.examination.domain.entity.Exam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ExamRepository extends JpaRepository<Exam, UUID> {

    boolean existsByTitleAndDeletedAtIsNull(String title);

    boolean existsByTitleAndIdNotAndDeletedAtIsNull(String title, UUID id);

    @Query("SELECT e FROM Exam e WHERE e.id = :id AND e.deletedAt IS NULL")
    Optional<Exam> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT e FROM Exam e WHERE e.deletedAt IS NULL AND " +
           "(CAST(:keyword AS string) IS NULL OR LOWER(e.title) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))) AND " +
           "(CAST(:status AS string) IS NULL OR e.status = CAST(:status AS string)) AND " +
           "(:createdBy IS NULL OR e.createdBy = :createdBy)")
    Page<Exam> findByFilters(@Param("keyword") String keyword, @Param("status") String status, @Param("createdBy") UUID createdBy, Pageable pageable);
}
