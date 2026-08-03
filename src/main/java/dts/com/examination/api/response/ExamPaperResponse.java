package dts.com.examination.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamPaperResponse {
    private UUID sessionId;
    private String status;
    private Long remainingSeconds;
    private List<QuestionPaperResponse> questions;
}
