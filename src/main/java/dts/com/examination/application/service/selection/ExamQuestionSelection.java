package dts.com.examination.application.service.selection;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Internal selection plan. Full content/keys must be verified and frozen before returning start. */
public record ExamQuestionSelection(String matrixPolicyId, String bankVersion, String licenseClass,
        ExamSelectionMode mode, List<ExamSourceQuestionMetadata> questions,
        Map<UUID, String> sectionCodeByQuestionId) {
    public ExamQuestionSelection {
        questions = List.copyOf(questions);
        sectionCodeByQuestionId = Map.copyOf(sectionCodeByQuestionId);
    }
}
