package dts.com.examination.api.controller;

import dts.com.examination.api.form.StartExamSessionRequest;
import dts.com.examination.api.response.ExamSessionResponse;
import dts.com.examination.application.service.ExamSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exam-sessions")
@RequiredArgsConstructor
public class ExamSessionController {

    private final ExamSessionService examSessionService;

    @PostMapping
    // @PreAuthorize("hasAuthority('PERM_exam_session:create')")
    public ResponseEntity<ExamSessionResponse> startSession(
            @Valid @RequestBody StartExamSessionRequest request,
            Principal principal) {
        
        UUID userId = UUID.fromString(principal.getName());
        ExamSessionResponse response = examSessionService.startSession(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{sessionId}")
    // @PreAuthorize("hasAuthority('PERM_exam_session:read')")
    public ResponseEntity<dts.com.examination.api.response.ExamSessionDetailResponse> getSessionDetail(
            @PathVariable UUID sessionId,
            Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        dts.com.examination.api.response.ExamSessionDetailResponse response = examSessionService.getSessionDetail(sessionId, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sessionId}/paper")
    // @PreAuthorize("hasAuthority('PERM_exam_session:read')")
    public ResponseEntity<dts.com.examination.api.response.ExamPaperResponse> getExamPaper(
            @PathVariable UUID sessionId,
            Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        dts.com.examination.api.response.ExamPaperResponse response = examSessionService.getExamPaper(sessionId, userId);
        return ResponseEntity.ok(response);
    }

    @org.springframework.web.bind.annotation.PostMapping("/{sessionId}/answers")
    // @PreAuthorize("hasAuthority('PERM_exam_session:update')")
    public ResponseEntity<dts.com.examination.api.response.SaveAnswersResponse> saveAnswers(
            @PathVariable UUID sessionId,
            @Valid @RequestBody dts.com.examination.api.form.SaveAnswersRequest request,
            Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        dts.com.examination.api.response.SaveAnswersResponse response = examSessionService.saveAnswers(sessionId, request, userId);
        return ResponseEntity.ok(response);
    }

    @org.springframework.web.bind.annotation.PostMapping("/{sessionId}/submit")
    // @PreAuthorize("hasAuthority('PERM_exam_session:update')")
    public ResponseEntity<dts.com.examination.api.response.SubmitExamResponse> submitExam(
            @PathVariable UUID sessionId,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        dts.com.examination.api.response.SubmitExamResponse response = examSessionService.submitExam(sessionId, idempotencyKey, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sessionId}/result")
    // @PreAuthorize("hasAuthority('PERM_exam_session:read')")
    public ResponseEntity<dts.com.examination.api.response.ExamResultResponse> getExamResult(
            @PathVariable UUID sessionId,
            Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        dts.com.examination.api.response.ExamResultResponse response = examSessionService.getExamResult(sessionId, userId);
        return ResponseEntity.ok(response);
    }
    @GetMapping("/{sessionId}/progress")
    // @PreAuthorize("hasAuthority('PERM_exam_session:read')")
    public ResponseEntity<dts.com.examination.api.response.SessionProgressResponse> getSessionProgress(
            @PathVariable UUID sessionId,
            Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(examSessionService.getSessionProgress(sessionId, userId));
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{sessionId}/pause")
    // @PreAuthorize("hasAuthority('PERM_exam_session:update')")
    public ResponseEntity<dts.com.examination.api.response.PauseSessionResponse> pauseSession(
            @PathVariable UUID sessionId,
            Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(examSessionService.pauseSession(sessionId, userId));
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{sessionId}/resume")
    // @PreAuthorize("hasAuthority('PERM_exam_session:update')")
    public ResponseEntity<dts.com.examination.api.response.ResumeSessionResponse> resumeSession(
            @PathVariable UUID sessionId,
            Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(examSessionService.resumeSession(sessionId, userId));
    }

}
