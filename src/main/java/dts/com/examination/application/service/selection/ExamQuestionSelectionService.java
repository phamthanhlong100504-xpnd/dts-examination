package dts.com.examination.application.service.selection;

import dts.com.examination.application.dto.SectionDto;
import dts.com.examination.application.exception.ExaminationException;
import dts.com.examination.application.service.ExamStructureValidator;
import dts.com.examination.domain.entity.Section;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

@Service
public class ExamQuestionSelectionService {
    private final ExamMatrixPolicyCatalog catalog;
    private final ExamStructureValidator structureValidator;
    private final RandomGenerator random;

    @Autowired
    public ExamQuestionSelectionService(ExamMatrixPolicyCatalog catalog, ExamStructureValidator validator) {
        this(catalog, validator, new SecureRandom());
    }

    ExamQuestionSelectionService(ExamMatrixPolicyCatalog catalog, ExamStructureValidator validator,
            RandomGenerator random) {
        this.catalog = catalog;
        this.structureValidator = validator;
        this.random = random;
    }

    /** Publish readiness only. Do not store this deterministic allocation as an AUTO_MATRIX paper. */
    public void validateAutomaticReadiness(String licenseClass, Instant examTime,
            ExamQuestionPool pool, List<Section> sections) {
        Prepared input = prepare(licenseClass, examTime, pool, sections);
        allocate(input, input.questions(), ExamSelectionMode.AUTO_MATRIX);
    }

    /** Call on a NEW attempt, never on resume, paper reads, answer saves or grading. */
    public ExamQuestionSelection select(String licenseClass, Instant examTime,
            ExamQuestionPool pool, List<Section> sections, ExamSelectionMode mode,
            List<UUID> manualQuestionIds) {
        if (mode == null) {
            throw bad("SELECTION_MODE_INVALID", "An explicit question selection mode is required");
        }
        Prepared input = prepare(licenseClass, examTime, pool, sections);
        List<ExamSourceQuestionMetadata> candidates;
        if (mode == ExamSelectionMode.MANUAL) {
            candidates = manualCandidates(input, manualQuestionIds);
        } else {
            if (manualQuestionIds != null && !manualQuestionIds.isEmpty()) {
                throw bad("MATRIX_INVALID", "AUTO_MATRIX must not contain a manual question list");
            }
            candidates = new ArrayList<>(input.questions());
            shuffle(candidates);
        }
        int[] selected = allocate(input, candidates, mode);
        Map<UUID, String> assignments = new LinkedHashMap<>();
        List<ExamSourceQuestionMetadata> ordered = new ArrayList<>();
        for (int i = 0; i < selected.length; i++) {
            ExamSourceQuestionMetadata question = candidates.get(selected[i]);
            assignments.put(question.id(), input.slots().get(i).getCode());
            ordered.add(question);
        }
        // Fixed manual IDs preserve the creator's order; allocation only determines sections.
        if (mode == ExamSelectionMode.MANUAL) {
            ordered = candidates;
        }
        return new ExamQuestionSelection(input.policy().id(), input.policy().bankVersion(),
                input.policy().licenseClass(), mode, ordered, assignments);
    }

    private Prepared prepare(String licenseClass, Instant examTime, ExamQuestionPool pool,
            List<Section> sections) {
        ExamMatrixPolicy policy = catalog.require(licenseClass, examTime);
        if (pool == null || !policy.bankVersion().equals(pool.bankVersion())
                || !policy.licenseClass().equals(pool.licenseClass())) {
            throw source("The source pool does not match the licensed matrix bank");
        }
        Map<UUID, Integer> chapters = chapterNumbers(pool.chapters(), policy.bankVersion());
        List<ExamSourceQuestionMetadata> questions = validateQuestions(pool.questions(), chapters.keySet());
        if (sections == null || sections.stream().anyMatch(java.util.Objects::isNull)) {
            throw bad("MATRIX_INVALID", "A valid structure is required");
        }
        List<Section> normalized = structureValidator.normalize(sections.stream().map(s ->
                new SectionDto(s.getCode(), s.getTitle(), s.getQuestionCount(), s.getScore(),
                        s.getOrder(), s.getChapterIds(), s.getCriticalQuestionCount())).toList());
        validateStandardStructure(policy, normalized, chapters);
        List<Section> slots = new ArrayList<>();
        for (Section section : normalized) {
            for (int i = 0; i < section.getQuestionCount(); i++) {
                slots.add(section);
            }
        }
        return new Prepared(policy, questions, slots);
    }

