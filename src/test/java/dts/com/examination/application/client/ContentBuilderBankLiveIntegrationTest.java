package dts.com.examination.application.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dts.com.examination.application.exception.ExaminationException;
import dts.com.examination.application.service.ExamStructureValidator;
import dts.com.examination.application.service.selection.ExamMatrixPolicyCatalog;
import dts.com.examination.application.service.selection.ExamQuestionPool;
import dts.com.examination.application.service.selection.ExamQuestionSelection;
import dts.com.examination.application.service.selection.ExamQuestionSelectionService;
import dts.com.examination.application.service.selection.ExamQuestionSourceReader;
import dts.com.examination.application.service.selection.ExamSelectionMode;
import dts.com.examination.application.service.selection.ExamSourceQuestionMetadata;
import dts.com.examination.application.service.selection.FrozenExamQuestion;
import dts.com.examination.config.security.JwtProperties;
import dts.com.examination.domain.entity.Section;
import feign.RequestTemplate;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "DTS_SCREEN12_LIVE_BANK_TEST", matches = "true")
class ContentBuilderBankLiveIntegrationTest {
    private static final String BANK = "dts-2026-600-v1";
    private static final String BANK_PATH = "/api/v1/content-builder/internal/questions/bank";
    private static final String CREDENTIAL_HEADER = "X-DTS-Exam-Source-Token";
    private static final List<String> METADATA_FIELDS = List.of("id", "optionIds", "chapterIds",
            "isCritical", "status", "questionType", "mediaFileIds", "contentVersion");

