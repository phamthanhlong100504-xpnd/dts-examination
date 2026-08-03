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
}