    private Map<UUID, Integer> chapterNumbers(List<ExamQuestionPool.ChapterReference> references,
            String bankVersion) {
        if (references == null || references.size() != 6) {
            throw source("A complete published six-chapter catalog is required");
        }
        Map<UUID, Integer> result = new LinkedHashMap<>();
        Set<Integer> numbers = new HashSet<>();
        for (var chapter : references) {
            if (chapter == null || chapter.id() == null || chapter.sourceChapterId() < 1
                    || chapter.sourceChapterId() > 6 || !bankVersion.equals(chapter.bankVersion())
                    || result.putIfAbsent(chapter.id(), chapter.sourceChapterId()) != null
                    || !numbers.add(chapter.sourceChapterId())) {
                throw source("The source chapter catalog is ambiguous or mixes banks");
            }
        }
        return result;
    }

    private List<ExamSourceQuestionMetadata> validateQuestions(List<ExamSourceQuestionMetadata> questions,
            Set<UUID> sourceChapters) {
        if (questions == null) {
            throw source("Content Builder did not return a question list");
        }
        Map<UUID, ExamSourceQuestionMetadata> unique = new LinkedHashMap<>();
        for (var question : questions) {
            if (question == null || question.id() == null || question.isCritical() == null
                    || !"PUBLISHED".equals(question.status()) || question.contentVersion() == null
                    || !question.contentVersion().matches("sha256:[0-9a-f]{64}")
                    || !validIds(question.optionIds(), 2) || !validIds(question.chapterIds(), 1)
                    || !sourceChapters.containsAll(question.chapterIds())
                    || !validIds(question.mediaFileIds(), 0)) {
                throw source("Content Builder returned incomplete or inconsistent published metadata");
            }
            if (question.questionType() == null) {
                throw source("Content Builder did not return a question type");
            }
            if (!Set.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE").contains(question.questionType())) {
                throw new ExaminationException(HttpStatus.CONFLICT, "SOURCE_QUESTION_TYPE_UNSUPPORTED",
                        "The examination engine does not support a source question type");
            }
            ExamSourceQuestionMetadata previous = unique.putIfAbsent(question.id(), question);
            if (previous != null && !previous.equals(question)) {
                throw source("Repeated source question IDs disagree in content or revision");
            }
        }
        return unique.values().stream().sorted(Comparator.comparing(q -> q.id().toString())).toList();
    }

    private boolean validIds(List<UUID> ids, int minimum) {
        return ids != null && ids.size() >= minimum && ids.stream().noneMatch(java.util.Objects::isNull)
                && new HashSet<>(ids).size() == ids.size();
    }

    private void validateStandardStructure(ExamMatrixPolicy policy, List<Section> sections,
            Map<UUID, Integer> chapterNumbers) {
        Map<Set<Integer>, Integer> ordinaryCounts = new HashMap<>();
        int criticalCount = 0;
        for (var section : sections) {
            Integer critical = section.getCriticalQuestionCount();
            if (critical == null) {
                throw bad("MATRIX_INVALID", "Official matrix sections require an explicit critical quota");
            }
            if (critical > 0) {
                if (critical.intValue() != section.getQuestionCount() || !section.getChapterIds().isEmpty()) {
                    throw bad("MATRIX_INVALID", "Critical questions require a separate unrestricted section");
                }
                criticalCount += critical;
            } else {
                Set<Integer> numbers = new HashSet<>();
                for (UUID chapter : section.getChapterIds()) {
                    Integer number = chapterNumbers.get(chapter);
                    if (number == null) {
                        throw bad("MATRIX_INVALID", "A structure chapter is outside the published source bank");
                    }
                    numbers.add(number);
                }
                if (numbers.isEmpty()) {
                    throw bad("MATRIX_INVALID", "Ordinary matrix sections require explicit source chapters");
                }
                ordinaryCounts.merge(Set.copyOf(numbers), section.getQuestionCount(), Math::addExact);
            }
        }
        Map<Set<Integer>, Integer> standardCounts = new HashMap<>();
        for (var topic : policy.topics()) {
            standardCounts.put(Set.copyOf(topic.sourceChapterIds()), topic.questionCount());
        }
        if (!ordinaryCounts.equals(standardCounts) || criticalCount != policy.criticalQuestionCount()
                || structureValidator.totals(sections).totalQuestions() != policy.totalQuestions()) {
            throw bad("MATRIX_INVALID", "The structure does not match the official licensed topic and critical quotas");
        }
    }

