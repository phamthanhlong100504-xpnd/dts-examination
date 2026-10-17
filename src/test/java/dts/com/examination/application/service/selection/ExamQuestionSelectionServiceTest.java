package dts.com.examination.application.service.selection;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dts.com.examination.application.exception.ExaminationException;
import dts.com.examination.application.service.ExamStructureValidator;
import dts.com.examination.domain.entity.Section;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ExamQuestionSelectionServiceTest {
    private static final Instant EXAM_TIME = Instant.parse("2026-10-13T08:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper();
    private ExamMatrixPolicyCatalog catalog;
    private ExamQuestionSelectionService service;
    private ExamQuestionPool b;
    private ExamQuestionPool a1;
    private List<Section> bSections;
    private List<Section> a1Sections;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new ExamMatrixPolicyCatalog(mapper);
        service = new ExamQuestionSelectionService(catalog, new ExamStructureValidator(), new Random(20261013));
        b = fixture("screen12-source-B-real.json",
                "719454d2e29fd2ee921d33e9619b365056a638def7f50608ec7d624f61f2fdd3");
        a1 = fixture("screen12-source-real.json",
                "07ec32a6bea06b30f0493abd3bdd71e131d34428167b41b2107948176b5a9749");
        bSections = structure(b, new int[][]{{1}, {2}, {3}, {4}, {5}, {6}}, new int[]{8, 1, 1, 1, 9, 9});
        a1Sections = structure(a1, new int[][]{{1}, {2}, {3, 4}, {5}, {6}}, new int[]{8, 1, 1, 8, 6});
    }

    static Stream<Arguments> licenseProfiles() {
        return Stream.of(
                Arguments.of("A1", 25, 19, 21), Arguments.of("A", 25, 19, 23), Arguments.of("B1", 25, 19, 23),
                Arguments.of("B", 30, 20, 27), Arguments.of("C1", 35, 22, 32), Arguments.of("C", 40, 24, 36),
                Arguments.of("D1", 45, 26, 41), Arguments.of("D2", 45, 26, 41), Arguments.of("D", 45, 26, 41),
                Arguments.of("BE", 45, 26, 41), Arguments.of("C1E", 45, 26, 41), Arguments.of("CE", 45, 26, 41),
                Arguments.of("D1E", 45, 26, 41), Arguments.of("D2E", 45, 26, 41), Arguments.of("DE", 45, 26, 41));
    }

    @ParameterizedTest
    @MethodSource("licenseProfiles")
    void policiesMatchSuppliedCurrentRegulations(String license, int count, int minutes, int passing) {
        ExamMatrixPolicy policy = catalog.require(license, EXAM_TIME);
        assertEquals(count, policy.totalQuestions());
        assertEquals(minutes, policy.durationMinutes());
        assertEquals(passing, policy.passingCorrectAnswers());
        assertEquals(1, policy.criticalQuestionCount());
        assertEquals(count - 1, policy.topics().stream().mapToInt(ExamMatrixPolicy.TopicQuota::questionCount).sum());
    }

    @Test
    void policyUsesVietnamDateAtTransitionAndDoesNotSilentlyReuseExpiredMatrix() {
        assertEquals("B", catalog.require(" b ", Instant.parse("2027-02-28T16:59:59Z")).licenseClass());
        error("MATRIX_POLICY_UNAVAILABLE", HttpStatus.CONFLICT,
                () -> catalog.require("B", Instant.parse("2027-02-28T17:00:00Z")));
        error("MATRIX_POLICY_UNAVAILABLE", HttpStatus.CONFLICT,
                () -> catalog.require("B", Instant.parse("2025-05-31T16:59:59Z")));
        assertNotNull(catalog.require("B", Instant.parse("2025-05-31T17:00:00Z")));
    }

    @Test
    void invalidLicenseOrMissingExamTimeIsRejected() {
        for (String license : new String[]{null, "", "B2"}) {
            error("MATRIX_INVALID", HttpStatus.BAD_REQUEST, () -> catalog.require(license, EXAM_TIME));
        }
        error("MATRIX_INVALID", HttpStatus.BAD_REQUEST, () -> catalog.require("B", null));
    }

    @Test
    void hundredAutomaticBPapersMatchExactQuotasAndRealSourceRevisionsWithoutDuplicates() {
        Set<Set<UUID>> papers = new HashSet<>();
        List<ExamSourceQuestionMetadata> before = List.copyOf(b.questions());
        for (int i = 0; i < 100; i++) {
            ExamQuestionSelection selection = automatic(b, bSections);
            assertPaper(selection, b, bSections, 30);
            papers.add(Set.copyOf(ids(selection)));
        }
        assertTrue(papers.size() > 1);
        assertEquals(before, b.questions());
    }

    @Test
    void automaticA1UsesActual250LicensedQuestionsAndEmptyConstructionChapter() {
        assertEquals(250, a1.questions().size());
        assertEquals(0, a1.questions().stream().filter(q -> q.chapterIds().contains(chapter(a1, 4))).count());
        assertPaper(automatic(a1, a1Sections), a1, a1Sections, 25);
    }

    @Test
    void readinessChecksAvailabilityWithoutConsumingNewAttemptRandomness() {
        service.validateAutomaticReadiness("B", EXAM_TIME, b, bSections);
        ExamQuestionSelection afterReadiness = automatic(b, bSections);
        ExamQuestionSelectionService other = new ExamQuestionSelectionService(catalog,
                new ExamStructureValidator(), new Random(20261013));
        assertEquals(afterReadiness, other.select("B", EXAM_TIME, b, bSections, ExamSelectionMode.AUTO_MATRIX, null));
    }

    @Test
    void validManualPaperPreservesFixedIdsOrderSectionsAndRevisionOnRepeatedReads() {
        List<UUID> fixed = new ArrayList<>(ids(automatic(b, bSections)));
        java.util.Collections.reverse(fixed);
        ExamQuestionSelection first = manual(b, bSections, fixed);
        assertEquals(fixed, ids(first));
        assertEquals(first, manual(b, bSections, fixed));
        assertPaper(first, b, bSections, 30);
    }

    @Test
    void validManualA1HasExact25QuestionsAndOneCritical() {
        List<UUID> fixed = ids(automatic(a1, a1Sections));
        assertPaper(manual(a1, a1Sections, fixed), a1, a1Sections, 25);
    }

    @Test
    void manualRejectsMissingTooFewAndTooManyIds() {
        List<UUID> fixed = ids(automatic(b, bSections));
        List<UUID> tooMany = new ArrayList<>(fixed);
        tooMany.add(b.questions().get(0).id());
        error("MANUAL_QUESTION_COUNT", HttpStatus.BAD_REQUEST, () -> manual(b, bSections, null));
        error("MANUAL_QUESTION_COUNT", HttpStatus.BAD_REQUEST,
                () -> manual(b, bSections, fixed.subList(0, 29)));
        error("MANUAL_QUESTION_COUNT", HttpStatus.BAD_REQUEST, () -> manual(b, bSections, tooMany));
    }

    @Test
    void manualRejectsDuplicateAndNullIds() {
        List<UUID> fixed = new ArrayList<>(ids(automatic(b, bSections)));
        fixed.set(1, fixed.get(0));
        error("MANUAL_DUPLICATE_QUESTION", HttpStatus.BAD_REQUEST, () -> manual(b, bSections, fixed));
        fixed.set(1, null);
        error("MANUAL_DUPLICATE_QUESTION", HttpStatus.BAD_REQUEST, () -> manual(b, bSections, fixed));
    }

    @Test
    void manualRejectsActualBQuestionOutsideA1Scope() {
        List<UUID> fixed = new ArrayList<>(ids(automatic(a1, a1Sections)));
        Set<UUID> allowed = new HashSet<>(a1.questions().stream().map(ExamSourceQuestionMetadata::id).toList());
        UUID outside = b.questions().stream().map(ExamSourceQuestionMetadata::id)
                .filter(id -> !allowed.contains(id)).findFirst().orElseThrow();
        fixed.set(0, outside);
        error("MANUAL_QUESTION_NOT_APPLICABLE", HttpStatus.BAD_REQUEST, () -> manual(a1, a1Sections, fixed));
    }

    @Test
    void manualRejectsWrongTopicEvenWhenTotalAndCriticalCountAreCorrect() {
        List<UUID> fixed = new ArrayList<>(ids(automatic(b, bSections)));
        UUID old = questionIn(b, fixed, chapter(b, 5), false);
        UUID replacement = unused(b, fixed, chapter(b, 6), false);
        fixed.set(fixed.indexOf(old), replacement);
        error("MANUAL_MATRIX_MISMATCH", HttpStatus.BAD_REQUEST, () -> manual(b, bSections, fixed));
    }

    @Test
    void manualRejectsNoCriticalAndTwoCriticalQuestions() {
        List<UUID> fixed = new ArrayList<>(ids(automatic(b, bSections)));
        UUID critical = questionIn(b, fixed, null, true);
        UUID ordinary = unused(b, fixed, chapter(b, 1), false);
        fixed.set(fixed.indexOf(critical), ordinary);
        List<UUID> noCritical = fixed;
        error("MANUAL_MATRIX_MISMATCH", HttpStatus.BAD_REQUEST, () -> manual(b, bSections, noCritical));
        fixed = new ArrayList<>(ids(automatic(b, bSections)));
        UUID normal = questionIn(b, fixed, chapter(b, 1), false);
        UUID anotherCritical = unused(b, fixed, null, true);
        fixed.set(fixed.indexOf(normal), anotherCritical);
        List<UUID> two = fixed;
        error("MANUAL_MATRIX_MISMATCH", HttpStatus.BAD_REQUEST, () -> manual(b, bSections, two));
    }

    @Test
    void automaticRejectsManualIdsAndMissingMode() {
        error("MATRIX_INVALID", HttpStatus.BAD_REQUEST,
                () -> service.select("B", EXAM_TIME, b, bSections, ExamSelectionMode.AUTO_MATRIX,
                        List.of(b.questions().get(0).id())));
        error("SELECTION_MODE_INVALID", HttpStatus.BAD_REQUEST,
                () -> service.select("B", EXAM_TIME, b, bSections, null, null));
    }

    @Test
    void sourcePoolCannotBeRelabeledAcrossBanksOrLicenses() {
        error("SOURCE_INVALID_RESPONSE", HttpStatus.BAD_GATEWAY, () -> service.select("A1", EXAM_TIME,
                b, a1Sections, ExamSelectionMode.AUTO_MATRIX, null));
        ExamQuestionPool otherBank = new ExamQuestionPool("other-bank", "B", b.chapters(), b.questions());
        error("SOURCE_INVALID_RESPONSE", HttpStatus.BAD_GATEWAY, () -> automatic(otherBank, bSections));
    }

    @Test
    void identicalRepeatedSourceIdsAreDeduplicatedBeforeAllocation() {
        List<ExamSourceQuestionMetadata> repeated = new ArrayList<>(b.questions());
        repeated.addAll(b.questions());
        assertPaper(automatic(withQuestions(b, repeated), bSections), b, bSections, 30);
    }

    @Test
    void repeatedIdWithConflictingRevisionIsRejected() {
        var question = b.questions().get(0);
        var other = new ExamSourceQuestionMetadata(question.id(), question.optionIds(), question.chapterIds(),
                question.isCritical(), question.status(), question.questionType(), question.mediaFileIds(),
                "sha256:" + "0".repeat(64));
        List<ExamSourceQuestionMetadata> repeated = new ArrayList<>(b.questions());
        repeated.add(other);
        error("SOURCE_INVALID_RESPONSE", HttpStatus.BAD_GATEWAY,
                () -> automatic(withQuestions(b, repeated), bSections));
    }

    @Test
    void insufficientTopicPoolAndEmptyPoolFailBeforeAnyAttemptIsCreated() {
        List<ExamSourceQuestionMetadata> withoutSigns = b.questions().stream()
                .filter(q -> !q.chapterIds().contains(chapter(b, 5))).toList();
        error("MATRIX_SOURCE_INSUFFICIENT", HttpStatus.CONFLICT,
                () -> automatic(withQuestions(b, withoutSigns), bSections));
        error("MATRIX_SOURCE_INSUFFICIENT", HttpStatus.CONFLICT,
                () -> automatic(withQuestions(b, List.of()), bSections));
    }

    @Test
    void insufficientCriticalPoolIsRejected() {
        var withoutCritical = b.questions().stream().filter(q -> !q.isCritical()).toList();
        error("MATRIX_SOURCE_INSUFFICIENT", HttpStatus.CONFLICT,
                () -> automatic(withQuestions(b, withoutCritical), bSections));
    }

    @Test
    void arbitraryTotalsAndTopicRatiosCannotOverrideTrustedPolicy() {
        List<Section> wrong = copy(bSections);
        wrong.get(0).setQuestionCount(9);
        wrong.get(4).setQuestionCount(8);
        error("MATRIX_INVALID", HttpStatus.BAD_REQUEST, () -> automatic(b, wrong));
        List<Section> wrongTotal = copy(bSections);
        wrongTotal.get(0).setQuestionCount(9);
        error("MATRIX_INVALID", HttpStatus.BAD_REQUEST, () -> automatic(b, wrongTotal));
    }

    @Test
    void officialSectionsRequireExplicitCriticalQuotaAndSourceChapters() {
        List<Section> noQuota = copy(bSections);
        noQuota.get(0).setCriticalQuestionCount(null);
        error("MATRIX_INVALID", HttpStatus.BAD_REQUEST, () -> automatic(b, noQuota));
        List<Section> noChapter = copy(bSections);
        noChapter.get(0).setChapterIds(List.of());
        error("MATRIX_INVALID", HttpStatus.BAD_REQUEST, () -> automatic(b, noChapter));
    }

    @Test
    void criticalSlotCannotBeMixedWithOrdinaryQuotaOrRestrictedToAChapter() {
        List<Section> mixed = copy(bSections);
        mixed.get(0).setCriticalQuestionCount(1);
        error("MATRIX_INVALID", HttpStatus.BAD_REQUEST, () -> automatic(b, mixed));
        List<Section> restricted = copy(bSections);
        restricted.get(6).setChapterIds(List.of(chapter(b, 1)));
        error("MATRIX_INVALID", HttpStatus.BAD_REQUEST, () -> automatic(b, restricted));
    }

    @Test
    void ordinaryQuotaCanBeSplitAcrossDistinctSectionsWithoutDuplicateQuestions() {
        List<Section> split = copy(bSections);
        split.get(0).setQuestionCount(3);
        split.add(new Section("RULES_EXTRA", "Rules extra", 5, 2, 10, List.of(chapter(b, 1)), 0));
        assertPaper(automatic(b, split), b, split, 30);
    }

    @Test
    void sourceCatalogMustBeCompleteUniqueAndFromOneBank() {
        var incomplete = new ExamQuestionPool(b.bankVersion(), "B", b.chapters().subList(0, 5), b.questions());
        error("SOURCE_INVALID_RESPONSE", HttpStatus.BAD_GATEWAY, () -> automatic(incomplete, bSections));
        var duplicate = new ArrayList<>(b.chapters());
        duplicate.set(1, duplicate.get(0));
        error("SOURCE_INVALID_RESPONSE", HttpStatus.BAD_GATEWAY,
                () -> automatic(new ExamQuestionPool(b.bankVersion(), "B", duplicate, b.questions()), bSections));
        var wrong = new ArrayList<>(b.chapters());
        var first = wrong.get(0);
        wrong.set(0, new ExamQuestionPool.ChapterReference(first.id(), first.sourceChapterId(), "other-bank"));
        error("SOURCE_INVALID_RESPONSE", HttpStatus.BAD_GATEWAY,
                () -> automatic(new ExamQuestionPool(b.bankVersion(), "B", wrong, b.questions()), bSections));
    }

    @Test
    void sourceDraftNullTypeMissingOptionsAndInvalidRevisionAreRejected() {
        var original = b.questions().get(0);
        for (var invalid : List.of(
                replace(original, "DRAFT", original.questionType(), original.optionIds(), original.contentVersion()),
                replace(original, original.status(), null, original.optionIds(), original.contentVersion()),
                replace(original, original.status(), original.questionType(), List.of(), original.contentVersion()),
                replace(original, original.status(), original.questionType(), original.optionIds(), "1"))) {
            var pool = new ArrayList<>(b.questions());
            pool.set(0, invalid);
            error("SOURCE_INVALID_RESPONSE", HttpStatus.BAD_GATEWAY, () -> automatic(withQuestions(b, pool), bSections));
        }
    }

    @Test
    void unsupportedQuestionTypeFailsRatherThanInventingGradingBehavior() {
        var original = b.questions().get(0);
        var pool = new ArrayList<>(b.questions());
        pool.set(0, replace(original, original.status(), "FILL_BLANK", original.optionIds(), original.contentVersion()));
        error("SOURCE_QUESTION_TYPE_UNSUPPORTED", HttpStatus.CONFLICT,
                () -> automatic(withQuestions(b, pool), bSections));
    }

    @Test
    void metadataMappingRejectsMissingCriticalFlagInsteadOfDefaultingFalse() {
        Map<String, Object> value = mapper.convertValue(b.questions().get(0),
                new TypeReference<Map<String, Object>>() {});
        value.remove("isCritical");
        error("SOURCE_INVALID_RESPONSE", HttpStatus.BAD_GATEWAY,
                () -> ExamSourceQuestionMetadata.fromMap(mapper, value));
        value.put("isCritical", "false");
        error("SOURCE_INVALID_RESPONSE", HttpStatus.BAD_GATEWAY,
                () -> ExamSourceQuestionMetadata.fromMap(mapper, value));
    }

    @Test
    void selectedMetadataAndPlanAreImmutableAndContainNoAnswerKeys() {
        var selection = automatic(b, bSections);
        assertThrows(UnsupportedOperationException.class, () -> selection.questions().clear());
        assertThrows(UnsupportedOperationException.class, () -> selection.sectionCodeByQuestionId().clear());
        assertThrows(UnsupportedOperationException.class, () -> selection.questions().get(0).optionIds().clear());
        String json = mapper.valueToTree(selection).toString();
        assertFalse(json.contains("isCorrect"));
        assertFalse(json.contains("explanations"));
    }

    private ExamQuestionPool fixture(String resource, String hash) throws Exception {
        byte[] bytes;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "Missing actual exported source fixture");
            bytes = input.readAllBytes();
        }
        assertEquals(hash, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        JsonNode root = mapper.readTree(bytes);
        List<ExamQuestionPool.ChapterReference> chapters = new ArrayList<>();
        Map<UUID, ExamSourceQuestionMetadata> questions = new java.util.LinkedHashMap<>();
        for (JsonNode chapter : root.get("chapters")) {
            chapters.add(new ExamQuestionPool.ChapterReference(UUID.fromString(chapter.get("id").textValue()),
                    chapter.get("sourceChapterId").intValue(), chapter.get("bankVersion").textValue()));
            for (JsonNode item : chapter.get("items")) {
                var question = ExamSourceQuestionMetadata.fromMap(mapper,
                        mapper.convertValue(item, new TypeReference<Map<String, Object>>() {}));
                var previous = questions.putIfAbsent(question.id(), question);
                if (previous != null) assertEquals(previous, question);
            }
        }
        return new ExamQuestionPool("dts-2026-600-v1", root.get("licenseClass").textValue(),
                chapters, new ArrayList<>(questions.values()));
    }

    private List<Section> structure(ExamQuestionPool pool, int[][] groups, int[] counts) {
        List<Section> result = new ArrayList<>();
        for (int i = 0; i < groups.length; i++) {
            List<UUID> chapters = new ArrayList<>();
            for (int group : groups[i]) chapters.add(chapter(pool, group));
            result.add(new Section("TOPIC_" + i, "Topic " + i, counts[i], 1, i, chapters, 0));
        }
        result.add(new Section("CRITICAL", "Critical", 1, 1, groups.length, List.of(), 1));
        return result;
    }

    private List<Section> copy(List<Section> sections) {
        return new ArrayList<>(sections.stream().map(s -> new Section(s.getCode(), s.getTitle(),
                s.getQuestionCount(), s.getScore(), s.getOrder(), s.getChapterIds(), s.getCriticalQuestionCount())).toList());
    }

    private UUID chapter(ExamQuestionPool pool, int number) {
        return pool.chapters().stream().filter(c -> c.sourceChapterId() == number).findFirst().orElseThrow().id();
    }

    private ExamQuestionPool withQuestions(ExamQuestionPool pool, List<ExamSourceQuestionMetadata> questions) {
        return new ExamQuestionPool(pool.bankVersion(), pool.licenseClass(), pool.chapters(), questions);
    }

    private ExamSourceQuestionMetadata replace(ExamSourceQuestionMetadata q, String status, String type,
            List<UUID> options, String revision) {
        return new ExamSourceQuestionMetadata(q.id(), options, q.chapterIds(), q.isCritical(), status,
                type, q.mediaFileIds(), revision);
    }

    private ExamQuestionSelection automatic(ExamQuestionPool pool, List<Section> sections) {
        return service.select(pool.licenseClass(), EXAM_TIME, pool, sections, ExamSelectionMode.AUTO_MATRIX, null);
    }

    private ExamQuestionSelection manual(ExamQuestionPool pool, List<Section> sections, List<UUID> ids) {
        return service.select(pool.licenseClass(), EXAM_TIME, pool, sections, ExamSelectionMode.MANUAL, ids);
    }

    private List<UUID> ids(ExamQuestionSelection selection) {
        return selection.questions().stream().map(ExamSourceQuestionMetadata::id).toList();
    }

    private UUID questionIn(ExamQuestionPool pool, List<UUID> fixed, UUID chapter, boolean critical) {
        return pool.questions().stream().filter(q -> fixed.contains(q.id()) && q.isCritical() == critical
                && (chapter == null || q.chapterIds().contains(chapter))).findFirst().orElseThrow().id();
    }

    private UUID unused(ExamQuestionPool pool, List<UUID> fixed, UUID chapter, boolean critical) {
        return pool.questions().stream().filter(q -> !fixed.contains(q.id()) && q.isCritical() == critical
                && (chapter == null || q.chapterIds().contains(chapter))).findFirst().orElseThrow().id();
    }

    private void assertPaper(ExamQuestionSelection selection, ExamQuestionPool pool,
            List<Section> sections, int total) {
        assertEquals(total, selection.questions().size());
        assertEquals(total, new HashSet<>(ids(selection)).size());
        assertEquals(1, selection.questions().stream().filter(ExamSourceQuestionMetadata::isCritical).count());
        assertEquals(pool.bankVersion(), selection.bankVersion());
        assertEquals(pool.licenseClass(), selection.licenseClass());
        assertEquals(total, selection.sectionCodeByQuestionId().size());
        Map<UUID, ExamSourceQuestionMetadata> source = new HashMap<>();
        pool.questions().forEach(q -> source.put(q.id(), q));
        for (var question : selection.questions()) {
            assertEquals(source.get(question.id()), question);
        }
        for (Section section : sections) {
            var selected = selection.questions().stream().filter(q -> section.getCode()
                    .equals(selection.sectionCodeByQuestionId().get(q.id()))).toList();
            assertEquals(section.getQuestionCount().intValue(), selected.size());
            assertEquals(section.getCriticalQuestionCount().longValue(), selected.stream()
                    .filter(ExamSourceQuestionMetadata::isCritical).count());
            if (!section.getChapterIds().isEmpty()) {
                assertTrue(selected.stream().allMatch(q -> !java.util.Collections.disjoint(
                        section.getChapterIds(), q.chapterIds())));
            }
        }
    }

    private void error(String code, HttpStatus status, Runnable action) {
        ExaminationException error = assertThrows(ExaminationException.class, action::run);
        assertEquals(code, error.getCode());
        assertEquals(status, error.getStatus());
    }
}
