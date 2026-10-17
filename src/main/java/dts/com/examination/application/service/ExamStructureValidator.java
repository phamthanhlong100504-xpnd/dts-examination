package dts.com.examination.application.service;

import dts.com.examination.application.dto.SectionDto;
import dts.com.examination.application.exception.ExaminationException;
import dts.com.examination.domain.entity.Section;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class ExamStructureValidator {

    public List<Section> normalize(List<SectionDto> sections) {
        if (sections == null || sections.isEmpty()) {
            throw invalid("A structure must contain at least one section");
        }
        Set<String> codes = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        List<Section> result = new ArrayList<>();
        for (SectionDto dto : sections) {
            if (dto == null) {
                throw invalid("Sections must not contain null entries");
            }
            String code = text(dto.code(), 64, "Section code");
            String title = text(dto.title(), 255, "Section title");
            if (!codes.add(code.toLowerCase(Locale.ROOT))) {
                throw invalid("Section codes must be unique within the structure");
            }
            if (dto.questionCount() == null || dto.questionCount() < 1) {
                throw invalid("Section question count must be greater than zero");
            }
            if (dto.score() == null || dto.score() < 0) {
                throw invalid("Section score must be non-negative");
            }
            if (dto.order() == null || dto.order() < 0 || !orders.add(dto.order())) {
                throw invalid("Section orders must be non-negative and unique");
            }
            Integer critical = dto.criticalQuestionCount();
            if (critical != null && (critical < 0 || critical > dto.questionCount())) {
                throw invalid("Critical question count must be between zero and the section question count");
            }
            List<UUID> chapters = dto.chapterIds() == null ? List.of() : dto.chapterIds();
            Set<UUID> uniqueChapters = new HashSet<>();
            for (UUID chapterId : chapters) {
                if (chapterId == null || !uniqueChapters.add(chapterId)) {
                    throw invalid("Chapter IDs must be non-null and unique within a section");
                }
            }
            result.add(Section.builder().code(code).title(title)
                    .questionCount(dto.questionCount()).score(dto.score()).order(dto.order())
                    .chapterIds(List.copyOf(chapters)).criticalQuestionCount(critical).build());
        }
        result.sort(Comparator.comparing(Section::getOrder));
        totals(result);
        return List.copyOf(result);
    }

    public Totals totals(List<Section> sections) {
        if (sections == null || sections.isEmpty()) {
            throw invalid("A structure must contain at least one section");
        }
        long questions = 0;
        long score = 0;
        for (Section section : sections) {
            if (section == null || section.getQuestionCount() == null || section.getQuestionCount() < 1
                    || section.getScore() == null || section.getScore() < 0) {
                throw invalid("Stored structure contains an invalid section count or score");
            }
            questions += section.getQuestionCount();
            score += (long) section.getQuestionCount() * section.getScore();
            if (questions > Integer.MAX_VALUE || score > Integer.MAX_VALUE) {
                throw invalid("Structure totals exceed the supported integer range");
            }
        }
        return new Totals((int) questions, (int) score);
    }

    public String text(String value, int maximumLength, String label) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw invalid(label + " must be non-blank and at most " + maximumLength + " characters");
        }
        return value.trim();
    }

    private ExaminationException invalid(String message) {
        return new ExaminationException(HttpStatus.BAD_REQUEST, "STRUCTURE_INVALID", message);
    }

    public record Totals(int totalQuestions, int maxScore) {
    }
}
