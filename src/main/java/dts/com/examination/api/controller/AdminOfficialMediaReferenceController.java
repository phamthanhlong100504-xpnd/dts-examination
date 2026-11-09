package dts.com.examination.api.controller;

import dts.com.examination.application.service.adminmedia.OfficialExamMediaReferenceService;
import dts.com.examination.application.service.adminmedia.OfficialExamMediaReferenceService.ReferenceReport;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Owner-local inventory; never grants Media retirement or leaks student exam answers. */
@RestController
@RequestMapping("/api/v1/admin/media-references")
public class AdminOfficialMediaReferenceController {
    private final OfficialExamMediaReferenceService service;
    public AdminOfficialMediaReferenceController(OfficialExamMediaReferenceService service) {
        this.service = service;
    }
    @GetMapping("/{mediaId}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PERM_media:read')")
    public ResponseEntity<ReferenceReport> inspect(@PathVariable UUID mediaId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.inspect(mediaId));
    }
}
