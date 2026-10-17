package dts.com.examination.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Section implements Serializable {
    private String code;
    private String title;
    private Integer questionCount;
    private Integer score;
    private Integer order;

    // Empty selects from the entire source referenced by the exam version.
    @Builder.Default
    private List<UUID> chapterIds = List.of();

    // Null means no exact quota; zero excludes critical questions in this section.
    private Integer criticalQuestionCount;

    public Section(String code, String title, Integer questionCount, Integer score, Integer order) {
        this(code, title, questionCount, score, order, List.of(), null);
    }
}
