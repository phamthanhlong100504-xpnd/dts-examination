package dts.com.examination.application.service;

import dts.com.examination.api.form.CreateExamRuleRequest;
import dts.com.examination.api.form.UpdateExamRuleRequest;
import dts.com.examination.api.response.ExamRuleResponse;
import dts.com.examination.api.response.PageResponse;
import dts.com.examination.application.exception.BusinessException;
import dts.com.examination.application.exception.ResourceNotFoundException;
import dts.com.examination.domain.entity.ExamRule;
import dts.com.examination.domain.repository.ExamRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExamRuleService {

    private final ExamRuleRepository examRuleRepository;

    @Transactional
    public ExamRuleResponse createRule(CreateExamRuleRequest request) {
        validateCrossFieldLogic(
                request.getAllowRetry(), request.getMaxRetry(),
                request.getAllowPause(), request.getAllowResume(),
                request.getShuffleQuestionsAcrossSections(), request.getShuffleQuestionsWithinSection(), request.getShuffleSections(),
                request.getPreventTabSwitch(), request.getMaxTabSwitchCount()
        );

        if (examRuleRepository.existsByTitleAndDeletedAtIsNull(request.getTitle())) {
            throw new BusinessException("Title already exists");
        }

        ExamRule rule = ExamRule.builder()
                .id(UUID.randomUUID())
                .title(request.getTitle())
                .allowRetry(request.getAllowRetry())
                .maxRetry(request.getMaxRetry())
                .retryIntervalSeconds(request.getRetryIntervalSeconds())
                .durationSeconds(request.getDurationSeconds())
                .gracePeriodSeconds(request.getGracePeriodSeconds())
                .autoSubmit(request.getAutoSubmit())
                .navigationMode(request.getNavigationMode())
                .allowSkip(request.getAllowSkip())
                .reviewMode(request.getReviewMode())
                .allowPause(request.getAllowPause())
                .maxPauseCount(request.getMaxPauseCount())
                .maxPauseDurationSeconds(request.getMaxPauseDurationSeconds())
                .allowResume(request.getAllowResume())
                .resumeTimeoutSeconds(request.getResumeTimeoutSeconds())
                .shuffleSections(request.getShuffleSections())
                .shuffleQuestionsWithinSection(request.getShuffleQuestionsWithinSection())
                .shuffleQuestionsAcrossSections(request.getShuffleQuestionsAcrossSections())
                .shuffleOptions(request.getShuffleOptions())
                .resultReleaseMode(request.getResultReleaseMode())
                .showAnswerAfterSubmit(request.getShowAnswerAfterSubmit())
                .showExplanationAfterSubmit(request.getShowExplanationAfterSubmit())
                .showQuestionScoreAfterSubmit(request.getShowQuestionScoreAfterSubmit())
                .requireFullscreen(request.getRequireFullscreen())
                .preventTabSwitch(request.getPreventTabSwitch())
                .maxTabSwitchCount(request.getMaxTabSwitchCount())
                .timeZone(request.getTimeZone())
                .metadata(request.getMetadata())
                .status("ACTIVE")
                .build();

        ExamRule savedRule = examRuleRepository.save(rule);
        return mapToResponse(savedRule);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExamRuleResponse> getRuleList(int page, int size, String keyword, String status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ExamRule> pageResult = examRuleRepository.searchRules(
                StringUtils.hasText(keyword) ? keyword : null,
                StringUtils.hasText(status) ? status : null,
                pageable
        );

        return PageResponse.<ExamRuleResponse>builder()
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .items(pageResult.getContent().stream().map(this::mapToResponse).collect(Collectors.toList()))
                .build();
    }

    @Transactional(readOnly = true)
    public ExamRuleResponse getRuleDetail(UUID ruleId) {
        ExamRule rule = examRuleRepository.findByIdActive(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam rule not found"));
        return mapToResponse(rule);
    }

    @Transactional
    public ExamRuleResponse updateRule(UUID ruleId, UpdateExamRuleRequest request) {
        ExamRule rule = examRuleRepository.findByIdActive(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam rule not found"));

        if (!"ACTIVE".equals(rule.getStatus())) {
            throw new BusinessException("Only ACTIVE rules can be updated");
        }

        // TODO: Check if rule is used by PUBLISHED ExamVersion once ExamVersion is implemented

        // Resolve effective values (merge request with existing)
        boolean allowRetry = request.getAllowRetry() != null ? request.getAllowRetry() : rule.isAllowRetry();
        int maxRetry = request.getMaxRetry() != null ? request.getMaxRetry() : rule.getMaxRetry();
        boolean allowPause = request.getAllowPause() != null ? request.getAllowPause() : rule.isAllowPause();
        boolean allowResume = request.getAllowResume() != null ? request.getAllowResume() : rule.isAllowResume();
        boolean shuffleAcross = request.getShuffleQuestionsAcrossSections() != null ? request.getShuffleQuestionsAcrossSections() : rule.isShuffleQuestionsAcrossSections();
        boolean shuffleWithin = request.getShuffleQuestionsWithinSection() != null ? request.getShuffleQuestionsWithinSection() : rule.isShuffleQuestionsWithinSection();
        boolean shuffleSections = request.getShuffleSections() != null ? request.getShuffleSections() : rule.isShuffleSections();
        boolean preventTabSwitch = request.getPreventTabSwitch() != null ? request.getPreventTabSwitch() : rule.isPreventTabSwitch();
        int maxTabSwitchCount = request.getMaxTabSwitchCount() != null ? request.getMaxTabSwitchCount() : rule.getMaxTabSwitchCount();

        validateCrossFieldLogic(allowRetry, maxRetry, allowPause, allowResume, shuffleAcross, shuffleWithin, shuffleSections, preventTabSwitch, maxTabSwitchCount);

        if (StringUtils.hasText(request.getTitle()) && !request.getTitle().equals(rule.getTitle())) {
            if (examRuleRepository.existsByTitleAndDeletedAtIsNull(request.getTitle())) {
                throw new BusinessException("Title already exists");
            }
            rule.setTitle(request.getTitle());
        }

        if (request.getAllowRetry() != null) rule.setAllowRetry(request.getAllowRetry());
        if (request.getMaxRetry() != null) rule.setMaxRetry(request.getMaxRetry());
        if (request.getRetryIntervalSeconds() != null) rule.setRetryIntervalSeconds(request.getRetryIntervalSeconds());
        if (request.getDurationSeconds() != null) rule.setDurationSeconds(request.getDurationSeconds());
        if (request.getGracePeriodSeconds() != null) rule.setGracePeriodSeconds(request.getGracePeriodSeconds());
        if (request.getAutoSubmit() != null) rule.setAutoSubmit(request.getAutoSubmit());
        if (request.getNavigationMode() != null) rule.setNavigationMode(request.getNavigationMode());
        if (request.getAllowSkip() != null) rule.setAllowSkip(request.getAllowSkip());
        if (request.getReviewMode() != null) rule.setReviewMode(request.getReviewMode());
        if (request.getAllowPause() != null) rule.setAllowPause(request.getAllowPause());
        if (request.getMaxPauseCount() != null) rule.setMaxPauseCount(request.getMaxPauseCount());
        if (request.getMaxPauseDurationSeconds() != null) rule.setMaxPauseDurationSeconds(request.getMaxPauseDurationSeconds());
        if (request.getAllowResume() != null) rule.setAllowResume(request.getAllowResume());
        if (request.getResumeTimeoutSeconds() != null) rule.setResumeTimeoutSeconds(request.getResumeTimeoutSeconds());
        if (request.getShuffleSections() != null) rule.setShuffleSections(request.getShuffleSections());
        if (request.getShuffleQuestionsWithinSection() != null) rule.setShuffleQuestionsWithinSection(request.getShuffleQuestionsWithinSection());
        if (request.getShuffleQuestionsAcrossSections() != null) rule.setShuffleQuestionsAcrossSections(request.getShuffleQuestionsAcrossSections());
        if (request.getShuffleOptions() != null) rule.setShuffleOptions(request.getShuffleOptions());
        if (request.getResultReleaseMode() != null) rule.setResultReleaseMode(request.getResultReleaseMode());
        if (request.getShowAnswerAfterSubmit() != null) rule.setShowAnswerAfterSubmit(request.getShowAnswerAfterSubmit());
        if (request.getShowExplanationAfterSubmit() != null) rule.setShowExplanationAfterSubmit(request.getShowExplanationAfterSubmit());
        if (request.getShowQuestionScoreAfterSubmit() != null) rule.setShowQuestionScoreAfterSubmit(request.getShowQuestionScoreAfterSubmit());
        if (request.getRequireFullscreen() != null) rule.setRequireFullscreen(request.getRequireFullscreen());
        if (request.getPreventTabSwitch() != null) rule.setPreventTabSwitch(request.getPreventTabSwitch());
        if (request.getMaxTabSwitchCount() != null) rule.setMaxTabSwitchCount(request.getMaxTabSwitchCount());
        if (request.getTimeZone() != null) rule.setTimeZone(request.getTimeZone());
        if (request.getMetadata() != null) rule.setMetadata(request.getMetadata());

        return mapToResponse(examRuleRepository.save(rule));
    }

    @Transactional
    public void deleteRule(UUID ruleId) {
        ExamRule rule = examRuleRepository.findByIdActive(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam rule not found"));

        // TODO: Check if rule is used by ANY ExamVersion once ExamVersion is implemented

        rule.setDeletedAt(Instant.now());
        examRuleRepository.save(rule);
    }

    private void validateCrossFieldLogic(Boolean allowRetry, Integer maxRetry,
                                         Boolean allowPause, Boolean allowResume,
                                         Boolean shuffleAcross, Boolean shuffleWithin, Boolean shuffleSections,
                                         Boolean preventTabSwitch, Integer maxTabSwitchCount) {
        if (allowRetry != null && !allowRetry && maxRetry != null && maxRetry != 0) {
            throw new BusinessException("If allowRetry is false, maxRetry must be 0");
        }
        if (allowPause != null && allowPause && allowResume != null && !allowResume) {
            throw new BusinessException("If allowPause is true, allowResume must be true");
        }
        if (shuffleAcross != null && shuffleAcross) {
            if (shuffleWithin != null && shuffleWithin) {
                throw new BusinessException("If shuffleQuestionsAcrossSections is true, shuffleQuestionsWithinSection must be false");
            }
            if (shuffleSections != null && shuffleSections) {
                throw new BusinessException("If shuffleQuestionsAcrossSections is true, shuffleSections must be false");
            }
        }
        if (preventTabSwitch != null && !preventTabSwitch && maxTabSwitchCount != null && maxTabSwitchCount != 0) {
            throw new BusinessException("If preventTabSwitch is false, maxTabSwitchCount must be 0");
        }
    }

    private ExamRuleResponse mapToResponse(ExamRule rule) {
        return ExamRuleResponse.builder()
                .id(rule.getId())
                .title(rule.getTitle())
                .allowRetry(rule.isAllowRetry())
                .maxRetry(rule.getMaxRetry())
                .retryIntervalSeconds(rule.getRetryIntervalSeconds())
                .durationSeconds(rule.getDurationSeconds())
                .gracePeriodSeconds(rule.getGracePeriodSeconds())
                .autoSubmit(rule.isAutoSubmit())
                .navigationMode(rule.getNavigationMode())
                .allowSkip(rule.isAllowSkip())
                .reviewMode(rule.getReviewMode())
                .allowPause(rule.isAllowPause())
                .maxPauseCount(rule.getMaxPauseCount())
                .maxPauseDurationSeconds(rule.getMaxPauseDurationSeconds())
                .allowResume(rule.isAllowResume())
                .resumeTimeoutSeconds(rule.getResumeTimeoutSeconds())
                .shuffleSections(rule.isShuffleSections())
                .shuffleQuestionsWithinSection(rule.isShuffleQuestionsWithinSection())
                .shuffleQuestionsAcrossSections(rule.isShuffleQuestionsAcrossSections())
                .shuffleOptions(rule.isShuffleOptions())
                .resultReleaseMode(rule.getResultReleaseMode())
                .showAnswerAfterSubmit(rule.isShowAnswerAfterSubmit())
                .showExplanationAfterSubmit(rule.isShowExplanationAfterSubmit())
                .showQuestionScoreAfterSubmit(rule.isShowQuestionScoreAfterSubmit())
                .requireFullscreen(rule.isRequireFullscreen())
                .preventTabSwitch(rule.isPreventTabSwitch())
                .maxTabSwitchCount(rule.getMaxTabSwitchCount())
                .timeZone(rule.getTimeZone())
                .status(rule.getStatus())
                .metadata(rule.getMetadata())
                .createdAt(rule.getCreatedAt())
                .createdBy(rule.getCreatedBy())
                .updatedAt(rule.getUpdatedAt())
                .updatedBy(rule.getUpdatedBy())
                .build();
    }
}
