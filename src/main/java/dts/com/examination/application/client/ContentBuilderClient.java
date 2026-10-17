package dts.com.examination.application.client;

import dts.com.examination.api.response.InternalQuestionDetailResponse;
import dts.com.examination.application.service.selection.ExamQuestionPool;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(
        name = "content-builder",
        url = "${CONTENT_BUILDER_URL:}",
        configuration = ContentBuilderFeignConfiguration.class,
        fallbackFactory = ContentBuilderClientFallback.class)
public interface ContentBuilderClient {

    @GetMapping("/api/v1/content-builder/internal/questions/bank")
    @CircuitBreaker(name = "contentBuilder", fallbackMethod = "getQuestionBankFallback")
    ExamQuestionPool getQuestionBank(@RequestParam("bankVersion") String bankVersion,
            @RequestParam("licenseClass") String licenseClass);

    default ExamQuestionPool getQuestionBankFallback(String bankVersion, String licenseClass, Exception error) {
        throw ContentBuilderClientFallback.failure(error);
    }

    // Keep the existing signature for current consumers.
    default List<Map<String, Object>> getQuestionsMetadata(UUID contentId, String contentType) {
        return getQuestionsMetadata(contentId, contentType, null);
    }

    @GetMapping("/api/v1/content-builder/internal/questions/metadata")
    @CircuitBreaker(name = "contentBuilder", fallbackMethod = "getQuestionsMetadataFallback")
    List<Map<String, Object>> getQuestionsMetadata(
            @RequestParam("contentId") UUID contentId,
            @RequestParam("contentType") String contentType,
            @RequestParam(value = "licenseClass", required = false) String licenseClass);

    // Keep the existing signature for current consumers.
    default List<InternalQuestionDetailResponse> getQuestionsBatch(List<UUID> questionIds) {
        return getQuestionsBatch(questionIds, null);
    }

    @PostMapping("/api/v1/content-builder/internal/questions/batch")
    @CircuitBreaker(name = "contentBuilder", fallbackMethod = "getQuestionsBatchFallback")
    List<InternalQuestionDetailResponse> getQuestionsBatch(
            @RequestBody List<UUID> questionIds,
            @RequestParam(value = "licenseClass", required = false) String licenseClass);

    // Keep the existing fallback signatures as well.
    default List<Map<String, Object>> getQuestionsMetadataFallback(
            UUID contentId, String contentType, Exception exception) {
        return getQuestionsMetadataFallback(contentId, contentType, null, exception);
    }

    default List<Map<String, Object>> getQuestionsMetadataFallback(
            UUID contentId, String contentType, String licenseClass, Exception exception) {
        throw ContentBuilderClientFallback.failure(exception);
    }

    default List<InternalQuestionDetailResponse> getQuestionsBatchFallback(
            List<UUID> questionIds, Exception exception) {
        return getQuestionsBatchFallback(questionIds, null, exception);
    }

    default List<InternalQuestionDetailResponse> getQuestionsBatchFallback(
            List<UUID> questionIds, String licenseClass, Exception exception) {
        throw ContentBuilderClientFallback.failure(exception);
    }
}
