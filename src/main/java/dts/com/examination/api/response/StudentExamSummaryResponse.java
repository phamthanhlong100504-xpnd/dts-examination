package dts.com.examination.api.response;

import java.time.Instant;
import java.util.UUID;

public record StudentExamSummaryResponse(UUID examId, UUID examVersionId, String title, String licenseClass,
        String examType, String bankVersion, Instant startedAt, Instant endedAt, int questionCount,
        int durationSeconds, Long maxAttempts, long usedAttempts, Long remainingAttempts,
        String availability, String participationStatus, UUID activeSessionId, boolean canStart,
        boolean canResume, String reasonCode, Instant retryAvailableAt) {}
