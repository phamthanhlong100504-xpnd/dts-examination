package dts.com.examination.application.client;

import dts.com.examination.api.response.InternalQuestionDetailResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "content-builder", fallback = ContentBuilderClientFallback.class)
public interface ContentBuilderClient {
    @GetMapping("/api/v1/content-builder/internal/questions/metadata")
    @CircuitBreaker(name = "contentBuilder", fallbackMethod = "getQuestionsMetadataFallback")
    List<Map<String, Object>> getQuestionsMetadata(@RequestParam("contentId") UUID contentId, @RequestParam("contentType") String contentType);

    @PostMapping("/api/v1/content-builder/internal/questions/batch")
    @CircuitBreaker(name = "contentBuilder", fallbackMethod = "getQuestionsBatchFallback")
    List<InternalQuestionDetailResponse> getQuestionsBatch(@RequestBody List<UUID> questionIds);

    // Fallback methods
    default List<Map<String, Object>> getQuestionsMetadataFallback(UUID contentId, String contentType, Exception ex) {
        throw new dts.com.examination.application.exception.BusinessRuleException(
                "Content service unavailable: unable to fetch question metadata. Please try again later."
        );
    }

    default List<InternalQuestionDetailResponse> getQuestionsBatchFallback(List<UUID> questionIds, Exception ex) {
        throw new dts.com.examination.application.exception.BusinessRuleException(
                "Content service unavailable: unable to fetch question details. Please try again later."
        );
    }
}
