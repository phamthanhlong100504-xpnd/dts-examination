package dts.com.examination.api.controller;

import dts.com.examination.application.service.AdminOfficialSessions;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/exam-sessions")
@RequiredArgsConstructor
public class AdminOfficialExamSessionController {
    private final AdminOfficialSessions sessions;
    private final dts.com.examination.application.service.session.OfficialExamSessionService official;

    @GetMapping
    @PreAuthorize("@examAdminAccess.can(authentication, 'exam_session', 'read')")
    public ResponseEntity<AdminOfficialSessions.SessionPage> list(
            @RequestParam(required = false) UUID examId,
            @RequestParam(required = false) UUID versionId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String licenseClass,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant startedFrom,
            @RequestParam(required = false) Instant startedUntil,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sessions.list(
                new AdminOfficialSessions.Filter(examId, versionId, userId, licenseClass, status,
                        startedFrom, startedUntil, page, size)));
    }

    @GetMapping("/{sessionId}")
    @PreAuthorize("@examAdminAccess.can(authentication, 'exam_session', 'read')")
    public ResponseEntity<AdminOfficialSessions.SessionDetail> detail(@PathVariable UUID sessionId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sessions.detail(sessionId));
    }

    @GetMapping("/{sessionId}/result-release")
    @PreAuthorize("@examAdminAccess.can(authentication, 'exam_session', 'read')")
    public ResponseEntity<AdminOfficialSessions.ReleaseStatus> resultRelease(@PathVariable UUID sessionId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sessions.releaseStatus(sessionId));
    }
    /** Manual result publication is distinct from read permission and student session routes. */
    @PostMapping("/{sessionId}/release-result")
    @PreAuthorize("@examAdminAccess.can(authentication, 'exam_session', 'release_result')")
    public ResponseEntity<Void> releaseResult(@PathVariable UUID sessionId, java.security.Principal principal) {
        if(principal==null || principal.getName()==null) throw new org.springframework.security.access.AccessDeniedException("Admin actor required");
        official.releaseManually(sessionId,UUID.fromString(principal.getName()));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
