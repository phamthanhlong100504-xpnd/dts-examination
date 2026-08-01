package dts.com.examination.api.controller;

import dts.com.examination.api.form.ChangeExamStatusRequest;
import dts.com.examination.api.form.CreateExamRequest;
import dts.com.examination.api.form.UpdateExamRequest;
import dts.com.examination.api.response.ExamResponse;
import dts.com.examination.application.service.ExamService;
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
@RequestMapping("/api/v1/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_exam:create')")
    public ExamResponse create(@Valid @RequestBody CreateExamRequest request) {
        return examService.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_exam:read')")
    public Page<ExamResponse> getList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID createdBy,
            Pageable pageable) {
        return examService.getList(keyword, status, createdBy, pageable);
    }

    @GetMapping("/{examId}")
    @PreAuthorize("hasAuthority('PERM_exam:read')")
    public ExamResponse getDetail(@PathVariable UUID examId) {
        return examService.getDetail(examId);
    }

    @PatchMapping("/{examId}")
    @PreAuthorize("hasAuthority('PERM_exam:update')")
    public ExamResponse update(@PathVariable UUID examId, @Valid @RequestBody UpdateExamRequest request) {
        return examService.update(examId, request);
    }

    @DeleteMapping("/{examId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_exam:delete')")
    public void delete(@PathVariable UUID examId) {
        examService.delete(examId);
    }

    @PatchMapping("/{examId}/status")
    @PreAuthorize("hasAuthority('PERM_exam:update')")
    public ExamResponse changeStatus(@PathVariable UUID examId, @Valid @RequestBody ChangeExamStatusRequest request) {
        return examService.changeStatus(examId, request);
    }
}
