package dts.com.examination.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dts.com.examination.api.form.CreateExamStructureRequest;
import dts.com.examination.api.form.UpdateExamStructureRequest;
import dts.com.examination.api.response.ExamStructureResponse;
import dts.com.examination.application.dto.SectionDto;
import dts.com.examination.application.exception.ExaminationException;
import dts.com.examination.application.exception.ResourceNotFoundException;
import dts.com.examination.application.service.impl.ExamStructureServiceImpl;
import dts.com.examination.domain.entity.ExamStructure;
import dts.com.examination.domain.entity.Section;
import dts.com.examination.domain.repository.ExamStructureRepository;
import dts.com.examination.domain.repository.ExamVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExamStructureServiceTest {

    // Chapter references from the verified published Content Builder bank.
    private static final UUID CHAPTER_ONE = UUID.fromString("19cd4bb3-7003-418f-bc98-2594a362e497");
    private static final UUID CHAPTER_SIX = UUID.fromString("500baaec-1e1f-4cc3-afd7-69865226de2d");
    private static final UUID STRUCTURE_ID = new UUID(0, 1);
    private static final UUID ACTOR = new UUID(0, 2);
    private ExamStructureRepository structures;
    private ExamVersionRepository versions;
    private ExamStructureValidator validator;
    private ExamStructureServiceImpl service;

    @BeforeEach
    void setUp() {
        structures = mock(ExamStructureRepository.class);
        versions = mock(ExamVersionRepository.class);
        validator = new ExamStructureValidator();
        service = new ExamStructureServiceImpl(structures, versions, validator);
        when(structures.save(any(ExamStructure.class))).thenAnswer(call -> {
            ExamStructure entity = call.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(STRUCTURE_ID);
            }
            return entity;
        });
    }

    @Test
    void createPersistsSelectorsQuotasWeightsAndCalculatedTotalsInStableOrder() {
        SectionDto late = new SectionDto("SA_HINH", " Sa hinh ", 2, 3, 2, List.of(CHAPTER_SIX), 0);
        SectionDto early = new SectionDto(" LUAT ", "Luat", 3, 2, 1, List.of(CHAPTER_ONE), 1);
        ExamStructureResponse response = service.create(
                new CreateExamStructureRequest(" Structure ", List.of(late, early), Map.of("note", "admin config")), ACTOR);
        assertEquals("Structure", response.title());
        assertEquals(5, response.totalQuestions());
        assertEquals(12, response.maxScore());
        assertEquals(List.of("LUAT", "SA_HINH"), response.sections().stream().map(SectionDto::code).toList());
        assertEquals(List.of(CHAPTER_ONE), response.sections().get(0).chapterIds());
        assertEquals(1, response.sections().get(0).criticalQuestionCount());
        assertEquals(0, response.sections().get(1).criticalQuestionCount());
        assertEquals("DRAFT", response.status());
    }

    @Test
    void oldSectionJsonAndFiveArgumentConstructorsRemainCompatible() throws Exception {
        Section section = new ObjectMapper().readValue(
                "{\"code\":\"LEGACY\",\"title\":\"Legacy\",\"questionCount\":2,\"score\":1,\"order\":1}", Section.class);
        assertTrue(section.getChapterIds().isEmpty());
        assertNull(section.getCriticalQuestionCount());
        SectionDto dto = new SectionDto("LEGACY", "Legacy", 2, 1, 1);
        assertTrue(dto.chapterIds().isEmpty());
        assertNull(dto.criticalQuestionCount());
        assertNull(new Section("LEGACY", "Legacy", 2, 1, 1).getCriticalQuestionCount());
        ExamStructureResponse response = service.create(new CreateExamStructureRequest("Legacy", List.of(dto), null), ACTOR);
        assertEquals(2, response.totalQuestions());
        assertEquals(2, response.maxScore());
    }

    @ParameterizedTest(name = "Rejects invalid matrix: {0}")
    @MethodSource("invalidMatrices")
    void invalidMatricesNeverReachTheRepository(String reason, List<SectionDto> sections) {
        ExaminationException error = assertThrows(ExaminationException.class,
                () -> service.create(new CreateExamStructureRequest("Structure", sections, null), ACTOR));
        assertEquals(400, error.getStatus().value());
        assertEquals("STRUCTURE_INVALID", error.getCode());
        verifyNoInteractions(structures, versions);
    }

    static Stream<Arguments> invalidMatrices() {
        SectionDto valid = new SectionDto("A", "A", 2, 1, 1);
        return Stream.of(
                Arguments.of("null sections", null),
                Arguments.of("empty sections", List.of()),
                Arguments.of("null entry", Arrays.asList((SectionDto) null)),
                Arguments.of("blank code", List.of(new SectionDto(" ", "A", 2, 1, 1))),
                Arguments.of("duplicate code ignoring case", List.of(valid, new SectionDto("a", "B", 1, 1, 2))),
                Arguments.of("code too long", List.of(new SectionDto("x".repeat(65), "A", 2, 1, 1))),
                Arguments.of("blank title", List.of(new SectionDto("A", " ", 2, 1, 1))),
                Arguments.of("null count", List.of(new SectionDto("A", "A", null, 1, 1))),
                Arguments.of("zero count", List.of(new SectionDto("A", "A", 0, 1, 1))),
                Arguments.of("negative count", List.of(new SectionDto("A", "A", -1, 1, 1))),
                Arguments.of("null weight", List.of(new SectionDto("A", "A", 2, null, 1))),
                Arguments.of("negative weight", List.of(new SectionDto("A", "A", 2, -1, 1))),
                Arguments.of("null order", List.of(new SectionDto("A", "A", 2, 1, null))),
                Arguments.of("negative order", List.of(new SectionDto("A", "A", 2, 1, -1))),
                Arguments.of("duplicate order", List.of(valid, new SectionDto("B", "B", 1, 1, 1))),
                Arguments.of("negative critical quota", List.of(new SectionDto("A", "A", 2, 1, 1, null, -1))),
                Arguments.of("critical quota exceeds count", List.of(new SectionDto("A", "A", 2, 1, 1, null, 3))),
                Arguments.of("null chapter", List.of(new SectionDto("A", "A", 2, 1, 1, Arrays.asList((UUID) null), null))),
                Arguments.of("duplicate chapter", List.of(new SectionDto("A", "A", 2, 1, 1, List.of(CHAPTER_ONE, CHAPTER_ONE), null))),
                Arguments.of("count overflow", List.of(new SectionDto("A", "A", Integer.MAX_VALUE, 0, 1),
                        new SectionDto("B", "B", 1, 0, 2))),
                Arguments.of("score overflow", List.of(new SectionDto("A", "A", 2, Integer.MAX_VALUE, 1)))
        );
    }

    @Test
    void updateBlocksStructuresUsedByPublishedOrArchivedVersionHistory() {
        ExamStructure entity = stored();
        when(structures.findByIdForUpdate(STRUCTURE_ID)).thenReturn(Optional.of(entity));
        when(versions.existsByExamStructureIdAndStatusIn(eq(STRUCTURE_ID), anyCollection())).thenReturn(true);
        ExaminationException error = assertThrows(ExaminationException.class, () -> service.update(STRUCTURE_ID,
                new UpdateExamStructureRequest("Changed", null, null, null), ACTOR));
        assertEquals(409, error.getStatus().value());
        assertEquals("STRUCTURE_IN_USE", error.getCode());
        assertEquals("Original", entity.getTitle());
        verify(versions).existsByExamStructureIdAndStatusIn(STRUCTURE_ID, List.of("PUBLISHED", "ARCHIVED"));
        verify(structures, never()).save(any());
    }

    @Test
    void anUnpublishedStructureCanBeEditedAndTotalsFollowTheSavedSections() {
        ExamStructure entity = stored();
        when(structures.findByIdForUpdate(STRUCTURE_ID)).thenReturn(Optional.of(entity));
        ExamStructureResponse response = service.update(STRUCTURE_ID, new UpdateExamStructureRequest("Changed",
                List.of(new SectionDto("NEW", "New", 4, 3, 1, List.of(CHAPTER_SIX), 0)), null, null), ACTOR);
        assertEquals(4, response.totalQuestions());
        assertEquals(12, response.maxScore());
        assertEquals(List.of(CHAPTER_SIX), response.sections().get(0).chapterIds());
        assertEquals(ACTOR, entity.getUpdatedBy());
    }

    @Test
    void invalidPartialUpdateDoesNotMutateTheManagedEntity() {
        ExamStructure entity = stored();
        when(structures.findByIdForUpdate(STRUCTURE_ID)).thenReturn(Optional.of(entity));
        assertThrows(ExaminationException.class, () -> service.update(STRUCTURE_ID,
                new UpdateExamStructureRequest("Changed", List.of(), null, null), ACTOR));
        assertEquals("Original", entity.getTitle());
        assertEquals(2, entity.getSections().get(0).getQuestionCount());
        verify(structures, never()).save(any());
    }

    @Test
    void anyReferencedVersionPreventsDeletionIncludingSoftDeletedHistory() {
        ExamStructure entity = stored();
        when(structures.findByIdForUpdate(STRUCTURE_ID)).thenReturn(Optional.of(entity));
        when(versions.existsByExamStructureId(STRUCTURE_ID)).thenReturn(true);
        ExaminationException error = assertThrows(ExaminationException.class, () -> service.delete(STRUCTURE_ID, ACTOR));
        assertEquals("STRUCTURE_REFERENCED", error.getCode());
        assertEquals(409, error.getStatus().value());
        assertNull(entity.getDeletedAt());
        verify(structures, never()).save(any());
    }

    @Test
    void unreferencedDeletionIsSoftAndPreservesTheAuditActor() {
        ExamStructure entity = stored();
        when(structures.findByIdForUpdate(STRUCTURE_ID)).thenReturn(Optional.of(entity));
        service.delete(STRUCTURE_ID, ACTOR);
        assertNotNull(entity.getDeletedAt());
        assertNotNull(entity.getUpdatedAt());
        assertEquals(ACTOR, entity.getUpdatedBy());
        verify(structures).save(entity);
    }

    @Test
    void aMissingOrDeletedStructureCannotBeUpdatedOrDeleted() {
        when(structures.findByIdForUpdate(STRUCTURE_ID)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.update(STRUCTURE_ID,
                new UpdateExamStructureRequest("Changed", null, null, null), ACTOR));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(STRUCTURE_ID, ACTOR));
        verify(structures, never()).save(any());
    }

    @Test
    void writesRequireAnAuthenticatedActor() {
        assertThrows(AccessDeniedException.class, () -> service.create(
                new CreateExamStructureRequest("Structure", List.of(new SectionDto("A", "A", 2, 1, 1)), null), null));
        assertThrows(AccessDeniedException.class, () -> service.delete(STRUCTURE_ID, null));
        verifyNoInteractions(structures, versions);
    }

    @Test
    void blankTopLevelTitleIsRejectedBeforeAnyWrite() {
        assertThrows(ExaminationException.class, () -> service.create(
                new CreateExamStructureRequest(" ", List.of(new SectionDto("A", "A", 2, 1, 1)), null), ACTOR));
        verifyNoInteractions(structures, versions);
    }

    @Test
    void emptyListsRemainAValidPageResponse() {
        PageRequest page = PageRequest.of(0, 20);
        when(structures.findByFilters(null, null, page)).thenReturn(Page.empty(page));
        Page<ExamStructureResponse> response = service.getList(null, null, page);
        assertTrue(response.isEmpty());
        assertEquals(0, response.getTotalElements());
        assertFalse(response.hasNext());
    }

    private ExamStructure stored() {
        return ExamStructure.builder().id(STRUCTURE_ID).title("Original")
                .sections(List.of(new Section("A", "A", 2, 1, 1))).createdBy(ACTOR).build();
    }
}
