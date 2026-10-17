package dts.com.examination.application.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "identity-service", contextId = "exam-identity-profile", url = "${IDENTITY_SERVICE_URL:}",
        configuration = IdentityProfileFeignConfiguration.class, fallbackFactory = IdentityProfileClientFallback.class)
public interface IdentityProfileClient {
    @GetMapping("/api/v1/users/me")
    IdentityMeResponse getCurrentUser(@RequestHeader("Authorization") String authorization);
}
