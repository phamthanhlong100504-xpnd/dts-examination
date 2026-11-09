package dts.com.examination.api.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Monitors the source-owned legacy official-result delivery, never exposing graded content. */
@RestController
@RequestMapping("/api/v1/admin/integrations")
public class AdminOfficialSyncController {
    private final Monitor monitor;
    public AdminOfficialSyncController(Monitor monitor){this.monitor=monitor;}

    @GetMapping("/official-history")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PERM_exam_session:read') and hasAuthority('PERM_integrations:read')")
    public ResponseEntity<Map<String,Object>> summary(){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(monitor.summary());
    }
    @GetMapping("/official-history/{sessionId}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PERM_exam_session:read') and hasAuthority('PERM_integrations:read')")
    public ResponseEntity<Map<String,Object>> inspect(@PathVariable UUID sessionId){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(monitor.inspect(sessionId));
    }

    @Service
    @Transactional(readOnly=true)
    public static class Monitor {
        private final JdbcTemplate jdbc;
        public Monitor(JdbcTemplate jdbc){this.jdbc=jdbc;}
        private static final String SCOPE="""
            FROM exam_sessions s JOIN exam_versions v ON v.id=s.exam_version_id
            JOIN exams e ON e.id=v.exam_id
            WHERE s.deleted_at IS NULL AND v.deleted_at IS NULL AND e.deleted_at IS NULL
              AND v.exam_type='OFFICIAL' AND s.status='SUBMITTED'
            """;
        public Map<String,Object> summary(){
            Map<String,Object> row=jdbc.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE s.metadata->>'legacyHistoryOutboxState'='PENDING')::bigint AS pending,
                  COUNT(*) FILTER (WHERE s.metadata->>'legacyHistoryOutboxState'='DELIVERED')::bigint AS broker_acknowledged,
                  COUNT(*) FILTER (WHERE s.metadata->>'legacyHistoryOutboxState'='PENDING'
                      AND COALESCE((s.metadata->>'legacyHistoryOutboxAttempts')::integer,0)>0)::bigint AS retrying,
                  MIN(s.submitted_at) FILTER (WHERE s.metadata->>'legacyHistoryOutboxState'='PENDING') AS oldest_pending_at,
                  MAX(s.updated_at) FILTER (WHERE s.metadata->>'legacyHistoryOutboxState'='DELIVERED') AS last_delivered_update_at
                """+SCOPE);
            var out=new LinkedHashMap<String,Object>();out.put("sourceService","dts-examination");
            out.put("asOf",Instant.now());out.put("dataStatus",((Number)row.get("pending")).longValue()>0?"PARTIAL":"READY");
            out.put("stream","legacy_official_history_only");out.put("sourceOutbox",row);
            out.put("otherOfficialDeliveryModes","NOT_COVERED_BY_LEGACY_OUTBOX");
            out.put("consumerReceived",null);out.put("projectionApplied",null);
            out.put("brokerAckMeansProjectionApplied",false);return out;
        }
        public Map<String,Object> inspect(UUID sessionId){
            if(sessionId==null)throw new IllegalArgumentException("sessionId required");
            List<Map<String,Object>> rows=jdbc.queryForList("""
                SELECT s.id AS session_id,s.metadata->>'legacyHistoryOutboxState' AS outbox_state,
                  s.metadata->>'legacyHistoryOutboxAttempts' AS attempts,
                  s.metadata->>'legacyHistoryNextAttemptAt' AS next_attempt_at
                """+SCOPE+" AND s.id=?",sessionId);
            if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Official submitted session not found");
            Map<String,Object> row=rows.getFirst();
            String state=(String)row.get("outbox_state");
            var out=new LinkedHashMap<String,Object>();out.put("sourceService","dts-examination");
            out.put("asOf",Instant.now());out.put("dataStatus","READY");out.put("sessionId",sessionId);
            out.put("sourceStage","PENDING".equals(state)?"SOURCE_QUEUED":"DELIVERED".equals(state)?"BROKER_ACKNOWLEDGED":"NOT_IN_LEGACY_OUTBOX");
            out.put("attempts",row.get("attempts"));out.put("nextAttemptAt",row.get("next_attempt_at"));
            out.put("consumerReceived",null);out.put("projectionApplied",null);out.put("targetApiReady",null);
            return out;
        }
    }
}
