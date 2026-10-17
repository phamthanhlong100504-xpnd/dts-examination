package dts.com.examination.application.client;

import dts.com.examination.api.response.InternalQuestionDetailResponse;
import dts.com.examination.application.exception.ExaminationException;
import dts.com.examination.application.service.selection.ExamQuestionPool;
import feign.FeignException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class ContentBuilderClientFallback
        implements ContentBuilderClient, FallbackFactory<ContentBuilderClient> {

    private final Throwable cause;

    public ContentBuilderClientFallback() {
        this(null);
    }

    private ContentBuilderClientFallback(Throwable cause) {
        this.cause = cause;
    }

    @Override
    public ContentBuilderClient create(Throwable cause) {
        return new ContentBuilderClientFallback(cause);
    }

    @Override
    public ExamQuestionPool getQuestionBank(String bankVersion, String licenseClass) {
        throw failure(cause);
    }

    @Override
    public List<Map<String, Object>> getQuestionsMetadata(UUID contentId, String contentType) {
        return getQuestionsMetadata(contentId, contentType, null);
    }

    @Override
    public List<Map<String, Object>> getQuestionsMetadata(
            UUID contentId, String contentType, String licenseClass) {
        throw failure(cause);
    }

    @Override
    public List<InternalQuestionDetailResponse> getQuestionsBatch(List<UUID> questionIds) {
        return getQuestionsBatch(questionIds, null);
    }

    @Override
    public List<InternalQuestionDetailResponse> getQuestionsBatch(
            List<UUID> questionIds, String licenseClass) {
        throw failure(cause);
    }

    static ExaminationException failure(Throwable cause) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        FeignException upstream = null;
        Throwable current = cause;
        while (current != null && visited.add(current)) {
            if (current instanceof ExaminationException coded) {
                return coded;
            }
            if (upstream == null && current instanceof FeignException feign) {
                upstream = feign;
            }
            current = current.getCause();
        }
        return failureForStatus(upstream == null ? -1 : upstream.status(), cause);
    }

    static ExaminationException failureForStatus(int status, Throwable cause) {
        // Do not forward upstream response bodies or request credentials.
        if (status == 400) {
            return new ExaminationException(HttpStatus.BAD_REQUEST, "SOURCE_BAD_REQUEST",
                    "Question source rejected the requested content or license class", cause);
        }
        if (status == 404) {
            return new ExaminationException(HttpStatus.NOT_FOUND, "SOURCE_NOT_FOUND",
                    "Requested question source or question was not found", cause);
        }
        if (status == 409) {
            return new ExaminationException(HttpStatus.CONFLICT, "SOURCE_CONFLICT",
                    "Question source is not ready for this request", cause);
        }
        if (status == 401 || status == 403) {
            return new ExaminationException(HttpStatus.BAD_GATEWAY, "SOURCE_ACCESS_DENIED",
                    "Examination cannot authenticate with the question source", cause);
        }
        if (status <= 0 || status == 408 || status == 429 || status >= 500) {
            return new ExaminationException(HttpStatus.SERVICE_UNAVAILABLE, "SOURCE_UNAVAILABLE",
                    "Question source is temporarily unavailable. Please try again later.", cause);
        }
        return new ExaminationException(HttpStatus.BAD_GATEWAY, "SOURCE_INVALID_RESPONSE",
                "Question source returned an unsupported or invalid response", cause);
    }
}
