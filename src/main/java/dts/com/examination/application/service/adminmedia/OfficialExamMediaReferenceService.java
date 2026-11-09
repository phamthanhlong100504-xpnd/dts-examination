package dts.com.examination.application.service.adminmedia;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Read-only references owned by Examination. Not a global Media retirement certificate. */
@Service
public class OfficialExamMediaReferenceService {
    private static final int LIMIT = 500;
    private final JdbcTemplate jdbc;

    public OfficialExamMediaReferenceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static void requireReadGrant() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() ||
            a.getAuthorities().stream().noneMatch(x -> "ROLE_ADMIN".equals(x.getAuthority())) ||
            a.getAuthorities().stream().noneMatch(x -> "PERM_media:read".equals(x.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "EXAM_MEDIA_REFERENCE_READ_FORBIDDEN");
        }
    }

    public record ReferenceItem(String resourceType, String resourceId, String versionId,
                                String usageType, String referenceStatus) {}
    public record ReferenceReport(UUID mediaId, String source, boolean verified,
                                  String reasonCode, int knownReferenceCount,
                                  boolean truncated, boolean canRetire,
                                  List<ReferenceItem> references) {}

    private static boolean add(List<ReferenceItem> output, List<ReferenceItem> source) {
        int remaining=Math.max(0,LIMIT-output.size());
        output.addAll(source.subList(0,Math.min(source.size(),remaining)));
        return source.size()>remaining;
    }

    @Transactional(readOnly = true)
    public ReferenceReport inspect(UUID mediaId) {
        requireReadGrant(); // Authenticate before querying any historical record.
        if (mediaId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MEDIA_ID_REQUIRED");
        }
        List<ReferenceItem> refs = new ArrayList<>();
        boolean truncated = false;
        // The exam parent can have its own thumbnail outside the version snapshot.
        truncated |= add(refs, jdbc.query("""
            SELECT e.id::text AS resource_id, e.status::text AS reference_status
              FROM exams e WHERE e.thumbnail_id=? ORDER BY e.id LIMIT 501
            """, (r,n) -> new ReferenceItem("official-exams",r.getString("resource_id"),
                null,"EXAM_PARENT_THUMBNAIL",r.getString("reference_status")),mediaId));
        // Historical official exam version thumbnails are retained after archive.
        truncated |= add(refs, jdbc.query("""
            SELECT v.id::text AS resource_id, v.version_no::text AS version_id,
                   v.status::text AS reference_status
            FROM exam_versions v WHERE v.thumbnail_id=?
            ORDER BY v.id LIMIT 501
            """, (r,n) -> new ReferenceItem("official-exam-versions", r.getString("resource_id"),
                r.getString("version_id"), "EXAM_THUMBNAIL", r.getString("reference_status")), mediaId));
        // A manually configured exam version freezes the question images in its config.
        truncated |= add(refs, jdbc.query("""
            SELECT v.id::text AS resource_id, v.version_no::text AS version_id,
                   v.status::text AS reference_status
            FROM exam_versions v
            WHERE jsonb_typeof(v.configs->'manualQuestionsSnapshot')='array'
              AND EXISTS (
                SELECT 1 FROM jsonb_array_elements(CASE WHEN jsonb_typeof(v.configs->'manualQuestionsSnapshot')='array' THEN v.configs->'manualQuestionsSnapshot' ELSE '[]'::jsonb END) AS q(item)
                WHERE jsonb_typeof(q.item->'mediaFileIds')='array'
                  AND jsonb_exists(q.item->'mediaFileIds',?)
              )
            ORDER BY v.id LIMIT 501
            """, (r,n) -> new ReferenceItem("official-exam-versions", r.getString("resource_id"),
                r.getString("version_id"), "MANUAL_QUESTION_MEDIA", r.getString("reference_status")),
                mediaId.toString()));
        // The student's immutable official paper is in exam_sessions.metadata. No user ID,
        // answer key, selection or question text is ever returned to the Admin browser.
        truncated |= add(refs, jdbc.query("""
            SELECT s.id::text AS resource_id, s.exam_version_id::text AS version_id,
                   s.status::text AS reference_status
            FROM exam_sessions s
            WHERE jsonb_typeof(s.metadata->'officialPaper'->'questions')='array'
              AND EXISTS (
                SELECT 1 FROM jsonb_array_elements(CASE WHEN jsonb_typeof(s.metadata->'officialPaper'->'questions')='array' THEN s.metadata->'officialPaper'->'questions' ELSE '[]'::jsonb END) AS q(item)
                WHERE jsonb_typeof(q.item->'mediaFileIds')='array'
                  AND jsonb_exists(q.item->'mediaFileIds',?)
              )
            ORDER BY s.id LIMIT 501
            """, (r,n) -> new ReferenceItem("official-exam-sessions", r.getString("resource_id"),
                r.getString("version_id"), "FROZEN_SESSION_MEDIA", r.getString("reference_status")),
                mediaId.toString()));
        // These queries cover known Examination owners only. All-service historical
        // completeness and concurrent writers are NOT certified here.
        return new ReferenceReport(mediaId,"EXAMINATION",false,
            "OTHER_SERVICE_AND_LEGACY_REFERENCES_NOT_VERIFIED",
            refs.size(),truncated,false,List.copyOf(refs));
    }
}
