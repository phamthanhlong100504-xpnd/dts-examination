package dts.com.examination.application.service.session;

import dts.com.examination.domain.repository.ExamSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Clock;
import java.util.UUID;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "dts.examination.session-worker.enabled", havingValue = "true", matchIfMissing = true)
public class OfficialSessionWorker {
    private final ExamSessionRepository sessions;
    private final OfficialExamSessionService service;
    private final Clock clock;

    // Kafka timeouts must not block the independent expiry task.
    @org.springframework.context.annotation.Bean
    public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3); scheduler.setThreadNamePrefix("official-session-");
        scheduler.setRemoveOnCancelPolicy(true); return scheduler;
    }

    @Scheduled(fixedDelayString = "${dts.examination.session-worker.expiry-delay-ms:1000}")
    public void expire() {
        for (UUID id : sessions.findOfficialExpiredIds(clock.instant(), PageRequest.of(0, 100))) {
            try { service.processExpired(id); }
            catch (RuntimeException failure) { log.warn("Official expiry will retry: session={}, failure={}", id, failure.getClass().getSimpleName()); }
        }
    }

    @Scheduled(fixedDelayString="${dts.examination.session-worker.history-backfill-delay-ms:15000}")
    public void backfillHistory() {
        for (UUID id:sessions.findHistoryBackfillIds(PageRequest.of(0,20))) {
            try { service.backfillHistory(id); }
            catch (RuntimeException failure) {
                try { service.recordHistoryBackfillFailure(id,failure.getClass().getSimpleName()); }
                catch (RuntimeException markerFailure) { log.warn("Unable to persist history backfill failure: session={}",id); }
                log.warn("Official history backfill needs reconciliation: session={}, error={}",id,failure.getClass().getSimpleName());
            }
        }
    }

    @Scheduled(fixedDelayString = "${dts.examination.session-worker.outbox-delay-ms:1000}")
    public void deliver() {
        for (UUID id : sessions.findOfficialOutboxIds(clock.instant(), PageRequest.of(0, 50))) {
            try { service.deliverResult(id); }
            catch (RuntimeException failure) { log.warn("Official outbox will retry: session={}, failure={}", id, failure.getClass().getSimpleName()); }
        }
    }
}
