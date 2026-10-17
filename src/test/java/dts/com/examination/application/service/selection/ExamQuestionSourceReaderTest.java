package dts.com.examination.application.service.selection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dts.com.examination.api.response.InternalQuestionDetailResponse;
import dts.com.examination.application.client.ContentBuilderClient;
import dts.com.examination.application.client.ContentBuilderClientFallback;
import dts.com.examination.application.client.ContentBuilderFeignConfiguration;
import dts.com.examination.application.exception.ExaminationException;
import dts.com.examination.config.security.JwtProperties;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExamQuestionSourceReaderTest {
    private static final String BANK = "dts-2026-600-v1";
    private final ObjectMapper mapper = new ObjectMapper();
    private ContentBuilderClient client;
    private ExamQuestionSourceReader reader;
    private ExamQuestionPool b;
    private ExamQuestionPool a1;
    private Map<UUID, InternalQuestionDetailResponse> details;

    @BeforeEach
    void setUp() throws Exception {
        client = mock(ContentBuilderClient.class);
        reader = new ExamQuestionSourceReader(client);
        JsonNode root = fixture("screen12-source-B-real.json",
                "719454d2e29fd2ee921d33e9619b365056a638def7f50608ec7d624f61f2fdd3");
        b = pool(root);
        a1 = pool(fixture("screen12-source-real.json",
                "07ec32a6bea06b30f0493abd3bdd71e131d34428167b41b2107948176b5a9749"));
        details = new LinkedHashMap<>();
        for (JsonNode item : root.get("questions")) {
            var detail = mapper.convertValue(item, InternalQuestionDetailResponse.class);
            details.put(detail.getId(), detail);
        }
        when(client.getQuestionBank(BANK, "B")).thenReturn(b);
        when(client.getQuestionBank(BANK, "A1")).thenReturn(a1);
        when(client.getQuestionsBatch(anyList(), eq("B"))).thenAnswer(call -> {
            List<UUID> ids = call.getArgument(0);
            return ids.stream().map(id -> details.get(id)).toList();
        });
    }

    @Test
    void licensedBBankReadReturnsActual600QuestionsAndUsesExplicitLicense() {
        var result = reader.loadBank(BANK, " b ");
        assertSame(b, result);
        assertEquals(600, result.questions().size());
        verify(client).getQuestionBank(BANK, "B");
        verifyNoMoreInteractions(client);
    }

    @Test
    void licensedA1BankReadReturnsActual250Questions() {
        var result = reader.loadBank(BANK, "A1");
        assertEquals(250, result.questions().size());
        assertEquals(20, result.questions().stream().filter(ExamSourceQuestionMetadata::isCritical).count());
        verify(client).getQuestionBank(BANK, "A1");
    }

    @Test
    void all600ActualQuestionsFreezeExactContentKeysExplanationsAndSourceOrderInTwoBatches() {
        List<UUID> ids = b.questions().stream().map(ExamSourceQuestionMetadata::id).toList();
        var frozen = reader.readVerifiedContent(b, ids);
        assertEquals(ids, frozen.stream().map(FrozenExamQuestion::id).toList());
        assertEquals(600, frozen.size());
        assertEquals(600, frozen.stream().map(FrozenExamQuestion::id).distinct().count());
        assertEquals(60, frozen.stream().filter(FrozenExamQuestion::isCritical).count());
        assertEquals(318, frozen.stream().filter(q -> !q.mediaFileIds().isEmpty()).count());
        for (var question : frozen) {
            var actual = details.get(question.id());
            assertEquals(actual.getContent(), question.content());
            assertEquals(actual.getContentVersion(), question.contentVersion());
            assertEquals(actual.getType(), question.type());
            assertEquals(actual.getIsCritical().booleanValue(), question.isCritical());
            assertEquals(mapper.valueToTree(actual.getOptions()), mapper.valueToTree(question.options()));
            assertEquals(actual.getExplanations(), question.explanations());
            assertEquals(actual.getMediaFileIds(), question.mediaFileIds().stream().map(UUID::toString).toList());
        }
        verify(client).getQuestionsBatch(ids.subList(0, 500), "B");
        verify(client).getQuestionsBatch(ids.subList(500, 600), "B");
        verifyNoMoreInteractions(client);
    }

    @Test
    void selectedActualQuestionsPreserveRequestedFixedOrder() {
        var ids = new ArrayList<>(b.questions().subList(0, 30).stream().map(ExamSourceQuestionMetadata::id).toList());
        java.util.Collections.reverse(ids);
        assertEquals(ids, reader.readVerifiedContent(b, ids).stream().map(FrozenExamQuestion::id).toList());
    }

    @Test
    void invalidLicenseOrBankCannotTriggerAnUnlicensedSourceCall() {
        for (String license : new String[]{null, "", "B2"}) {
            error("SOURCE_BAD_REQUEST", 400, () -> reader.loadBank(BANK, license));
        }
        for (String bank : new String[]{null, "", "../bank"}) {
            error("SOURCE_BAD_REQUEST", 400, () -> reader.loadBank(bank, "B"));
        }
        verifyNoInteractions(client);
    }

    @Test
    void missingOrRelabeledSourceResponseIsRejected() {
        when(client.getQuestionBank(BANK, "B")).thenReturn(null);
        error("SOURCE_INVALID_RESPONSE", 502, () -> reader.loadBank(BANK, "B"));
        when(client.getQuestionBank(BANK, "B")).thenReturn(a1);
        error("SOURCE_INVALID_RESPONSE", 502, () -> reader.loadBank(BANK, "B"));
        when(client.getQuestionBank(BANK, "B")).thenReturn(new ExamQuestionPool("other-bank", "B", b.chapters(), b.questions()));
        error("SOURCE_INVALID_RESPONSE", 502, () -> reader.loadBank(BANK, "B"));
    }

    @Test
    void completeUniqueSameBankChapterCatalogIsRequired() {
        var catalog = new ArrayList<>(b.chapters());
        catalog.set(1, catalog.get(0));
        when(client.getQuestionBank(BANK, "B")).thenReturn(new ExamQuestionPool(BANK, "B", catalog, b.questions()));
        error("SOURCE_INVALID_RESPONSE", 502, () -> reader.loadBank(BANK, "B"));
        when(client.getQuestionBank(BANK, "B")).thenReturn(new ExamQuestionPool(BANK, "B", b.chapters().subList(0, 5), b.questions()));
        error("SOURCE_INVALID_RESPONSE", 502, () -> reader.loadBank(BANK, "B"));
    }

    @Test
    void duplicateQuestionIdsInBankResponseAreRejected() {
        var duplicate = new ArrayList<>(b.questions());
        duplicate.add(duplicate.get(0));
        when(client.getQuestionBank(BANK, "B")).thenReturn(new ExamQuestionPool(BANK, "B", b.chapters(), duplicate));
        error("SOURCE_INVALID_RESPONSE", 502, () -> reader.loadBank(BANK, "B"));
    }

    @Test
    void anchorMustBeAnActualPublishedChapterInTheBank() {
        reader.requireBankAnchor(b, b.chapters().get(0).id());
        error("SOURCE_BAD_REQUEST", 400, () -> reader.requireBankAnchor(b, null));
        error("SOURCE_BAD_REQUEST", 400, () -> reader.requireBankAnchor(b, b.questions().get(0).id()));
    }

    @Test
    void contentReadRejectsAnActualBQuestionOutsideA1ScopeBeforeRemoteCall() {
        var allowed = new HashSet<>(a1.questions().stream().map(ExamSourceQuestionMetadata::id).toList());
        UUID outside = b.questions().stream().map(ExamSourceQuestionMetadata::id)
                .filter(id -> !allowed.contains(id)).findFirst().orElseThrow();
        error("SOURCE_BAD_REQUEST", 400, () -> reader.readVerifiedContent(a1, List.of(outside)));
        verifyNoInteractions(client);
    }

    @Test
    void duplicateNullAndAbsentQuestionIdListsAreRejectedBeforeRemoteCall() {
        UUID id = b.questions().get(0).id();
        error("SOURCE_BAD_REQUEST", 400, () -> reader.readVerifiedContent(b, null));
        error("SOURCE_BAD_REQUEST", 400, () -> reader.readVerifiedContent(b, List.of(id, id)));
        error("SOURCE_BAD_REQUEST", 400, () -> reader.readVerifiedContent(b, java.util.Arrays.asList(id, null)));
        assertTrue(reader.readVerifiedContent(b, List.of()).isEmpty());
        verifyNoInteractions(client);
    }

    @Test
    void incompleteOrNullDetailBatchCannotBecomeASuccessfulSnapshot() {
        var ids = List.of(b.questions().get(0).id());
        when(client.getQuestionsBatch(ids, "B")).thenReturn(List.of());
        error("SOURCE_INVALID_RESPONSE", 502, () -> reader.readVerifiedContent(b, ids));
        when(client.getQuestionsBatch(ids, "B")).thenReturn(null);
        error("SOURCE_INVALID_RESPONSE", 502, () -> reader.readVerifiedContent(b, ids));
    }

    @Test
    void wrongQuestionOrderInDetailBatchIsRejected() {
        var ids = b.questions().subList(0, 2).stream().map(ExamSourceQuestionMetadata::id).toList();
        when(client.getQuestionsBatch(ids, "B")).thenReturn(List.of(details.get(ids.get(1)), details.get(ids.get(0))));
        error("SOURCE_INVALID_RESPONSE", 502, () -> reader.readVerifiedContent(b, ids));
    }

    @Test
    void changedContentRevisionBetweenReadsFailsWithConflict() {
        var id = b.questions().get(0).id();
        details.get(id).setContentVersion("sha256:" + "0".repeat(64));
        error("SOURCE_CHANGED", 409, () -> reader.readVerifiedContent(b, List.of(id)));
    }

    @Test
    void changedCriticalFlagTypeAndStatusAreConflicts() {
        var id = b.questions().get(0).id();
        var detail = details.get(id);
        detail.setIsCritical(!detail.getIsCritical());
        error("SOURCE_CHANGED", 409, () -> reader.readVerifiedContent(b, List.of(id)));
        detail.setIsCritical(!detail.getIsCritical());
        detail.setType("MULTIPLE_CHOICE");
        error("SOURCE_CHANGED", 409, () -> reader.readVerifiedContent(b, List.of(id)));
        detail.setType("SINGLE_CHOICE");
        detail.setStatus("DRAFT");
        error("SOURCE_CHANGED", 409, () -> reader.readVerifiedContent(b, List.of(id)));
    }

    @Test
    void changedOptionOrderAndMediaReferencesAreConflicts() {
        var id = b.questions().get(0).id();
        var detail = details.get(id);
        var original = List.copyOf(detail.getOptions());
        var reversed = new ArrayList<>(original);
        java.util.Collections.reverse(reversed);
        detail.setOptions(reversed);
        error("SOURCE_CHANGED", 409, () -> reader.readVerifiedContent(b, List.of(id)));
        detail.setOptions(original);
        String realMedia = b.questions().stream().filter(q -> !q.mediaFileIds().isEmpty()).findFirst()
                .orElseThrow().mediaFileIds().get(0).toString();
        detail.setMediaFileIds(List.of(realMedia));
        error("SOURCE_CHANGED", 409, () -> reader.readVerifiedContent(b, List.of(id)));
    }

    @Test
    void missingAnswerKeyOrNoCorrectOptionIsRejected() {
        var id = b.questions().get(0).id();
        var detail = details.get(id);
        var option = detail.getOptions().get(0);
        Boolean original = option.getIsCorrect();
        option.setIsCorrect(null);
        error("SOURCE_INVALID_RESPONSE", 502, () -> reader.readVerifiedContent(b, List.of(id)));
        option.setIsCorrect(original);
        detail.getOptions().forEach(item -> item.setIsCorrect(false));
        error("SOURCE_INVALID_RESPONSE", 502, () -> reader.readVerifiedContent(b, List.of(id)));
    }

    @Test
    void blankContentAndFilesystemMediaPathAreRejected() {
        var id = b.questions().get(0).id();
        var detail = details.get(id);
        String content = detail.getContent();
        detail.setContent("");
        error("SOURCE_INVALID_RESPONSE", 502, () -> reader.readVerifiedContent(b, List.of(id)));
        detail.setContent(content);
        detail.setMediaFileIds(List.of("/home/internal/image.webp"));
        error("SOURCE_INVALID_RESPONSE", 502, () -> reader.readVerifiedContent(b, List.of(id)));
    }

    @Test
    void nullExplanationsFromThreeActualSourceRecordsRemainNull() {
        var ids = details.values().stream().filter(d -> d.getExplanations() == null)
                .map(InternalQuestionDetailResponse::getId).toList();
        assertEquals(3, ids.size());
        var frozen = reader.readVerifiedContent(b, ids);
        assertTrue(frozen.stream().allMatch(q -> q.explanations() == null));
    }

    @Test
    void frozenDataIsIndependentOfMutableFeignDtosAndExplanationMaps() {
        var id = b.questions().get(0).id();
        var detail = details.get(id);
        var frozen = reader.readVerifiedContent(b, List.of(id)).get(0);
        String original = frozen.options().get(0).content();
        detail.getOptions().get(0).setContent(detail.getOptions().get(1).getContent());
        assertEquals(original, frozen.options().get(0).content());
        var originalExplanation = frozen.explanations().get("text");
        detail.getExplanations().put("text", details.values().stream().filter(d -> d != detail
                && d.getExplanations() != null).findFirst().orElseThrow().getExplanations().get("text"));
        assertEquals(originalExplanation, frozen.explanations().get("text"));
        assertThrows(UnsupportedOperationException.class, () -> frozen.options().clear());
        assertThrows(UnsupportedOperationException.class, () -> frozen.explanations().clear());
    }

    @Test
    void bankFallbackKeepsCodedFailuresAndNeverReturnsAnEmptyBank() {
        ExaminationException original = new ExaminationException(HttpStatus.CONFLICT, "SOURCE_CHANGED", "Source changed");
        var fallback = new ContentBuilderClientFallback().create(original);
        assertSame(original, assertThrows(ExaminationException.class, () -> fallback.getQuestionBank(BANK, "B")));
        assertSame(original, assertThrows(ExaminationException.class,
                () -> fallback.getQuestionBankFallback(BANK, "B", original)));
        assertEquals("SOURCE_UNAVAILABLE", assertThrows(ExaminationException.class,
                () -> new ContentBuilderClientFallback().getQuestionBank(BANK, "B")).getCode());
    }

    @Test
    void scopedInterceptorSignsTheNewBankGetAndLegacyReadsButNoOtherRoute() throws Exception {
        JwtProperties properties = new JwtProperties();
        // Test-only key material from the approved fixture checksum; no user credential is used or logged.
        properties.setSecret(Base64.getEncoder().encodeToString(HexFormat.of().parseHex(
                "719454d2e29fd2ee921d33e9619b365056a638def7f50608ec7d624f61f2fdd3")));
        var interceptor = new ContentBuilderFeignConfiguration().contentBuilderExamSourceAuthentication(properties);
        for (var route : List.of(
                List.of("GET", "/api/v1/content-builder/internal/questions/bank"),
                List.of("GET", "/api/v1/content-builder/internal/questions/metadata"),
                List.of("POST", "/api/v1/content-builder/internal/questions/batch"))) {
            RequestTemplate template = new RequestTemplate();
            template.method(route.get(0)); template.uri(route.get(1));
            interceptor.apply(template);
            assertEquals(1, template.headers().get("X-DTS-Exam-Source-Token").size());
        }
        for (var route : List.of(
                List.of("POST", "/api/v1/content-builder/internal/questions/bank"),
                List.of("GET", "/api/v1/content-builder/internal/questions/bank/extra"),
                List.of("GET", "/api/v1/content-builder/chapters"))) {
            RequestTemplate template = new RequestTemplate();
            template.method(route.get(0)); template.uri(route.get(1));
            template.header("X-DTS-Exam-Source-Token", "stale-test-credential");
            interceptor.apply(template);
            assertFalse(template.headers().containsKey("X-DTS-Exam-Source-Token"));
        }
    }

    private JsonNode fixture(String name, String hash) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input);
            byte[] bytes = input.readAllBytes();
            assertEquals(hash, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            return mapper.readTree(bytes);
        }
    }

    private ExamQuestionPool pool(JsonNode root) {
        List<ExamQuestionPool.ChapterReference> chapters = new ArrayList<>();
        Map<UUID, ExamSourceQuestionMetadata> metadata = new LinkedHashMap<>();
        for (JsonNode chapter : root.get("chapters")) {
            chapters.add(new ExamQuestionPool.ChapterReference(UUID.fromString(chapter.get("id").textValue()),
                    chapter.get("sourceChapterId").intValue(), chapter.get("bankVersion").textValue()));
            for (JsonNode item : chapter.get("items")) {
                var question = mapper.convertValue(item, ExamSourceQuestionMetadata.class);
                metadata.putIfAbsent(question.id(), question);
            }
        }
        return new ExamQuestionPool(BANK, root.get("licenseClass").textValue(), chapters, new ArrayList<>(metadata.values()));
    }

    private void error(String code, int http, Runnable call) {
        ExaminationException error = assertThrows(ExaminationException.class, call::run);
        assertEquals(code, error.getCode()); assertEquals(http, error.getStatus().value());
    }

}
