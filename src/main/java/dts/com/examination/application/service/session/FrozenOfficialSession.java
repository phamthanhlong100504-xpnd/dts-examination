package dts.com.examination.application.service.session;

import dts.com.examination.application.service.selection.FrozenExamQuestion;
import dts.com.examination.application.service.selection.PublishedExamSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Private persisted content, policies and answer key. Never return this record over HTTP. */
public record FrozenOfficialSession(int schemaVersion, UUID examId, UUID examVersionId,
        Instant examEndedAt, PublishedExamSnapshot configuration, List<FrozenExamQuestion> questions,
        Map<UUID, String> sectionAssignments) {
    public FrozenOfficialSession {
        questions = List.copyOf(questions);
        sectionAssignments = Map.copyOf(sectionAssignments);
    }
}
