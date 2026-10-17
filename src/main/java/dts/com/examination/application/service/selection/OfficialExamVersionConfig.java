package dts.com.examination.application.service.selection;

import java.util.List;
import java.util.UUID;

/** Explicit administrative input; trusted policy and snapshots are added by the backend. */
public record OfficialExamVersionConfig(String licenseClass, String bankVersion,
        ExamSelectionMode selectionMode, List<UUID> manualQuestionIds) {
    public OfficialExamVersionConfig {
        manualQuestionIds = manualQuestionIds == null ? List.of() : java.util.Collections.unmodifiableList(
                new java.util.ArrayList<>(manualQuestionIds));
    }
}
