package dts.com.examination.application.service.selection;

import java.time.LocalDate;
import java.util.List;

/** Trusted backend policy, loaded from a versioned resource; never from request metadata. */
public record ExamMatrixPolicy(
        String id, String bankVersion, LocalDate validFrom, LocalDate validUntilExclusive,
        String licenseClass, int totalQuestions, int durationMinutes, int passingCorrectAnswers,
        int criticalQuestionCount, List<TopicQuota> topics) {
    public ExamMatrixPolicy {
        topics = List.copyOf(topics);
    }

    public record TopicQuota(String code, List<Integer> sourceChapterIds, int questionCount) {
        public TopicQuota {
            sourceChapterIds = List.copyOf(sourceChapterIds);
        }
    }
}
