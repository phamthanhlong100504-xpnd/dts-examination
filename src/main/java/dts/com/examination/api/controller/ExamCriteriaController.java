package dts.com.examination.api.controller;

import dts.com.examination.api.form.CreateExamCriteriaRequest;
import dts.com.examination.api.form.UpdateExamCriteriaRequest;
import dts.com.examination.api.response.ExamCriteriaResponse;
import dts.com.examination.api.response.PageResponse;
import dts.com.examination.application.service.ExamCriteriaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exam-criterias")
@RequiredArgsConstructor
public class ExamCriteriaController {

    private final ExamCriteriaService examCriteriaService;

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_exam_criteria:create')")
    public ResponseEntity<ExamCriteriaResponse> createCriteria(@RequestBody @Valid CreateExamCriteriaRequest request) {
        ExamCriteriaResponse response = examCriteriaService.createCriteria(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_exam_criteria:read')")
    public ResponseEntity<PageResponse<ExamCriteriaResponse>> getCriteriaList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        PageResponse<ExamCriteriaResponse> response = examCriteriaService.getCriteriaList(page, size, keyword, status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{criteriaId}")
    @PreAuthorize("hasAuthority('PERM_exam_criteria:read')")
    public ResponseEntity<ExamCriteriaResponse> getCriteriaDetail(@PathVariable UUID criteriaId) {
        ExamCriteriaResponse response = examCriteriaService.getCriteriaDetail(criteriaId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{criteriaId}")
    @PreAuthorize("hasAuthority('PERM_exam_criteria:update')")
    public ResponseEntity<ExamCriteriaResponse> updateCriteria(
            @PathVariable UUID criteriaId,
            @RequestBody @Valid UpdateExamCriteriaRequest request) {
        ExamCriteriaResponse response = examCriteriaService.updateCriteria(criteriaId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{criteriaId}")
    @PreAuthorize("hasAuthority('PERM_exam_criteria:delete')")
    public ResponseEntity<Void> deleteCriteria(@PathVariable UUID criteriaId) {
        examCriteriaService.deleteCriteria(criteriaId);
        return ResponseEntity.noContent().build();
    }
}
