package dts.com.examination.api.controller;

import dts.com.examination.api.form.CreateExamRuleRequest;
import dts.com.examination.api.form.UpdateExamRuleRequest;
import dts.com.examination.api.response.ExamRuleResponse;
import dts.com.examination.api.response.PageResponse;
import dts.com.examination.application.service.ExamRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exam-rules")
@RequiredArgsConstructor
public class ExamRuleController {

    private final ExamRuleService examRuleService;

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_exam_rule:create')")
    public ResponseEntity<ExamRuleResponse> createRule(@RequestBody @Valid CreateExamRuleRequest request) {
        ExamRuleResponse response = examRuleService.createRule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_exam_rule:read')")
    public ResponseEntity<PageResponse<ExamRuleResponse>> getRuleList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        PageResponse<ExamRuleResponse> response = examRuleService.getRuleList(page, size, keyword, status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('PERM_exam_rule:read')")
    public ResponseEntity<ExamRuleResponse> getRuleDetail(@PathVariable UUID ruleId) {
        ExamRuleResponse response = examRuleService.getRuleDetail(ruleId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('PERM_exam_rule:update')")
    public ResponseEntity<ExamRuleResponse> updateRule(
            @PathVariable UUID ruleId,
            @RequestBody @Valid UpdateExamRuleRequest request) {
        ExamRuleResponse response = examRuleService.updateRule(ruleId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('PERM_exam_rule:delete')")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID ruleId) {
        examRuleService.deleteRule(ruleId);
        return ResponseEntity.noContent().build();
    }
}
