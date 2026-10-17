package dts.com.examination.application.service;

import dts.com.examination.application.service.selection.PublishedExamSnapshot;
import dts.com.examination.domain.entity.ExamSession;
import dts.com.examination.domain.entity.ExamVersion;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

@Component
public class ExamParticipationPolicy {
    public record Decision(String availability, String participationStatus, Long maxAttempts,
            long usedAttempts, Long remainingAttempts, UUID activeSessionId, boolean canStart,
            boolean canResume, String reasonCode, Instant retryAvailableAt) {}

    public Decision assess(ExamVersion version, PublishedExamSnapshot snapshot, List<ExamSession> history,
            Instant now, boolean publiclyVisible, boolean licenseMatches) {
        var rule = snapshot.rule();
        String availability = !publiclyVisible || !"PUBLISHED".equals(version.getStatus()) ? "CLOSED"
                : now.isBefore(version.getStartedAt()) ? "UPCOMING" : !now.isBefore(version.getEndedAt()) ? "CLOSED" : "OPEN";
        Long maximum = null;
        if (!rule.isAllowRetry()) maximum = 1L;
        else if (rule.getMaxRetry() > 0) maximum = ((long) rule.getMaxRetry()) + 1;
        long used = history.size();
        Long remaining = maximum == null ? null : Math.max(0, maximum - used);
        Comparator<ExamSession> latestFirst = Comparator.comparing(ExamSession::getAttemptNo, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(ExamSession::getStartedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(ExamSession::getId, Comparator.nullsFirst(Comparator.naturalOrder())).reversed();
        List<ExamSession> live = history.stream().filter(s -> s.getDeletedAt() == null).sorted(latestFirst).toList();
        ExamSession latest = live.isEmpty() ? null : live.get(0);
        String participation = latest == null ? "NOT_STARTED" : effectiveStatus(latest, version, snapshot, now);
        List<ExamSession> active = live.stream().filter(s -> Set.of("IN_PROGRESS", "READY", "SUBMISSION_GRACE").contains(effectiveStatus(s, version, snapshot, now))).toList();
        if (active.size() > 1) return new Decision(availability, participation, maximum, used, remaining, null, false, false, "SESSION_CONFLICT", null);
        if (active.size() == 1) {
            ExamSession session = active.get(0);
            if ("SUBMISSION_GRACE".equals(effectiveStatus(session, version, snapshot, now)))
                return new Decision(availability, "SUBMISSION_GRACE", maximum, used, remaining,
                        session.getId(), false, false, "SUBMISSION_GRACE", null);
            boolean resumable = "IN_PROGRESS".equals(session.getStatus()) || pausedCanResume(session, snapshot, version, now);
            return new Decision(availability, session.getStatus(), maximum, used, remaining, session.getId(), false, resumable,
                    resumable ? "IN_PROGRESS".equals(session.getStatus()) ? "SESSION_IN_PROGRESS" : "SESSION_PAUSED" : "SESSION_NOT_RESUMABLE", null);
        }
        Instant retryAt = null;
        if (latest != null && rule.getRetryIntervalSeconds() > 0) {
            Instant completed = latest.getSubmittedAt() != null ? latest.getSubmittedAt()
                    : "EXPIRED".equals(participation) ? effectiveDeadline(latest, version, snapshot) : latest.getStartedAt();
            if (completed != null) retryAt = completed.plusSeconds(rule.getRetryIntervalSeconds());
        }
        String reason = !publiclyVisible || !"PUBLISHED".equals(version.getStatus()) ? "EXAM_UNAVAILABLE"
                : !licenseMatches ? "LICENSE_CLASS_MISMATCH" : "UPCOMING".equals(availability) ? "EXAM_NOT_STARTED"
                : "CLOSED".equals(availability) ? "EXAM_CLOSED" : remaining != null && remaining == 0 ? "ATTEMPTS_EXHAUSTED"
                : retryAt != null && now.isBefore(retryAt) ? "RETRY_INTERVAL" : null;
        return new Decision(availability, participation, maximum, used, remaining, null, reason == null, false, reason, retryAt);
    }

    private String effectiveStatus(ExamSession session, ExamVersion version, PublishedExamSnapshot snapshot, Instant now) {
        if ("IN_PROGRESS".equals(session.getStatus())) {
            Instant deadline = deadline(session, version);
            if (deadline != null && !now.isBefore(deadline) && session.getMetadata() != null
                    && session.getMetadata().containsKey("officialSessionVersion") && snapshot.rule().getGracePeriodSeconds() > 0) {
                Instant graceEnd = deadline.plusSeconds(snapshot.rule().getGracePeriodSeconds());
                if (graceEnd.isAfter(version.getEndedAt())) graceEnd = version.getEndedAt();
                if (now.isBefore(graceEnd)) return "SUBMISSION_GRACE";
            }
            return deadline == null || !now.isBefore(deadline) ? "EXPIRED" : "IN_PROGRESS";
        }
        if ("READY".equals(session.getStatus())) {
            Instant deadline = pausedDeadline(session, snapshot, version);
            if (!now.isBefore(version.getEndedAt()) || deadline != null && !now.isBefore(deadline)) return "EXPIRED";
        }
        return session.getStatus() == null ? "UNKNOWN" : session.getStatus();
    }

    private Instant deadline(ExamSession session, ExamVersion version) {
        if (session.getExpiredAt() == null) return null;
        return session.getExpiredAt().isBefore(version.getEndedAt()) ? session.getExpiredAt() : version.getEndedAt();
    }

    private boolean pausedCanResume(ExamSession session, PublishedExamSnapshot snapshot, ExamVersion version, Instant now) {
        var rule = snapshot.rule();
        var metadata = session.getMetadata();
        if (!rule.isAllowResume() || !rule.isAllowPause() || metadata == null
                || !(metadata.get("pausedRemainingSeconds") instanceof Number remaining) || remaining.longValue() < 1
                || !(metadata.get("pausedAt") instanceof String raw)) return false;
        try {
            Instant paused = Instant.parse(raw);
            Instant deadline = pausedDeadline(session, snapshot, version);
            return deadline != null && !now.isBefore(paused) && now.isBefore(deadline);
        } catch (RuntimeException failure) { return false; }
    }

    private Instant effectiveDeadline(ExamSession session, ExamVersion version, PublishedExamSnapshot snapshot) {
        return "READY".equals(session.getStatus()) ? pausedDeadline(session, snapshot, version) : deadline(session, version);
    }

    private Instant pausedDeadline(ExamSession session, PublishedExamSnapshot snapshot, ExamVersion version) {
        var metadata = session.getMetadata();
        long limit = Math.min(snapshot.rule().getMaxPauseDurationSeconds(), snapshot.rule().getResumeTimeoutSeconds());
        if (limit < 1 || metadata == null || !(metadata.get("pausedAt") instanceof String raw)) return null;
        try {
            Instant limitAt = Instant.parse(raw).plusSeconds(limit);
            return limitAt.isBefore(version.getEndedAt()) ? limitAt : version.getEndedAt();
        } catch (RuntimeException failure) { return null; }
    }
}
