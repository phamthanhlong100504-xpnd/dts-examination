package dts.com.examination.application.client;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import java.util.concurrent.TimeUnit;

// Client-local configuration; never registers an interceptor that forwards credentials to other services.
public class IdentityProfileFeignConfiguration {
    @Bean public Logger.Level identityProfileLoggerLevel() { return Logger.Level.NONE; }
    @Bean public Retryer identityProfileRetryer() { return Retryer.NEVER_RETRY; }
    @Bean public Request.Options identityProfileRequestOptions() {
        return new Request.Options(5, TimeUnit.SECONDS, 10, TimeUnit.SECONDS, false);
    }
    @Bean public ErrorDecoder identityProfileErrorDecoder() {
        return (method, response) -> IdentityProfileClientFallback.failureForStatus(response.status());
    }
}
