package dts.com.examination.application.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dts.com.examination.api.response.InternalQuestionDetailResponse;
import dts.com.examination.application.exception.ExaminationException;
import dts.com.examination.config.security.JwtProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@EnabledIfEnvironmentVariable(named = "DTS_SCREEN12_LIVE_SOURCE_TEST", matches = "true")
class ContentBuilderClientLiveIntegrationTest {

    private static final String FIXTURE = "/screen12-source-B-real.json";
    private static final List<String> METADATA_FIELDS = List.of(
            "id", "optionIds", "chapterIds", "isCritical", "status",
            "questionType", "mediaFileIds", "contentVersion");
    private static final List<String> QUESTION_FIELDS = List.of(
            "id", "content", "type", "isCritical", "mediaFileIds", "options",
            "status", "contentVersion", "explanations");

    @Test
    @Timeout(120)
    void configuredFeignReadsTheVerifiedPublishedBankUsingServiceAuthentication() throws Exception {
        JsonNode expected;
        try (InputStream stream = getClass().getResourceAsStream(FIXTURE)) {
            assertNotNull(stream, "Missing actual source fixture");
            expected = new ObjectMapper().readTree(stream);
        }

        String sourceUrl = System.getenv("DTS_SCREEN12_SOURCE_URL");
        if (sourceUrl == null || sourceUrl.isBlank()) {
            sourceUrl = "http://127.0.0.1:8082";
        }
        URI source = URI.create(sourceUrl);
        assertTrue("http".equalsIgnoreCase(source.getScheme())
                        && Set.of("127.0.0.1", "localhost", "[::1]", "::1").contains(source.getHost())
                        && source.getUserInfo() == null,
                "This integration test must target the local Content Builder");

        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(
                        PropertyPlaceholderAutoConfiguration.class,
                        JacksonAutoConfiguration.class,
                        HttpMessageConvertersAutoConfiguration.class,
                        FeignAutoConfiguration.class))
                .withUserConfiguration(LiveFeignConfiguration.class)
                .withPropertyValues(
                        "CONTENT_BUILDER_URL=" + sourceUrl,
                        "spring.cloud.openfeign.circuitbreaker.enabled=false",
                        "spring.cloud.openfeign.client.config.content-builder.loggerLevel=NONE",
                        "eureka.client.enabled=false")
                .run(context -> {
                    if (context.getStartupFailure() != null) {
                        fail("Live Feign context failed: " + failureTypes(context.getStartupFailure()));
                    }
                    try {
                        ContentBuilderClient client = context.getBean(ContentBuilderClient.class);
                        ObjectMapper mapper = context.getBean(ObjectMapper.class);
                        verifySource(client, mapper, expected);
                    } catch (ExaminationException error) {
                        fail("Live source call failed: HTTP=" + error.getStatus().value()
                                + ", code=" + error.getCode());
                    } catch (RuntimeException error) {
                        fail("Live source call failed: " + failureTypes(error));
                    }
                });
    }

    private void verifySource(ContentBuilderClient client, ObjectMapper mapper, JsonNode expected) {
        String license = expected.path("licenseClass").asText();
        assertTrue(!license.isBlank(), "Fixture license class is required");
        assertTrue(expected.path("chapters").isArray()
                && !expected.path("chapters").isEmpty(), "Fixture chapters are required");
        assertTrue(expected.path("questions").isArray()
                && !expected.path("questions").isEmpty(), "Fixture questions are required");

        Map<UUID, JsonNode> metadataById = new LinkedHashMap<>();
        int chapterCount = 0;
        for (JsonNode chapter : expected.path("chapters")) {
            UUID chapterId = UUID.fromString(chapter.path("id").asText());
            List<Map<String, Object>> actual = client.getQuestionsMetadata(chapterId, "CHAPTER", license);
            assertNotNull(actual, "Metadata response must not be null");
            JsonNode expectedItems = chapter.path("items");
            assertEquals(expectedItems.size(), actual.size(), "Chapter count: " + chapterId);
            for (int index = 0; index < actual.size(); index++) {
                JsonNode item = mapper.valueToTree(actual.get(index));
                assertFields(expectedItems.get(index), item, METADATA_FIELDS, "metadata " + chapterId);
                assertTrue("PUBLISHED".equals(item.path("status").asText()), "Only published metadata");
                UUID id = UUID.fromString(item.path("id").asText());
                JsonNode previous = metadataById.putIfAbsent(id, item);
                if (previous != null) {
                    assertFields(previous, item, METADATA_FIELDS, "Repeated metadata " + id);
                }
            }
            System.out.printf("LiveChapter=%s Items=%d%n",
                    chapter.path("sourceChapterId").asText(), actual.size());
            chapterCount++;
        }

        List<UUID> orderedIds = new ArrayList<>(metadataById.keySet());
        assertEquals(expected.path("questions").size(), orderedIds.size(), "Unique source count");
        List<InternalQuestionDetailResponse> actualQuestions = new ArrayList<>();
        for (int start = 0; start < orderedIds.size(); start += 500) {
            List<UUID> requested = List.copyOf(orderedIds.subList(start, Math.min(start + 500, orderedIds.size())));
            List<InternalQuestionDetailResponse> batch = client.getQuestionsBatch(requested, license);
            assertNotNull(batch, "Batch response must not be null");
            assertEquals(requested.size(), batch.size(), "Batch must be complete");
            for (int index = 0; index < requested.size(); index++) {
                assertEquals(requested.get(index), batch.get(index).getId(), "Batch input order");
            }
            actualQuestions.addAll(batch);
        }

        Set<UUID> returnedIds = new HashSet<>();
        int critical = 0;
        int withMedia = 0;
        for (int index = 0; index < actualQuestions.size(); index++) {
            InternalQuestionDetailResponse question = actualQuestions.get(index);
            JsonNode detail = mapper.valueToTree(question);
            UUID id = question.getId();
            assertTrue(returnedIds.add(id), "No duplicated question: " + id);
            assertFields(expected.path("questions").get(index), detail, QUESTION_FIELDS, "question " + id);
            JsonNode metadata = metadataById.get(id);
            assertTrue(metadata.path("contentVersion").equals(detail.path("contentVersion")),
                    "Metadata/detail revision: " + id);
            assertTrue(metadata.path("isCritical").equals(detail.path("isCritical")),
                    "Metadata/detail critical flag: " + id);
            assertTrue(metadata.path("questionType").equals(detail.path("type")),
                    "Metadata/detail type: " + id);
            assertTrue(metadata.path("mediaFileIds").equals(detail.path("mediaFileIds")),
                    "Metadata/detail media: " + id);
            assertTrue("PUBLISHED".equals(detail.path("status").asText()), "Only published details");
            if (Boolean.TRUE.equals(question.getIsCritical())) {
                critical++;
            }
            if (question.getMediaFileIds() != null && !question.getMediaFileIds().isEmpty()) {
                withMedia++;
            }
        }
        assertEquals(metadataById.keySet(), returnedIds, "Complete metadata/detail ID set");

        JsonNode firstChapter = expected.path("chapters").get(0);
        List<Map<String, Object>> repeatedMetadata = client.getQuestionsMetadata(
                UUID.fromString(firstChapter.path("id").asText()), "CHAPTER", license);
        assertEquals(firstChapter.path("items").size(), repeatedMetadata.size(), "Stable metadata count");
        for (int index = 0; index < repeatedMetadata.size(); index++) {
            assertFields(firstChapter.path("items").get(index), mapper.valueToTree(repeatedMetadata.get(index)),
                    METADATA_FIELDS, "Repeated first chapter");
        }
        List<InternalQuestionDetailResponse> repeated = client.getQuestionsBatch(List.of(orderedIds.get(0)), license);
        assertNotNull(repeated, "Repeated batch must not be null");
        assertEquals(1, repeated.size(), "Repeated batch count");
        assertFields(expected.path("questions").get(0), mapper.valueToTree(repeated.get(0)),
                QUESTION_FIELDS, "Repeated first question");

        System.out.printf("LiveSourceConfirmed=true%nLicenseClass=%s%nPublishedChapters=%d%n"
                        + "ExpectedQuestions=%d%nActualQuestions=%d%nUniqueQuestions=%d%n"
                        + "CriticalQuestions=%d%nQuestionsWithMedia=%d%n"
                        + "ContentAndOptionsMatch=true%nMetadataAndVersionMatch=true%n"
                        + "InputOrderPreserved=true%nRepeatedReadStable=true%n",
                license, chapterCount, expected.path("questions").size(), actualQuestions.size(),
                returnedIds.size(), critical, withMedia);
    }

    private void assertFields(JsonNode expected, JsonNode actual, List<String> fields, String label) {
        assertNotNull(expected, "Missing fixture item: " + label);
        assertNotNull(actual, "Missing source item: " + label);
        for (String field : fields) {
            assertTrue(!expected.path(field).isMissingNode(), "Missing fixture field: " + field);
            assertTrue(expected.path(field).equals(actual.path(field)), label + " field=" + field);
        }
    }

    private String failureTypes(Throwable error) {
        List<String> types = new ArrayList<>();
        Set<Throwable> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        while (error != null && visited.add(error)) {
            types.add(error.getClass().getSimpleName());
            error = error.getCause();
        }
        return String.join(" -> ", types);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(clients = ContentBuilderClient.class)
    @EnableConfigurationProperties(JwtProperties.class)
    @Import(ContentBuilderClientFallback.class)
    static class LiveFeignConfiguration {
    }
}
