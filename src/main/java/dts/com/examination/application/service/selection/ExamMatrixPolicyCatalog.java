package dts.com.examination.application.service.selection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dts.com.examination.application.exception.ExaminationException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ExamMatrixPolicyCatalog {
    private static final ZoneId EXAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final Map<String, ExamMatrixPolicy> policies;

    public ExamMatrixPolicyCatalog(ObjectMapper mapper) {
        try (var input = new ClassPathResource("exam-matrices/csgt-2262-2025.json").getInputStream()) {
            JsonNode root = mapper.readTree(input);
            Map<String, ExamMatrixPolicy> loaded = new LinkedHashMap<>();
            for (JsonNode profile : root.required("profiles")) {
                List<ExamMatrixPolicy.TopicQuota> topics = new ArrayList<>();
                for (JsonNode topic : profile.required("topics")) {
                    List<Integer> chapters = new ArrayList<>();
                    topic.required("sourceChapterIds").forEach(id -> chapters.add(id.intValue()));
                    topics.add(new ExamMatrixPolicy.TopicQuota(topic.required("code").textValue(),
                            chapters, topic.required("questionCount").intValue()));
                }
                for (JsonNode license : profile.required("licenseClasses")) {
                    String code = license.textValue();
                    ExamMatrixPolicy policy = new ExamMatrixPolicy(root.required("id").textValue(),
                            root.required("bankVersion").textValue(),
                            LocalDate.parse(root.required("validFrom").textValue()),
                            LocalDate.parse(root.required("validUntilExclusive").textValue()), code,
                            profile.required("totalQuestions").intValue(),
                            profile.required("durationMinutes").intValue(),
                            profile.required("passingCorrectAnswers").intValue(),
                            profile.required("criticalQuestionCount").intValue(), topics);
                    if (loaded.putIfAbsent(code, policy) != null) {
                        throw new IllegalStateException("Duplicate matrix license class");
                    }
                    validate(policy);
                }
            }
            policies = Map.copyOf(loaded);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot load official examination matrix policy", e);
        }
    }

    public ExamMatrixPolicy require(String licenseClass, Instant examTime) {
        if (licenseClass == null || licenseClass.isBlank() || examTime == null) {
            throw invalid("A license class and examination time are required");
        }
        ExamMatrixPolicy policy = policies.get(licenseClass.trim().toUpperCase(Locale.ROOT));
        if (policy == null) {
            throw invalid("The license class has no supported official matrix");
        }
        LocalDate date = examTime.atZone(EXAM_ZONE).toLocalDate();
        if (date.isBefore(policy.validFrom()) || !date.isBefore(policy.validUntilExclusive())) {
            throw new ExaminationException(HttpStatus.CONFLICT, "MATRIX_POLICY_UNAVAILABLE",
                    "No verified matrix policy is available for the examination date");
        }
        return policy;
    }

    private void validate(ExamMatrixPolicy policy) {
        int ordinary = policy.topics().stream().mapToInt(ExamMatrixPolicy.TopicQuota::questionCount).sum();
        if (policy.id() == null || policy.id().isBlank() || policy.bankVersion() == null
                || policy.topics().isEmpty() || policy.criticalQuestionCount() != 1
                || ordinary + policy.criticalQuestionCount() != policy.totalQuestions()
                || policy.durationMinutes() < 1 || policy.passingCorrectAnswers() < 1
                || policy.passingCorrectAnswers() > policy.totalQuestions()
                || !policy.validFrom().isBefore(policy.validUntilExclusive())) {
            throw new IllegalStateException("Invalid official matrix policy");
        }
        var codes = new java.util.HashSet<String>();
        var chapters = new java.util.HashSet<Integer>();
        for (var topic : policy.topics()) {
            if (topic.code() == null || topic.code().isBlank() || !codes.add(topic.code())
                    || topic.questionCount() < 1 || topic.sourceChapterIds().isEmpty()) {
                throw new IllegalStateException("Invalid official matrix topic");
            }
            for (Integer chapter : topic.sourceChapterIds()) {
                if (chapter == null || chapter < 1 || chapter > 6 || !chapters.add(chapter)) {
                    throw new IllegalStateException("Ambiguous official matrix chapter");
                }
            }
        }
    }

    private ExaminationException invalid(String message) {
        return new ExaminationException(HttpStatus.BAD_REQUEST, "MATRIX_INVALID", message);
    }
}
