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
}
