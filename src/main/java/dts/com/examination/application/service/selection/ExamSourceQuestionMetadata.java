package dts.com.examination.application.service.selection;

import com.fasterxml.jackson.databind.ObjectMapper;
import dts.com.examination.application.exception.ExaminationException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The existing licensed Content Builder metadata contract, without answer keys. */
public record ExamSourceQuestionMetadata(
        UUID id, List<UUID> optionIds, List<UUID> chapterIds, Boolean isCritical,
        String status, String questionType, List<UUID> mediaFileIds, String contentVersion) {
    public ExamSourceQuestionMetadata {
        optionIds = optionIds == null ? null : List.copyOf(optionIds);
        chapterIds = chapterIds == null ? null : List.copyOf(chapterIds);
        mediaFileIds = mediaFileIds == null ? null : List.copyOf(mediaFileIds);
    }

    public static ExamSourceQuestionMetadata fromMap(ObjectMapper mapper, Map<String, Object> value) {
        try {
            if (value == null || !(value.get("isCritical") instanceof Boolean)) {
                throw new IllegalArgumentException("Missing source critical flag");
            }
            return mapper.convertValue(value, ExamSourceQuestionMetadata.class);
        } catch (IllegalArgumentException e) {
            throw new ExaminationException(HttpStatus.BAD_GATEWAY, "SOURCE_INVALID_RESPONSE",
                    "Content Builder returned invalid question metadata", e);
        }
    }
}
