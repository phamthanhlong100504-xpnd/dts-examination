package dts.com.examination.domain.repository;

import dts.com.examination.domain.entity.ExamStructure;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ExamStructureRepository extends JpaRepository<ExamStructure, UUID> {

    @Query("SELECT e FROM ExamStructure e WHERE e.id = :id AND e.deletedAt IS NULL")
    Optional<ExamStructure> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT e FROM ExamStructure e WHERE e.deletedAt IS NULL AND " +
           "(CAST(:keyword AS string) IS NULL OR LOWER(e.title) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))) AND " +
           "(CAST(:status AS string) IS NULL OR e.status = CAST(:status AS string))")
    Page<ExamStructure> findByFilters(@Param("keyword") String keyword, @Param("status") String status, Pageable pageable);
}
