package dts.com.examination.api.controller;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** BE06-08: read-only official-review journal; never exposes exam answers or grades. */
@RestController
@RequestMapping("/api/v1/admin/audit")
public class AdminOfficialAuditStreamController {
    private final ReviewAuditReader reader;
    public AdminOfficialAuditStreamController(ReviewAuditReader reader) { this.reader = reader; }

    @GetMapping("/official-reviews")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PERM_audit:read')")
    public ResponseEntity<Map<String,Object>> list(
            @RequestParam(required=false) Instant from,
            @RequestParam(required=false) Instant to,
            @RequestParam(required=false) UUID actorId,
            @RequestParam(required=false) UUID versionId,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="30") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(reader.list(from,to,actorId,versionId,page,size));
    }

    @Service
    @Transactional(readOnly=true)
    public static class ReviewAuditReader {
        private final JdbcTemplate jdbc;
        public ReviewAuditReader(JdbcTemplate jdbc) { this.jdbc = jdbc; }
        private static final String SQL = """
            SELECT audit_id::text AS "auditId",actor_id AS "actorId",action,
                'official-exam-version-review'::text AS "resourceType",
                exam_version_id::text AS "resourceId",exam_version_id::text AS "versionId",
                review_id::text AS "reviewId",occurred_at AS "occurredAt",
                'RECORDED'::text AS result,(reason IS NOT NULL) AS "reasonPresent"
            FROM public.admin_official_review_audit
            WHERE occurred_at >= ? AND occurred_at < ?
              AND (?::uuid IS NULL OR actor_id=?::uuid)
              AND (?::uuid IS NULL OR exam_version_id=?::uuid)
            ORDER BY occurred_at DESC,audit_id DESC
            LIMIT ? OFFSET ?
            """;
        public Map<String,Object> list(Instant from, Instant to, UUID actorId,
                                       UUID versionId, int page, int size) {
            Instant now=Instant.now(), end=to==null?now:to;
            Instant start=from==null?end.minus(Duration.ofDays(30)):from;
            if (page<0 || page>1000 || size<1 || size>100 || !start.isBefore(end)
                    || end.isAfter(now.plusSeconds(60))
                    || Duration.between(start,end).compareTo(Duration.ofDays(90))>0)
                throw new IllegalArgumentException("Invalid audit interval or pagination");
            List<Map<String,Object>> rows=jdbc.queryForList(SQL,
                Timestamp.from(start),Timestamp.from(end),actorId,actorId,
                versionId,versionId,size+1,(long)page*size);
            boolean hasMore=rows.size()>size;
            var out=new LinkedHashMap<String,Object>();
            out.put("sourceService","dts-examination");out.put("dataStatus","READY");
            out.put("asOf",now);out.put("from",start);out.put("to",end);
            out.put("timezone","Asia/Ho_Chi_Minh");
            out.put("page",page);out.put("size",size);out.put("hasMore",hasMore);
            out.put("items",new ArrayList<>(rows.subList(0,Math.min(size,rows.size()))));
            out.put("coverage",List.of("admin_official_review_audit"));
            out.put("warnings",List.of("Free-text review reasons and approval fingerprint are not exposed",
                "No conclusion about publication or student result readiness can be inferred from this audit"));
            return out;
        }
    }
}