    @ParameterizedTest
    @CsvSource({
            "B,screen12-source-B-real.json,719454d2e29fd2ee921d33e9619b365056a638def7f50608ec7d624f61f2fdd3",
            "A1,screen12-source-real.json,07ec32a6bea06b30f0493abd3bdd71e131d34428167b41b2107948176b5a9749"
    })
    @Timeout(120)
    void realBankQueryAndSignedReaderMatchActualImportedSourceAndMatrix(
            String license, String resource, String fixtureHash) throws Exception {
        // The service signing key is loaded by JwtProperties from the existing backend configuration.
        // Account login supplies a separate ADMIN bearer for the administrative HTTP check.
        String adminToken = System.getenv("DTS_SCREEN12_ADMIN_ACCESS_TOKEN");
        assertTrue(adminToken != null && !adminToken.isBlank(), "The local script must obtain an ADMIN access token");
        String sourceUrl = System.getenv("DTS_SCREEN12_SOURCE_URL");
        if (sourceUrl == null || sourceUrl.isBlank()) sourceUrl = "http://127.0.0.1:8082";
        URI source = URI.create(sourceUrl);
        assertTrue("http".equals(source.getScheme()) && Set.of("127.0.0.1", "localhost", "[::1]", "::1")
                .contains(source.getHost()) && source.getUserInfo() == null && source.getQuery() == null
                && source.getFragment() == null && (source.getPath().isEmpty() || "/".equals(source.getPath()))
                && source.getPort() > 0 && source.getPort() <= 65535, "Use the local Content Builder only");
        String baseUrl = sourceUrl.replaceAll("/+$", "");
        JsonNode expected = fixture(resource, fixtureHash);
        assertEquals(license, expected.path("licenseClass").asText());

        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class,
                        JacksonAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
                        FeignAutoConfiguration.class))
                .withUserConfiguration(LiveBankConfiguration.class)
                .withPropertyValues("CONTENT_BUILDER_URL=" + baseUrl,
                        "spring.cloud.openfeign.circuitbreaker.enabled=false",
                        "spring.cloud.openfeign.client.config.content-builder.loggerLevel=NONE",
                        "eureka.client.enabled=false")
                .run(context -> {
                    if (context.getStartupFailure() != null) {
                        fail("Live bank context failed: " + failureTypes(context.getStartupFailure()));
                    }
                    try {
                        verify(context.getBean(ContentBuilderClient.class), context.getBean(ObjectMapper.class),
                                context.getBean(JwtProperties.class), expected, license, baseUrl, adminToken);
                    } catch (ExaminationException error) {
                        fail("Live bank failed: HTTP=" + error.getStatus().value() + ", code=" + error.getCode());
                    } catch (Exception error) {
                        fail("Live bank failed: " + failureTypes(error));
                    }
                });
    }

    private void verify(ContentBuilderClient client, ObjectMapper mapper, JwtProperties properties,
            JsonNode expected, String license, String baseUrl, String adminToken) throws Exception {
        ExamQuestionSourceReader reader = new ExamQuestionSourceReader(client);
        ExamQuestionPool pool = reader.loadBank(BANK, license);
        Map<UUID, JsonNode> metadata = new LinkedHashMap<>();
        Map<UUID, JsonNode> details = new LinkedHashMap<>();
        Map<Integer, UUID> chapterIds = new LinkedHashMap<>();
        for (JsonNode chapter : expected.path("chapters")) {
            assertEquals(BANK, chapter.path("bankVersion").asText());
            chapterIds.put(chapter.path("sourceChapterId").asInt(), UUID.fromString(chapter.path("id").asText()));
            for (JsonNode question : chapter.path("items")) {
                JsonNode previous = metadata.putIfAbsent(UUID.fromString(question.path("id").asText()), question);
                if (previous != null) assertEquals(previous, question);
            }
        }
        for (JsonNode question : expected.path("questions")) {
            assertNull(details.put(UUID.fromString(question.path("id").asText()), question), "Fixture IDs must be unique");
        }
        assertEquals(6, pool.chapters().size());
        assertEquals(Set.of(1, 2, 3, 4, 5, 6), chapterIds.keySet());
        assertEquals(List.of(1, 2, 3, 4, 5, 6), pool.chapters().stream()
                .map(ExamQuestionPool.ChapterReference::sourceChapterId).toList());
        for (var chapter : pool.chapters()) {
            assertEquals(chapterIds.get(chapter.sourceChapterId()), chapter.id());
            assertEquals(BANK, chapter.bankVersion());
        }
        assertTrue(new ArrayList<>(metadata.keySet()).equals(pool.questions().stream()
                .map(ExamSourceQuestionMetadata::id).toList()), "Exact stable bank order");
        assertEquals(metadata.size(), pool.questions().size());
        for (var question : pool.questions()) {
            JsonNode actual = mapper.valueToTree(question);
            for (String field : METADATA_FIELDS) {
                assertTrue(metadata.get(question.id()).path(field).equals(actual.path(field)),
                        "Bank metadata differs: field=" + field);
            }
            assertEquals("PUBLISHED", question.status());
        }

        List<UUID> ids = pool.questions().stream().map(ExamSourceQuestionMetadata::id).toList();
        List<FrozenExamQuestion> frozen = reader.readVerifiedContent(pool, ids);
        assertEquals(ids, frozen.stream().map(FrozenExamQuestion::id).toList());
        assertTrue(details.keySet().equals(new HashSet<>(ids)), "Complete metadata/detail ID set");
        int critical = 0;
        int media = 0;
        for (var question : frozen) {
            JsonNode original = details.get(question.id());
            JsonNode actual = mapper.valueToTree(question);
            for (String field : List.of("id", "content", "type", "isCritical", "contentVersion", "mediaFileIds", "options", "explanations")) {
                assertTrue(original.path(field).equals(actual.path(field)), "Frozen source differs: field=" + field);
            }
            if (question.isCritical()) critical++;
            if (!question.mediaFileIds().isEmpty()) media++;
        }
        assertEquals(license.equals("B") ? 600 : 250, ids.size());
        assertEquals(license.equals("B") ? 60 : 20, critical);
        assertEquals(license.equals("B") ? 318 : 127, media);

        var catalog = new ExamMatrixPolicyCatalog(mapper);
        Instant examTime = Instant.now();
        var policy = catalog.require(license, examTime);
        List<Section> sections = new ArrayList<>();
        for (var topic : policy.topics()) {
            sections.add(new Section(topic.code(), topic.code(), topic.questionCount(), 1, sections.size(),
                    topic.sourceChapterIds().stream().map(chapterIds::get).toList(), 0));
        }
        sections.add(new Section("CRITICAL", "Critical", policy.criticalQuestionCount(), 1,
                sections.size(), List.of(), policy.criticalQuestionCount()));
        var selector = new ExamQuestionSelectionService(catalog, new ExamStructureValidator());
        selector.validateAutomaticReadiness(license, examTime, pool, sections);
        var auto = selector.select(license, examTime, pool, sections, ExamSelectionMode.AUTO_MATRIX, null);
        assertSelection(auto, sections, policy.totalQuestions());
        var manualIds = auto.questions().stream().map(ExamSourceQuestionMetadata::id).toList();
        var manual = selector.select(license, examTime, pool, sections, ExamSelectionMode.MANUAL, manualIds);
        assertSelection(manual, sections, policy.totalQuestions());
        assertEquals(manualIds, manual.questions().stream().map(ExamSourceQuestionMetadata::id).toList());
        assertTrue(manual.equals(selector.select(license, examTime, pool, sections,
                ExamSelectionMode.MANUAL, manualIds)), "Fixed manual selection must repeat unchanged");
        List<FrozenExamQuestion> selected = reader.readVerifiedContent(pool, manualIds);
        Map<UUID, FrozenExamQuestion> full = new LinkedHashMap<>();
        frozen.forEach(question -> full.put(question.id(), question));
        assertTrue(manualIds.stream().map(full::get).toList().equals(selected), "Selected private content must match full verified source");
        assertTrue(pool.equals(reader.loadBank(BANK, license)), "Repeated bank read must be stable");

        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        URI uri = URI.create(baseUrl + BANK_PATH + "?bankVersion=" + BANK + "&licenseClass=" + license);
        var denied = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(403, denied.statusCode(), "Anonymous bank read must be denied");
        RequestTemplate template = new RequestTemplate();
        template.method("GET"); template.uri(BANK_PATH);
        new ContentBuilderFeignConfiguration().contentBuilderExamSourceAuthentication(properties).apply(template);
        assertNotNull(template.headers().get(CREDENTIAL_HEADER));
        String serviceCredential = template.headers().get(CREDENTIAL_HEADER).iterator().next();
        var response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                .header(CREDENTIAL_HEADER, serviceCredential).header("Accept", "application/json").GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().allValues("Cache-Control").stream().anyMatch(value ->
                java.util.Arrays.stream(value.split(",")).anyMatch(token -> "no-store".equalsIgnoreCase(token.trim()))),
                "Bank response must be no-store");
        assertTrue(mapper.valueToTree(pool).equals(mapper.readTree(response.body())), "Raw signed GET and Feign must agree");

        var adminResponse = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + adminToken).header("Accept", "application/json").GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, adminResponse.statusCode(), "ADMIN bearer must read the same bank");
        assertTrue(mapper.valueToTree(pool).equals(mapper.readTree(adminResponse.body())),
                "ADMIN and signed service readers must receive the same published bank");
        assertTrue(adminResponse.headers().allValues("Cache-Control").stream().anyMatch(value ->
                java.util.Arrays.stream(value.split(",")).anyMatch(token -> "no-store".equalsIgnoreCase(token.trim()))));
        System.out.printf("AdminBankMatches=%s%n", license);

        System.out.printf("LiveBankLicense=%s Questions=%d Critical=%d Media=%d Selected=%d%n"
                        + "LiveBankConfirmed=%s%nNativeBankQueryConfirmed=%s%n"
                        + "BankMetadataMatches=%s%nPrivateContentMatches=%s%n"
                        + "AutomaticMatrixValid=%s%nManualMatrixValid=%s%n"
                        + "SelectedContentVerified=%s%nRepeatedBankStable=%s%n"
                        + "BankNoStore=%s%nAnonymousBankDenied=%s%n",
                license, ids.size(), critical, media, selected.size(), license, license, license,
                license, license, license, license, license, license, license);
    }

    private void assertSelection(ExamQuestionSelection selection, List<Section> sections, int total) {
        assertEquals(total, selection.questions().size());
        assertEquals(total, selection.questions().stream().map(ExamSourceQuestionMetadata::id).distinct().count());
        assertEquals(1, selection.questions().stream().filter(ExamSourceQuestionMetadata::isCritical).count());
        assertEquals(total, selection.sectionCodeByQuestionId().size());
        for (var section : sections) {
            var questions = selection.questions().stream().filter(question -> section.getCode()
                    .equals(selection.sectionCodeByQuestionId().get(question.id()))).toList();
            assertEquals(section.getQuestionCount().intValue(), questions.size());
            assertEquals(section.getCriticalQuestionCount().longValue(), questions.stream()
                    .filter(ExamSourceQuestionMetadata::isCritical).count());
            if (!section.getChapterIds().isEmpty()) assertTrue(questions.stream().allMatch(question ->
                    !java.util.Collections.disjoint(question.chapterIds(), section.getChapterIds())));
        }
    }

    private JsonNode fixture(String name, String hash) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input, "Missing actual source fixture");
            byte[] bytes = input.readAllBytes();
            assertEquals(hash, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            return new ObjectMapper().readTree(bytes);
        }
    }

    private static String failureTypes(Throwable error) {
        List<String> types = new ArrayList<>();
        Set<Throwable> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        while (error != null && visited.add(error)) {
            types.add(error.getClass().getSimpleName()); error = error.getCause();
        }
        return String.join(" -> ", types);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(clients = ContentBuilderClient.class)
    @EnableConfigurationProperties(JwtProperties.class)
    @Import(ContentBuilderClientFallback.class)
    static class LiveBankConfiguration {
    }
}