    private List<ExamSourceQuestionMetadata> manualCandidates(Prepared input, List<UUID> ids) {
        if (ids == null || ids.size() != input.policy().totalQuestions()) {
            throw bad("MANUAL_QUESTION_COUNT", "The manual paper must have the exact licensed question count");
        }
        Set<UUID> unique = new HashSet<>();
        Map<UUID, ExamSourceQuestionMetadata> available = new HashMap<>();
        input.questions().forEach(q -> available.put(q.id(), q));
        List<ExamSourceQuestionMetadata> result = new ArrayList<>();
        for (UUID id : ids) {
            if (id == null || !unique.add(id)) {
                throw bad("MANUAL_DUPLICATE_QUESTION", "Manual question IDs must be non-null and unique");
            }
            ExamSourceQuestionMetadata question = available.get(id);
            if (question == null) {
                throw bad("MANUAL_QUESTION_NOT_APPLICABLE", "A manual question is outside the published licensed source");
            }
            result.add(question);
        }
        return List.copyOf(result);
    }

    /** Maximum bipartite matching, so overlapping section eligibility cannot consume a scarce slot. */
    private int[] allocate(Prepared input, List<ExamSourceQuestionMetadata> candidates,
            ExamSelectionMode mode) {
        int[] owners = new int[candidates.size()];
        int[] selected = new int[input.slots().size()];
        Arrays.fill(owners, -1);
        Arrays.fill(selected, -1);
        for (int slot = 0; slot < input.slots().size(); slot++) {
            if (!assign(slot, input.slots(), candidates, owners, selected, new boolean[candidates.size()])) {
                if (mode == ExamSelectionMode.MANUAL) {
                    throw bad("MANUAL_MATRIX_MISMATCH", "The selected questions violate a topic or critical quota");
                }
                throw new ExaminationException(HttpStatus.CONFLICT, "MATRIX_SOURCE_INSUFFICIENT",
                        "The published licensed source cannot fill every matrix slot without duplicates");
            }
        }
        return selected;
    }

    private boolean assign(int slot, List<Section> slots, List<ExamSourceQuestionMetadata> questions,
            int[] owners, int[] selected, boolean[] seen) {
        Section section = slots.get(slot);
        boolean critical = section.getCriticalQuestionCount() > 0;
        for (int candidate = 0; candidate < questions.size(); candidate++) {
            ExamSourceQuestionMetadata question = questions.get(candidate);
            if (seen[candidate] || question.isCritical() != critical
                    || (!section.getChapterIds().isEmpty()
                    && java.util.Collections.disjoint(section.getChapterIds(), question.chapterIds()))) {
                continue;
            }
            seen[candidate] = true;
            if (owners[candidate] < 0 || assign(owners[candidate], slots, questions, owners, selected, seen)) {
                owners[candidate] = slot;
                selected[slot] = candidate;
                return true;
            }
        }
        return false;
    }

    private <T> void shuffle(List<T> values) {
        for (int i = values.size() - 1; i > 0; i--) {
            int other = random.nextInt(i + 1);
            T old = values.get(i);
            values.set(i, values.get(other));
            values.set(other, old);
        }
    }

    private ExaminationException bad(String code, String message) {
        return new ExaminationException(HttpStatus.BAD_REQUEST, code, message);
    }

    private ExaminationException source(String message) {
        return new ExaminationException(HttpStatus.BAD_GATEWAY, "SOURCE_INVALID_RESPONSE", message);
    }

    private record Prepared(ExamMatrixPolicy policy, List<ExamSourceQuestionMetadata> questions,
            List<Section> slots) {
    }
}
