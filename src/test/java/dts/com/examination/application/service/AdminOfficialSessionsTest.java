package dts.com.examination.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dts.com.examination.application.exception.ExaminationException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminOfficialSessionsTest {
    private final ObjectMapper json = new ObjectMapper();
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final AdminOfficialSessions service = new AdminOfficialSessions(jdbc, json,
            Clock.fixed(Instant.parse("2026-11-06T13:00:00Z"), ZoneOffset.UTC));

    @Test void releaseStatusesUseFrozenModeAndClockWithoutReturningGrades() throws Exception {
        var metadata = json.readTree("""
                {"gradingSnapshot":{"score":20,"maxScore":30,"questions":[]},
                 "officialPaper":{"examEndedAt":"2026-11-06T14:00:00Z"}}
                """);
        assertEquals("GRADED", AdminOfficialSessions.grading("SUBMITTED", metadata));
        assertEquals("HELD", AdminOfficialSessions.release("SUBMITTED", metadata, "MANUAL", Instant.parse("2026-11-06T13:00:00Z")));
        assertEquals("SCHEDULED", AdminOfficialSessions.release("SUBMITTED", metadata, "AFTER_EXAM_END", Instant.parse("2026-11-06T13:00:00Z")));
        assertEquals("RELEASED", AdminOfficialSessions.release("SUBMITTED", metadata, "AFTER_EXAM_END", Instant.parse("2026-11-06T15:00:00Z")));
        assertEquals("RELEASED", AdminOfficialSessions.release("SUBMITTED", metadata, "IMMEDIATE", Instant.parse("2026-11-06T13:00:00Z")));
        assertEquals("NOT_AVAILABLE", AdminOfficialSessions.release("IN_PROGRESS", metadata, "IMMEDIATE", Instant.parse("2026-11-06T13:00:00Z")));
        assertFalse(json.writeValueAsString(new AdminOfficialSessions.SessionRow(null,null,null,null,"B",1,"SUBMITTED",null,null,"GRADED","HELD",null)).contains("score"));
    }

    @Test void missingOrPartialGradeDoesNotClaimRelease() throws Exception {
        var metadata = json.readTree("""
                {"gradingSnapshot":{"score":20},"resultReleasedAt":"2026-11-06T13:00:00Z"}
                """);
        assertEquals("PENDING", AdminOfficialSessions.grading("SUBMITTED", metadata));
        assertEquals("NOT_AVAILABLE", AdminOfficialSessions.release("SUBMITTED", metadata, "MANUAL", Instant.now()));
    }

    @Test void badFiltersAreRejectedBeforeDatabase() {
        assertThrows(ExaminationException.class, () -> service.list(new AdminOfficialSessions.Filter(null,null,null,null,null,null,null,-1,20)));
        assertThrows(ExaminationException.class, () -> service.list(new AdminOfficialSessions.Filter(null,null,null,null,null,null,null,0,51)));
        assertThrows(ExaminationException.class, () -> service.list(new AdminOfficialSessions.Filter(null,null,null,"INVALID!",null,null,null,0,20)));
        assertThrows(ExaminationException.class, () -> service.list(new AdminOfficialSessions.Filter(null,null,null,"B","BROKEN",null,null,0,20)));
        verifyNoInteractions(jdbc);
    }

    @Test void queryOnlyUsesOfficialSessionTablesAndBoundParameters() {
        when(jdbc.queryForObject(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class),
                any(org.springframework.jdbc.core.RowMapper.class))).thenReturn(List.of());
        var page = service.list(new AdminOfficialSessions.Filter(null,null,null,"B","SUBMITTED",null,null,0,20));
        assertEquals(0, page.totalElements());
        var query = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(query.capture(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class),
                any(org.springframework.jdbc.core.RowMapper.class));
        assertTrue(query.getValue().contains("v.exam_type='OFFICIAL'"));
        assertTrue(query.getValue().contains("s.started_at DESC,s.id DESC"));
        assertFalse(query.getValue().contains("answer_snapshot"));
    }
}
