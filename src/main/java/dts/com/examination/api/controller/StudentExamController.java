package dts.com.examination.api.controller;

import dts.com.examination.api.response.*;
import dts.com.examination.application.service.StudentExamReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/exams")
@RequiredArgsConstructor
public class StudentExamController {
    private final StudentExamReadService exams;

    @GetMapping
    @PreAuthorize("isAuthenticated() and hasAuthority('ROLE_STUDENT')")
    public ResponseEntity<StudentExamListResponse> list(Principal principal,
            @RequestParam(defaultValue = "OPEN") String tab,
            @RequestParam(required = false) String licenseClass,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(exams.list(UUID.fromString(principal.getName()), tab, licenseClass, page, size));
    }

    @GetMapping("/{examId}")
    @PreAuthorize("isAuthenticated() and hasAuthority('ROLE_STUDENT')")
    public ResponseEntity<StudentExamDetailResponse> detail(Principal principal,
            @PathVariable UUID examId, @RequestParam(required = false) UUID examVersionId) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(exams.detail(UUID.fromString(principal.getName()), examId, examVersionId));
    }
}
