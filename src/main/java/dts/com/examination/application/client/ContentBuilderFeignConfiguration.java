package dts.com.examination.application.client;

import dts.com.examination.config.security.JwtProperties;
import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import org.springframework.context.annotation.Bean;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

// Registered only through ContentBuilderClient's configuration attribute.
// No component annotation: this interceptor must not become a global bean.
public class ContentBuilderFeignConfiguration {

    private static final String HEADER_NAME = "X-DTS-Exam-Source-Token";
    private static final String KEY_CONTEXT = "dts:content-builder:exam-source:v1";
    private static final String ISSUER = "dts-examination";
    private static final String SUBJECT = "exam-source-reader";
    private static final String AUDIENCE = "dts-content-builder";
    private static final String PURPOSE = "EXAM_SOURCE_READ";
    private static final String TOKEN_TYPE = "dts-service+jwt";
    private static final long TOKEN_LIFETIME_SECONDS = 45;
    private static final String METADATA_PATH = "/api/v1/content-builder/internal/questions/metadata";
    private static final String BATCH_PATH = "/api/v1/content-builder/internal/questions/batch";
    private static final String BANK_PATH = "/api/v1/content-builder/internal/questions/bank";

    @Bean
    public RequestInterceptor contentBuilderExamSourceAuthentication(JwtProperties properties) {
        SecretKey serviceKey = deriveServiceKey(properties.getSecret());
        return template -> {
            // Remove any previous credential, including one from a retry.
            template.removeHeader(HEADER_NAME);
            if (!isExamSourceRequest(template)) {
                return;
            }
            Instant now = Instant.now();
            String token = Jwts.builder()
                    .header().type(TOKEN_TYPE).and()
                    .issuer(ISSUER)
                    .subject(SUBJECT)
                    .claim("aud", List.of(AUDIENCE))
                    .claim("purpose", PURPOSE)
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(now.plusSeconds(TOKEN_LIFETIME_SECONDS)))
                    .id(UUID.randomUUID().toString())
                    .signWith(serviceKey, Jwts.SIG.HS256)
                    .compact();
            template.header(HEADER_NAME, token);
        };
    }

    @Bean
    public Logger.Level contentBuilderFeignLoggerLevel() {
        // Requests carry a credential; responses may contain private answer keys.
        return Logger.Level.NONE;
    }

    @Bean
    public ErrorDecoder contentBuilderSourceErrorDecoder() {
        return (methodKey, response) ->
                ContentBuilderClientFallback.failureForStatus(response.status(), null);
    }

    @Bean
    public Retryer contentBuilderSourceRetryer() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    public Request.Options contentBuilderSourceRequestOptions() {
        return new Request.Options(5, TimeUnit.SECONDS, 15, TimeUnit.SECONDS, false);
    }

    private boolean isExamSourceRequest(RequestTemplate template) {
        return ("GET".equals(template.method())
                && (METADATA_PATH.equals(template.path()) || BANK_PATH.equals(template.path())))
                || ("POST".equals(template.method()) && BATCH_PATH.equals(template.path()));
    }

    private SecretKey deriveServiceKey(String encodedSecret) {
        if (encodedSecret == null || encodedSecret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be empty");
        }
        byte[] rootKey = Decoders.BASE64.decode(encodedSecret);
        if (rootKey.length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(rootKey, "HmacSHA256"));
            byte[] derivedKey = mac.doFinal(KEY_CONTEXT.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(derivedKey, "HmacSHA256");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot initialize internal exam source authentication", exception);
        }
    }
}
