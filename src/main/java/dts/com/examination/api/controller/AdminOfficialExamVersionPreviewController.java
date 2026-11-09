package dts.com.examination.api.controller;

import dts.com.examination.application.service.AdminOfficialVersionPreview;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Does not change the existing version CRUD or publish workflow. */
@RestController
@RequestMapping("/api/v1/exam-versions")
@RequiredArgsConstructor
public class AdminOfficialExamVersionPreviewController {
    private final AdminOfficialVersionPreview preview;

    @PostMapping("/{versionId}/validate")
    @PreAuthorize("@examAdminAccess.can(authentication, 'exam_version', 'read')")
    public ResponseEntity<AdminOfficialVersionPreview.Validation> validate(@PathVariable UUID versionId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(preview.validate(versionId));
    }
}
