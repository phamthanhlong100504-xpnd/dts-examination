package dts.com.examination.application.service.selection;

import dts.com.examination.api.response.InternalQuestionDetailResponse;
import dts.com.examination.application.client.ContentBuilderClient;
import dts.com.examination.application.exception.ExaminationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExamQuestionSourceReader {
    private static final int BATCH_SIZE = 500;
    private static final Set<String> LICENSES = Set.of("A1", "A", "B1", "B", "C1", "C",
            "D1", "D2", "D", "BE", "C1E", "CE", "D1E", "D2E", "DE");
    private final ContentBuilderClient client;

    /** Always uses licensed, signed service-to-service source reads. No Practice question source. */
    public ExamQuestionPool loadBank(String bankVersion, String licenseClass) {
        if (bankVersion == null || !bankVersion.matches("[A-Za-z0-9._:-]{1,128}") || licenseClass == null) {
            throw bad("A valid source bank and license class are required");
        }
        String license = licenseClass.trim().toUpperCase(Locale.ROOT);
        if (!LICENSES.contains(license)) {
            throw bad("Unsupported license class");
        }
        ExamQuestionPool pool = client.getQuestionBank(bankVersion, license);
        if (pool == null || !bankVersion.equals(pool.bankVersion()) || !license.equals(pool.licenseClass())) {
            throw invalid("Source bank or license class does not match the request");
        }
        validatePool(pool);
        return pool;
    }

    /** Existing content_id can remain a stable source-owned anchor in the selected bank. */
    public void requireBankAnchor(ExamQuestionPool pool, UUID contentId) {
        validatePool(pool);
        if (contentId == null || pool.chapters().stream().noneMatch(chapter -> contentId.equals(chapter.id()))) {
            throw bad("contentId must reference a published chapter in the selected source bank");
        }
    }

    /** Metadata and detail revision must agree BEFORE a private snapshot is accepted. */
    public List<FrozenExamQuestion> readVerifiedContent(ExamQuestionPool pool, List<UUID> questionIds) {
        validatePool(pool);
        if (questionIds == null || questionIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(questionIds).size() != questionIds.size()) {
            throw bad("Question IDs must be a non-null list of unique IDs");
        }
        Map<UUID, ExamSourceQuestionMetadata> expected = new LinkedHashMap<>();
        pool.questions().forEach(question -> expected.put(question.id(), question));
        if (!expected.keySet().containsAll(questionIds)) {
            throw bad("A requested question is outside the published licensed source bank");
        }
        List<FrozenExamQuestion> result = new ArrayList<>();
        for (int start = 0; start < questionIds.size(); start += BATCH_SIZE) {
            List<UUID> ids = List.copyOf(questionIds.subList(start, Math.min(start + BATCH_SIZE, questionIds.size())));
            List<InternalQuestionDetailResponse> details = client.getQuestionsBatch(ids, pool.licenseClass());
            if (details == null || details.size() != ids.size()) {
                throw invalid("Source detail batch is incomplete");
            }
            for (int i = 0; i < ids.size(); i++) {
                InternalQuestionDetailResponse detail = details.get(i);
                if (detail == null || !ids.get(i).equals(detail.getId())) {
                    throw invalid("Source detail batch does not preserve requested question order");
                }
                result.add(freeze(expected.get(ids.get(i)), detail));
            }
        }
        return List.copyOf(result);
    }

    private void validatePool(ExamQuestionPool pool) {
        if (pool == null || pool.bankVersion() == null || pool.licenseClass() == null
                || !LICENSES.contains(pool.licenseClass()) || pool.chapters() == null
                || pool.chapters().size() != 6 || pool.questions() == null) {
            throw invalid("Source bank is incomplete");
        }
        Set<UUID> chapterIds = new HashSet<>();
        Set<Integer> numbers = new HashSet<>();
        for (var chapter : pool.chapters()) {
            if (chapter.id() == null || chapter.sourceChapterId() < 1 || chapter.sourceChapterId() > 6
                    || !pool.bankVersion().equals(chapter.bankVersion())
                    || !chapterIds.add(chapter.id()) || !numbers.add(chapter.sourceChapterId())) {
                throw invalid("Source chapter catalog is ambiguous or mixes banks");
            }
        }
        Set<UUID> questionIds = new HashSet<>();
        for (var question : pool.questions()) {
            if (question.id() == null || !questionIds.add(question.id()) || question.isCritical() == null
                    || !"PUBLISHED".equals(question.status()) || question.contentVersion() == null
                    || !question.contentVersion().matches("sha256:[0-9a-f]{64}")
                    || !validIds(question.optionIds(), 2) || !validIds(question.chapterIds(), 1)
                    || !chapterIds.containsAll(question.chapterIds()) || !validIds(question.mediaFileIds(), 0)
                    || question.questionType() == null) {
                throw invalid("Source question metadata is inconsistent");
            }
            if (!Set.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE").contains(question.questionType())) {
                throw new ExaminationException(HttpStatus.CONFLICT, "SOURCE_QUESTION_TYPE_UNSUPPORTED",
                        "The examination engine does not support a source question type");
            }
        }
    }

    private boolean validIds(List<UUID> ids, int minimum) {
        return ids != null && ids.size() >= minimum && ids.stream().noneMatch(Objects::isNull)
                && new HashSet<>(ids).size() == ids.size();
    }

    private FrozenExamQuestion freeze(ExamSourceQuestionMetadata expected, InternalQuestionDetailResponse detail) {
        if (!expected.contentVersion().equals(detail.getContentVersion())
                || !expected.status().equals(detail.getStatus()) || !expected.questionType().equals(detail.getType())
                || !expected.isCritical().equals(detail.getIsCritical())) {
            throw changed();
        }
        if (detail.getContent() == null || detail.getContent().isBlank() || detail.getOptions() == null
                || detail.getMediaFileIds() == null) {
            throw invalid("Source question content is incomplete");
        }
        List<UUID> media = new ArrayList<>();
        for (String value : detail.getMediaFileIds()) {
            if (value == null || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
                throw invalid("Source media reference must be a UUID, not a URL or filesystem path");
            }
            media.add(UUID.fromString(value));
        }
        List<FrozenExamQuestion.Option> options = new ArrayList<>();
        List<UUID> optionIds = new ArrayList<>();
        for (var option : detail.getOptions()) {
            if (option == null || option.getId() == null || option.getIsCorrect() == null
                    || option.getContent() == null || option.getContent().isBlank()
                    || (option.getSortOrder() != null && option.getSortOrder() < 0)) {
                throw invalid("Source option content or answer key is incomplete");
            }
            optionIds.add(option.getId());
            options.add(new FrozenExamQuestion.Option(option.getId(), option.getContent(),
                    option.getSortOrder(), option.getIsCorrect()));
        }
        if (!expected.optionIds().equals(optionIds) || !expected.mediaFileIds().equals(media)) {
            throw changed();
        }
        long correct = options.stream().filter(FrozenExamQuestion.Option::isCorrect).count();
        if (correct == 0 || ("SINGLE_CHOICE".equals(detail.getType()) && correct != 1)
                || ("TRUE_FALSE".equals(detail.getType()) && (options.size() != 2 || correct != 1))) {
            throw invalid("Source correct-answer configuration is invalid");
        }
        try {
            return new FrozenExamQuestion(detail.getId(), detail.getContentVersion(), detail.getContent(),
                    detail.getType(), detail.getIsCritical(), expected.chapterIds(), media, options, detail.getExplanations());
        } catch (IllegalArgumentException error) {
            throw invalid("Source explanation has an invalid JSON value");
        }
    }

    private ExaminationException changed() {
        return new ExaminationException(HttpStatus.CONFLICT, "SOURCE_CHANGED",
                "Source changed between metadata and content reads. Read the source again.");
    }

    private ExaminationException invalid(String message) {
        return new ExaminationException(HttpStatus.BAD_GATEWAY, "SOURCE_INVALID_RESPONSE", message);
    }

    private ExaminationException bad(String message) {
        return new ExaminationException(HttpStatus.BAD_REQUEST, "SOURCE_BAD_REQUEST", message);
    }
}
