package dts.com.examination.application.service.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dts.com.examination.application.event.LearningResultEvent;
import dts.com.examination.domain.entity.ExamSession;
import dts.com.examination.domain.repository.ExamSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class LegacyResultOutbox {
    private final ExamSessionRepository sessions;
    private final ObjectMapper mapper;
    private final KafkaTemplate<String,Object> kafka;
    private final TransactionTemplate transaction;
    public LegacyResultOutbox(ExamSessionRepository sessions,ObjectMapper mapper,KafkaTemplate<String,Object> kafka,
            PlatformTransactionManager manager) {
        this.sessions=sessions;this.mapper=mapper;this.kafka=kafka;this.transaction=new TransactionTemplate(manager);
    }
    public void enqueue(ExamSession session,LearningResultEvent event,Map<String,Object> metadata) {
        metadata.put("legacyResultEvent",mapper.copy().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .convertValue(event,new TypeReference<LinkedHashMap<String,Object>>(){}));
        metadata.put("legacyHistoryOutboxState","PENDING");metadata.put("legacyHistoryOutboxAttempts",0);
        metadata.put("legacyHistoryNextAttemptAt",Instant.now().toString());session.setMetadata(metadata);
    }
    @Scheduled(fixedDelayString="${dts.examination.session-worker.legacy-outbox-delay-ms:5000}")
    public void deliverPending() {
        for(var id:sessions.findLegacyHistoryOutboxIds(Instant.now(),PageRequest.of(0,20))) {
            try { transaction.executeWithoutResult(status -> deliver(id)); }
            catch(RuntimeException failure) { log.warn("Legacy result will retry: session={}, error={}",id,failure.getClass().getSimpleName()); }
        }
    }
    private void deliver(UUID id) {
        var session=sessions.findByIdForUpdate(id).orElseThrow();var metadata=new LinkedHashMap<>(session.getMetadata());
        if (!"SUBMITTED".equals(session.getStatus()) || session.getDeletedAt()!=null
                || !"PENDING".equals(metadata.get("legacyHistoryOutboxState"))
                || !Set.of("IMMEDIATE","IMMEDIATELY").contains(metadata.get("legacyResultReleaseMode"))) return;
        if(Instant.now().isBefore(Instant.parse((String)metadata.get("legacyHistoryNextAttemptAt")))) return;
        var event=mapper.convertValue(metadata.get("legacyResultEvent"),LearningResultEvent.class);
        if (!session.getId().equals(event.getSourceId()) || !session.getUserId().equals(event.getUserId())
                || !Set.of("PASSED","FAILED").contains(event.getResult())) throw new IllegalStateException("Invalid legacy source result");
        int attempts=((Number)metadata.get("legacyHistoryOutboxAttempts")).intValue()+1;
        try {
            kafka.send("learning-results",session.getUserId().toString(),event).get(10,TimeUnit.SECONDS);
            metadata.put("legacyHistoryOutboxState","DELIVERED");
        } catch(Exception failure) {
            if(failure instanceof InterruptedException)Thread.currentThread().interrupt();
            metadata.put("legacyHistoryNextAttemptAt",Instant.now().plusSeconds(Math.min(300,1L<<Math.min(attempts,8))).toString());
        }
        metadata.put("legacyHistoryOutboxAttempts",attempts);session.setMetadata(metadata);sessions.save(session);
    }
}
