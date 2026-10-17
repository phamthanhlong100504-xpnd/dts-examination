package dts.com.examination.application.client;

import dts.com.examination.application.exception.ExaminationException;
import feign.FeignException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class IdentityProfileClientFallback implements FallbackFactory<IdentityProfileClient> {
    @Override public IdentityProfileClient create(Throwable cause) {
        return authorization -> { throw failure(cause); };
    }

    public static ExaminationException failure(Throwable cause) {
        int inspected = 0;
        for (Throwable current = cause; current != null && inspected++ < 8; current = current.getCause()) {
            if (current instanceof ExaminationException known) return known;
            if (current instanceof FeignException feign) return failureForStatus(feign.status());
            if (current == current.getCause()) break;
        }
        return failureForStatus(-1);
    }

    public static ExaminationException failureForStatus(int status) {
        if (status == 401) return new ExaminationException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "A valid access token is required");
        if (status == 403 || status == 404) return new ExaminationException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "The Identity profile is unavailable to this account");
        if (status == -1 || status == 408 || status == 429 || status >= 500) {
            return new ExaminationException(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_UNAVAILABLE", "Identity is temporarily unavailable");
        }
        return new ExaminationException(HttpStatus.BAD_GATEWAY, "IDENTITY_INVALID_RESPONSE", "Identity returned an invalid profile response");
    }
}
