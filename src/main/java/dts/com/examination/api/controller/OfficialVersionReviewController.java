package dts.com.examination.api.controller;

import dts.com.examination.application.service.OfficialVersionReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/exam-versions")
public class OfficialVersionReviewController {
    private final OfficialVersionReviewService reviews;
    public record RejectRequest(@NotBlank @Size(max=500) String reason) { }

    private UUID actor(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID actor)) {
            throw new org.springframework.security.access.AccessDeniedException("Authenticated actor required");
        }
        return actor;
    }

    @GetMapping("/{versionId}/review")
    @PreAuthorize("@examAdminAccess.can(authentication, 'exam_version', 'read')")
    public ResponseEntity<OfficialVersionReviewService.ReviewResponse> current(@PathVariable UUID versionId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviews.current(versionId));
    }

    @PostMapping("/{versionId}/submit-review")
    @PreAuthorize("@examAdminAccess.can(authentication, 'exam_version', 'submit')")
    public ResponseEntity<OfficialVersionReviewService.ReviewResponse> submit(
            @PathVariable UUID versionId, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviews.submit(versionId, actor(authentication)));
    }

    @PostMapping("/{versionId}/approve")
    @PreAuthorize("@examAdminAccess.can(authentication, 'exam_version', 'approve')")
    public ResponseEntity<OfficialVersionReviewService.ReviewResponse> approve(
            @PathVariable UUID versionId, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviews.approve(versionId, actor(authentication)));
    }

    @PostMapping("/{versionId}/reject")
    @PreAuthorize("@examAdminAccess.can(authentication, 'exam_version', 'approve')")
    public ResponseEntity<OfficialVersionReviewService.ReviewResponse> reject(
            @PathVariable UUID versionId, Authentication authentication,
            @Valid @RequestBody RejectRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reviews.reject(versionId, actor(authentication), request.reason()));
    }
}
