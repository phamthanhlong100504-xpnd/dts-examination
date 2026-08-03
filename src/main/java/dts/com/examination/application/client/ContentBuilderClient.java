package dts.com.examination.application.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "content-builder")
public interface ContentBuilderClient {
    @GetMapping("/api/v1/content-builder/internal/questions/metadata")
    List<Map<String, Object>> getQuestionsMetadata(@RequestParam("contentId") UUID contentId, @RequestParam("contentType") String contentType);

    @org.springframework.web.bind.annotation.PostMapping("/api/v1/content-builder/internal/questions/batch")
    List<dts.com.examination.api.response.InternalQuestionDetailResponse> getQuestionsBatch(@org.springframework.web.bind.annotation.RequestBody List<UUID> questionIds);
}
