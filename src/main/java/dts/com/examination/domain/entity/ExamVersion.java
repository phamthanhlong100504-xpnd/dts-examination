package dts.com.examination.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "exam_versions")
@Getter
@Setter
public class ExamVersion {

    @Id
    private UUID id;

    @Column(name = "exam_structure_id")
    private UUID examStructureId;

    private String status;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
