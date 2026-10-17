package dts.com.examination.application.service;

import dts.com.examination.application.event.LearningResultEvent;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static dts.com.examination.application.service.PublishedVersionTestFixture.*;

class Screen13OfficialHistoryTest {
    @Test void titleAndWeightedCountsAreFrozenAtSource() throws Exception {
        var f=new OfficialSessionFixture();f.parent.setTitle("Original exam");var session=f.start();
        f.parent.setTitle("Renamed exam");f.correctAll(session);f.service.submit(session.getId(),"key",USER);
        var event=f.published.mapper.convertValue(session.getMetadata().get("resultEvent"),LearningResultEvent.class);
        assertEquals(2,event.getEventVersion());assertEquals("Original exam",event.getDisplayTitle());
        assertEquals("OFFICIAL_EXAM",event.getActivityType());assertEquals(30,event.getQuestionCount());
        assertEquals(30,event.getCorrectCount());assertEquals(EXAM,event.getTargetId());assertTrue(event.getResultAvailable());
        assertFalse(event.getResultSnapshot().containsKey("answers"));
    }
    @Test void manualBackfillDoesNotSendBeforeReleaseAndOldTitleRemainsUnknown() throws Exception {
        var f=new OfficialSessionFixture();f.published.rule.setResultReleaseMode("MANUAL");f.published.freezeRule();
        var session=f.start();f.service.submit(session.getId(),"key",USER);
        var metadata=new java.util.LinkedHashMap<>(session.getMetadata());metadata.remove("historySchemaVersion");metadata.remove("historyDisplayTitle");
        var event=new java.util.LinkedHashMap<>((Map<String,Object>)metadata.get("resultEvent"));event.put("eventVersion",1);
        metadata.put("resultEvent",event);metadata.put("outboxState","DELIVERED");session.setMetadata(metadata);
        f.service.backfillHistory(session.getId());f.service.deliverResult(session.getId());
        verifyNoInteractions(f.kafka);assertEquals("PENDING",session.getMetadata().get("outboxState"));
        var upgraded=f.published.mapper.convertValue(session.getMetadata().get("resultEvent"),LearningResultEvent.class);
        assertNull(upgraded.getDisplayTitle());assertEquals(2,upgraded.getEventVersion());
        assertEquals(event.get("eventId"),((Map<?,?>)session.getMetadata().get("resultEvent")).get("eventId"));
    }
}
