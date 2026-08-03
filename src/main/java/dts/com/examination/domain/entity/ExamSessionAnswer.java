package dts.com.examination.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "exam_session_answers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class ExamSessionAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "exam_session_id", nullable = false)
    private UUID examSessionId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_answer", columnDefinition = "jsonb")
    private Map<String, Object> selectedAnswer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "display_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> displaySnapshot;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 0;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
