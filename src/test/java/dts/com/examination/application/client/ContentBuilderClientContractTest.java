package dts.com.examination.application.client;

import dts.com.examination.application.exception.ExaminationException;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import feign.codec.DecodeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentBuilderClientContractTest {

    // Identifiers from the attached, verified published B source.
    private static final UUID CHAPTER_ID = UUID.fromString("19cd4bb3-7003-418f-bc98-2594a362e497");
    private static final UUID QUESTION_ID = UUID.fromString("ef1d0f5a-2ff5-4d02-8fda-6dacfdaaa7f2");

    @ParameterizedTest
    @CsvSource({
            "400,400,SOURCE_BAD_REQUEST",
            "404,404,SOURCE_NOT_FOUND",
            "409,409,SOURCE_CONFLICT",
            "401,502,SOURCE_ACCESS_DENIED",
            "403,502,SOURCE_ACCESS_DENIED",
            "408,503,SOURCE_UNAVAILABLE",
            "429,503,SOURCE_UNAVAILABLE",
            "500,503,SOURCE_UNAVAILABLE",
            "503,503,SOURCE_UNAVAILABLE",
            "405,502,SOURCE_INVALID_RESPONSE"
    })
    void upstreamHttpErrorsKeepTheirMeaningThroughDecoderAndFallback(
            int sourceHttp, int expectedHttp, String expectedCode) {
        Response response = response(sourceHttp);
        Exception decoded = new ContentBuilderFeignConfiguration()
                .contentBuilderSourceErrorDecoder().decode("source", response);
        ContentBuilderClient client = new ContentBuilderClientFallback()
                .create(new CompletionException(decoded));

        ExaminationException error = assertThrows(ExaminationException.class,
                () -> client.getQuestionsMetadata(CHAPTER_ID, "CHAPTER", "B"));
        assertEquals(expectedHttp, error.getStatus().value());
        assertEquals(expectedCode, error.getCode());
        assertSame(decoded, error);
        assertFalse(error.getMessage().contains("private-upstream-diagnostic"));
    }

    @Test
    void connectionFailureNeverReturnsAnEmptySource() {
        ContentBuilderClient client = new ContentBuilderClientFallback()
                .create(new IOException("private-network-diagnostic"));
        ExaminationException metadata = assertThrows(ExaminationException.class,
                () -> client.getQuestionsMetadata(CHAPTER_ID, "CHAPTER", "B"));
        ExaminationException batch = assertThrows(ExaminationException.class,
                () -> client.getQuestionsBatch(List.of(QUESTION_ID), "B"));
        assertEquals(503, metadata.getStatus().value());
        assertEquals("SOURCE_UNAVAILABLE", metadata.getCode());
        assertEquals(503, batch.getStatus().value());
        assertEquals("SOURCE_UNAVAILABLE", batch.getCode());
        assertFalse(metadata.getMessage().contains("private-network-diagnostic"));
    }

    @Test
    void anExistingCodedSourceFailureSurvivesNestedWrappers() {
        ExaminationException original = new ExaminationException(
                HttpStatus.CONFLICT, "SOURCE_CHANGED", "Source changed during snapshot creation");
        ContentBuilderClient client = new ContentBuilderClientFallback()
                .create(new CompletionException(new IllegalStateException(original)));
        assertSame(original, assertThrows(ExaminationException.class,
                () -> client.getQuestionsBatch(List.of(QUESTION_ID), "B")));
    }

    @Test
    void malformedSuccessfulResponseIs502InsteadOfAnEmptyListOr409() {
        ContentBuilderClient client = new ContentBuilderClientFallback().create(
                new DecodeException(200, "private-upstream-diagnostic", request()));
        ExaminationException error = assertThrows(ExaminationException.class,
                () -> client.getQuestionsBatch(List.of(QUESTION_ID), "B"));
        assertEquals(502, error.getStatus().value());
        assertEquals("SOURCE_INVALID_RESPONSE", error.getCode());
        assertFalse(error.getMessage().contains("private-upstream-diagnostic"));
    }

    @Test
    void rawFeignHttpFailureIsMappedWhenNoCustomDecoderWasUsed() {
        ContentBuilderClient client = new ContentBuilderClientFallback().create(
                new CompletionException(FeignException.errorStatus("source", response(404))));
        ExaminationException error = assertThrows(ExaminationException.class,
                () -> client.getQuestionsMetadata(CHAPTER_ID, "CHAPTER", "B"));
        assertEquals(404, error.getStatus().value());
        assertEquals("SOURCE_NOT_FOUND", error.getCode());
    }

    @Test
    void legacyOverloadsAndAnnotationFallbackMethodsKeepCodedFailures() {
        ContentBuilderClient client = new ContentBuilderClientFallback();
        assertEquals("SOURCE_UNAVAILABLE", assertThrows(ExaminationException.class,
                () -> client.getQuestionsMetadata(CHAPTER_ID, "CHAPTER")).getCode());
        assertEquals("SOURCE_UNAVAILABLE", assertThrows(ExaminationException.class,
                () -> client.getQuestionsBatch(List.of(QUESTION_ID))).getCode());

        ExaminationException original = new ExaminationException(
                HttpStatus.NOT_FOUND, "SOURCE_NOT_FOUND", "Source not found");
        assertSame(original, assertThrows(ExaminationException.class,
                () -> client.getQuestionsMetadataFallback(CHAPTER_ID, "CHAPTER", original)));
        assertSame(original, assertThrows(ExaminationException.class,
                () -> client.getQuestionsBatchFallback(List.of(QUESTION_ID), original)));
    }

    private Request request() {
        return Request.create(Request.HttpMethod.GET,
                "http://content-builder/api/v1/content-builder/internal/questions/metadata",
                Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
    }

    private Response response(int status) {
        return Response.builder().status(status).reason("Source response")
                .request(request()).headers(Map.of())
                .body("private-upstream-diagnostic", StandardCharsets.UTF_8).build();
    }
}
