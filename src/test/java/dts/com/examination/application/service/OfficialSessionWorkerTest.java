package dts.com.examination.application.service;

import dts.com.examination.application.service.session.*;
import dts.com.examination.domain.repository.ExamSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class OfficialSessionWorkerTest {
    @Test void oneFailedExpiryDoesNotPreventOtherSessionsFromBeingReconciled() {
        var repository=mock(ExamSessionRepository.class);var service=mock(OfficialExamSessionService.class);
        var now=Instant.parse("2026-11-01T00:00:00Z");var first=new UUID(0,1);var second=new UUID(0,2);
        when(repository.findOfficialExpiredIds(now,PageRequest.of(0,100))).thenReturn(List.of(first,second));
        doThrow(new IllegalStateException("Malformed snapshot")).when(service).processExpired(first);
        new OfficialSessionWorker(repository,service,Clock.fixed(now,ZoneOffset.UTC)).expire();verify(service).processExpired(first);verify(service).processExpired(second);
    }
    @Test void pendingEventsAreDelegatedToTheTransactionalDeliveryService() {
        var repository=mock(ExamSessionRepository.class);var service=mock(OfficialExamSessionService.class);
        var now=Instant.parse("2026-11-01T00:00:00Z");var id=new UUID(0,1);
        when(repository.findOfficialOutboxIds(now,PageRequest.of(0,50))).thenReturn(List.of(id));
        var worker=new OfficialSessionWorker(repository,service,Clock.fixed(now,ZoneOffset.UTC));worker.deliver();verify(service).deliverResult(id);
        var scheduler=worker.taskScheduler();assertEquals(3,scheduler.getPoolSize());scheduler.shutdown();
    }

    @Test void invalidHistorySnapshotIsRecordedAndDoesNotBlockTheNextSource() {
        var repository=mock(ExamSessionRepository.class);var service=mock(OfficialExamSessionService.class);
        var first=new UUID(0,1);var second=new UUID(0,2);
        when(repository.findHistoryBackfillIds(PageRequest.of(0,20))).thenReturn(List.of(first,second));
        doThrow(new IllegalStateException("Invalid persisted snapshot")).when(service).backfillHistory(first);
        new OfficialSessionWorker(repository,service,Clock.systemUTC()).backfillHistory();
        verify(service).recordHistoryBackfillFailure(first,"IllegalStateException");
        verify(service).backfillHistory(second);
    }
    @Test void failureToPersistAnErrorMarkerStillDoesNotBlockTheNextSource() {
        var repository=mock(ExamSessionRepository.class);var service=mock(OfficialExamSessionService.class);
        var first=new UUID(0,1);var second=new UUID(0,2);
        when(repository.findHistoryBackfillIds(PageRequest.of(0,20))).thenReturn(List.of(first,second));
        doThrow(new IllegalStateException("Invalid persisted snapshot")).when(service).backfillHistory(first);
        doThrow(new IllegalStateException("Marker failed")).when(service).recordHistoryBackfillFailure(first,"IllegalStateException");
        new OfficialSessionWorker(repository,service,Clock.systemUTC()).backfillHistory();
        verify(service).backfillHistory(second);
    }
}
