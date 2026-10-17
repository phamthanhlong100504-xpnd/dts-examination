package dts.com.examination.application.service.selection;

import java.util.List;
import java.util.UUID;

/**
 * Internal input assembled from published source chapters and metadata fetched WITH licenseClass.
 * Do not bind this record to an HTTP request or use unlicensed metadata to construct it.
 */
public record ExamQuestionPool(String bankVersion, String licenseClass,
        List<ChapterReference> chapters, List<ExamSourceQuestionMetadata> questions) {
    public ExamQuestionPool {
        chapters = chapters == null ? null : List.copyOf(chapters);
        questions = questions == null ? null : List.copyOf(questions);
    }

    public record ChapterReference(UUID id, int sourceChapterId, String bankVersion) {
    }
}
