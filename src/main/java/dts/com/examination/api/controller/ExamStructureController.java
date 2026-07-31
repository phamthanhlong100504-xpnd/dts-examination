package dts.com.examination.api.controller;

import dts.com.examination.api.form.CreateExamStructureRequest;
import dts.com.examination.api.form.UpdateExamStructureRequest;
import dts.com.examination.api.response.ExamStructureResponse;
import dts.com.examination.application.service.ExamStructureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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

import java.security.Principal;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/exam-structures")
@RequiredArgsConstructor
public class ExamStructureController {

    private final ExamStructureService examStructureService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_exam_structure:create')")
    public ExamStructureResponse create(
            @Valid @RequestBody CreateExamStructureRequest request,
            Principal principal) {
        UUID currentUserId = UUID.fromString(principal.getName());
        return examStructureService.create(request, currentUserId);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_exam_structure:read')")
    public Page<ExamStructureResponse> getList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return examStructureService.getList(keyword, status, pageable);
    }

    @GetMapping("/{structureId}")
    @PreAuthorize("hasAuthority('PERM_exam_structure:read')")
    public ExamStructureResponse getDetail(@PathVariable UUID structureId) {
        return examStructureService.getDetail(structureId);
    }

    @PatchMapping("/{structureId}")
    @PreAuthorize("hasAuthority('PERM_exam_structure:update')")
    public ExamStructureResponse update(
            @PathVariable UUID structureId,
            @Valid @RequestBody UpdateExamStructureRequest request,
            Principal principal) {
        UUID currentUserId = UUID.fromString(principal.getName());
        return examStructureService.update(structureId, request, currentUserId);
    }

    @DeleteMapping("/{structureId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_exam_structure:delete')")
    public void delete(
            @PathVariable UUID structureId,
            Principal principal) {
        UUID currentUserId = UUID.fromString(principal.getName());
        examStructureService.delete(structureId, currentUserId);
    }
}
