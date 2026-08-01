package dts.com.examination.api.controller;

import dts.com.examination.api.form.CreateExamVersionRequest;
import dts.com.examination.api.form.UpdateExamVersionRequest;
import dts.com.examination.api.response.ExamVersionResponse;
import dts.com.examination.application.service.ExamVersionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ExamVersionController {

    private final ExamVersionService examVersionService;

    @PostMapping("/exams/{examId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_exam_version:create')")
    public ExamVersionResponse createExamVersion(@PathVariable UUID examId,
                                                 @Valid @RequestBody CreateExamVersionRequest request) {
        return examVersionService.create(examId, request);
    }

    @GetMapping("/exams/{examId}/versions")
    @PreAuthorize("hasAuthority('PERM_exam_version:read')")
    public Page<ExamVersionResponse> getExamVersionList(@PathVariable UUID examId,
                                                        @RequestParam(required = false) String status,
                                                        Pageable pageable) {
        return examVersionService.getList(examId, status, pageable);
    }

    @GetMapping("/exam-versions/{versionId}")
    @PreAuthorize("hasAuthority('PERM_exam_version:read')")
    public ExamVersionResponse getExamVersionDetail(@PathVariable UUID versionId) {
        return examVersionService.getDetail(versionId);
    }

    @PatchMapping("/exam-versions/{versionId}")
    @PreAuthorize("hasAuthority('PERM_exam_version:update')")
    public ExamVersionResponse updateExamVersion(@PathVariable UUID versionId,
                                                 @Valid @RequestBody UpdateExamVersionRequest request) {
        return examVersionService.update(versionId, request);
    }

    @DeleteMapping("/exam-versions/{versionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_exam_version:delete')")
    public void deleteExamVersion(@PathVariable UUID versionId) {
        examVersionService.delete(versionId);
    }

    @PostMapping("/exam-versions/{versionId}/publish")
    @PreAuthorize("hasAuthority('PERM_exam_version:publish')")
    public ExamVersionResponse publishExamVersion(@PathVariable UUID versionId) {
        return examVersionService.publish(versionId);
    }

    @PostMapping("/exam-versions/{versionId}/archive")
    @PreAuthorize("hasAuthority('PERM_exam_version:archive')")
    public ExamVersionResponse archiveExamVersion(@PathVariable UUID versionId) {
        return examVersionService.archive(versionId);
    }

    @PostMapping("/exam-versions/{versionId}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_exam_version:clone')")
    public ExamVersionResponse cloneExamVersion(@PathVariable UUID versionId) {
        return examVersionService.clone(versionId);
    }
}
