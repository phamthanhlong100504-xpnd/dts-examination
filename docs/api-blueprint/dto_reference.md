# Examination — DTO reference

Field lấy từ source hiện có, không phải mẫu payload đầy đủ hay schema validation thay OpenAPI. Chỉ DTO public; model FrozenOfficialSession/PublishedExamSnapshot không phải response frontend. Xem trang nghiệp vụ để biết điều kiện visibility và null.

## AnswerItemRequest

Nguồn: [AnswerItemRequest](../../src/main/java/dts/com/examination/api/form/AnswerItemRequest.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| questionId | `UUID` |
| selectedAnswer | `Map<String, Object>` |

## ChangeExamStatusRequest

Nguồn: [ChangeExamStatusRequest](../../src/main/java/dts/com/examination/api/form/ChangeExamStatusRequest.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| status | `String` |

## CreateExamCriteriaRequest

Nguồn: [CreateExamCriteriaRequest](../../src/main/java/dts/com/examination/api/form/CreateExamCriteriaRequest.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| title | `String` |
| criteria | `CriteriaConfig` |
| metadata | `Map<String, Object>` |

## CreateExamRequest

Nguồn: [CreateExamRequest](../../src/main/java/dts/com/examination/api/form/CreateExamRequest.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| title | `String` |
| thumbnailId | `UUID` |
| metadata | `Map<String, Object>` |

## CreateExamRuleRequest

Nguồn: [CreateExamRuleRequest](../../src/main/java/dts/com/examination/api/form/CreateExamRuleRequest.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| title | `String` |
| allowRetry | `Boolean` |
| maxRetry | `Integer` |
| retryIntervalSeconds | `Integer` |
| durationSeconds | `Integer` |
| gracePeriodSeconds | `Integer` |
| autoSubmit | `Boolean` |
| navigationMode | `String` |
| allowSkip | `Boolean` |
| reviewMode | `String` |
| allowPause | `Boolean` |
| maxPauseCount | `Integer` |
| maxPauseDurationSeconds | `Integer` |
| allowResume | `Boolean` |
| resumeTimeoutSeconds | `Integer` |
| shuffleSections | `Boolean` |
| shuffleQuestionsWithinSection | `Boolean` |
| shuffleQuestionsAcrossSections | `Boolean` |
| shuffleOptions | `Boolean` |
| resultReleaseMode | `String` |
| showAnswerAfterSubmit | `Boolean` |
| showExplanationAfterSubmit | `Boolean` |
| showQuestionScoreAfterSubmit | `Boolean` |
| requireFullscreen | `Boolean` |
| preventTabSwitch | `Boolean` |
| maxTabSwitchCount | `Integer` |
| timeZone | `String` |
| metadata | `Map<String, Object>` |

## CreateExamStructureRequest

Nguồn: [CreateExamStructureRequest](../../src/main/java/dts/com/examination/api/form/CreateExamStructureRequest.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| title | `String` |
| sections | `List<SectionDto>` |
| metadata | `Map<String, Object>` |

## CreateExamVersionRequest

Nguồn: [CreateExamVersionRequest](../../src/main/java/dts/com/examination/api/form/CreateExamVersionRequest.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| title | `String` |
| examType | `String` |
| thumbnailId | `UUID` |
| examStructureId | `UUID` |
| examRuleId | `UUID` |
| examCriteriaId | `UUID` |
| contentType | `String` |
| contentId | `UUID` |
| startedAt | `Instant` |
| endedAt | `Instant` |
| metadata | `Map<String, Object>` |
| licenseClass | `String` |
| bankVersion | `String` |
| selectionMode | `ExamSelectionMode` |
| manualQuestionIds | `List< UUID>` |

## SaveAnswersRequest

Nguồn: [SaveAnswersRequest](../../src/main/java/dts/com/examination/api/form/SaveAnswersRequest.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| answers | `List<AnswerItemRequest>` |
| version | `Integer` |

## SessionActionRequest

Nguồn: [SessionActionRequest](../../src/main/java/dts/com/examination/api/form/SessionActionRequest.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| version | `Integer` |
| targetPosition | `Integer` |
| marked | `Boolean` |

## SessionViolationRequest

Nguồn: [SessionViolationRequest](../../src/main/java/dts/com/examination/api/form/SessionViolationRequest.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| eventId | `UUID` |
| eventType | `String` |

## StartExamSessionRequest

Nguồn: [StartExamSessionRequest](../../src/main/java/dts/com/examination/api/form/StartExamSessionRequest.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| examVersionId | `UUID` |
| examId | `UUID` |
| clientInfo | `Map<String, Object>` |

## UpdateExamCriteriaRequest

Nguồn: [UpdateExamCriteriaRequest](../../src/main/java/dts/com/examination/api/form/UpdateExamCriteriaRequest.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| title | `String` |
| criteria | `CriteriaConfig` |
| metadata | `Map<String, Object>` |

## UpdateExamRequest

Nguồn: [UpdateExamRequest](../../src/main/java/dts/com/examination/api/form/UpdateExamRequest.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| title | `String` |
| thumbnailId | `UUID` |
| metadata | `Map<String, Object>` |

## UpdateExamRuleRequest

Nguồn: [UpdateExamRuleRequest](../../src/main/java/dts/com/examination/api/form/UpdateExamRuleRequest.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| title | `String` |
| allowRetry | `Boolean` |
| maxRetry | `Integer` |
| retryIntervalSeconds | `Integer` |
| durationSeconds | `Integer` |
| gracePeriodSeconds | `Integer` |
| autoSubmit | `Boolean` |
| navigationMode | `String` |
| allowSkip | `Boolean` |
| reviewMode | `String` |
| allowPause | `Boolean` |
| maxPauseCount | `Integer` |
| maxPauseDurationSeconds | `Integer` |
| allowResume | `Boolean` |
| resumeTimeoutSeconds | `Integer` |
| shuffleSections | `Boolean` |
| shuffleQuestionsWithinSection | `Boolean` |
| shuffleQuestionsAcrossSections | `Boolean` |
| shuffleOptions | `Boolean` |
| resultReleaseMode | `String` |
| showAnswerAfterSubmit | `Boolean` |
| showExplanationAfterSubmit | `Boolean` |
| showQuestionScoreAfterSubmit | `Boolean` |
| requireFullscreen | `Boolean` |
| preventTabSwitch | `Boolean` |
| maxTabSwitchCount | `Integer` |
| timeZone | `String` |
| metadata | `Map<String, Object>` |

## UpdateExamStructureRequest

Nguồn: [UpdateExamStructureRequest](../../src/main/java/dts/com/examination/api/form/UpdateExamStructureRequest.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| title | `String` |
| sections | `List<SectionDto>` |
| status | `String` |
| metadata | `Map<String, Object>` |

## UpdateExamVersionRequest

Nguồn: [UpdateExamVersionRequest](../../src/main/java/dts/com/examination/api/form/UpdateExamVersionRequest.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| title | `String` |
| thumbnailId | `UUID` |
| examStructureId | `UUID` |
| examRuleId | `UUID` |
| examCriteriaId | `UUID` |
| startedAt | `Instant` |
| endedAt | `Instant` |
| metadata | `Map<String, Object>` |
| examType | `String` |
| contentType | `String` |
| contentId | `UUID` |
| licenseClass | `String` |
| bankVersion | `String` |
| selectionMode | `ExamSelectionMode` |
| manualQuestionIds | `List< UUID>` |

## ExamCriteriaResponse

Nguồn: [ExamCriteriaResponse](../../src/main/java/dts/com/examination/api/response/ExamCriteriaResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| id | `UUID` |
| title | `String` |
| status | `String` |
| criteria | `CriteriaConfig` |
| metadata | `Map<String, Object>` |

## ExamPaperResponse

Nguồn: [ExamPaperResponse](../../src/main/java/dts/com/examination/api/response/ExamPaperResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| sessionId | `UUID` |
| status | `String` |
| remainingSeconds | `Long` |
| questions | `List<QuestionPaperResponse>` |
| version | `Integer` |
| currentPosition | `Integer` |
| serverNow | `java.time.Instant` |

## ExamResponse

Nguồn: [ExamResponse](../../src/main/java/dts/com/examination/api/response/ExamResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| id | `UUID` |
| title | `String` |
| thumbnailId | `UUID` |
| status | `String` |
| metadata | `Map<String, Object>` |
| createdAt | `Instant` |
| updatedAt | `Instant` |

## ExamResultDetailResponse

Nguồn: [ExamResultDetailResponse](../../src/main/java/dts/com/examination/api/response/ExamResultDetailResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| questionId | `UUID` |
| display | `Map<String, Object>` |
| selectedAnswer | `Map<String, Object>` |
| isCorrect | `Boolean` |
| score | `BigDecimal` |

## ExamResultResponse

Nguồn: [ExamResultResponse](../../src/main/java/dts/com/examination/api/response/ExamResultResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| sessionId | `UUID` |
| status | `String` |
| submittedAt | `Instant` |
| summary | `ExamResultSummary` |
| answers | `List<ExamResultDetailResponse>` |
| resultPublished | `boolean` |
| resultReleaseMode | `String` |
| releaseAt | `Instant` |

## ExamResultSummary

Nguồn: [ExamResultSummary](../../src/main/java/dts/com/examination/api/response/ExamResultSummary.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| totalQuestions | `Integer` |
| answeredQuestions | `Integer` |
| correctQuestions | `Integer` |
| wrongQuestions | `Integer` |
| unansweredQuestions | `Integer` |
| score | `BigDecimal` |
| result | `String` |

## ExamReviewResponse

Nguồn: [ExamReviewResponse](../../src/main/java/dts/com/examination/api/response/ExamReviewResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| sessionId | `UUID` |
| examId | `UUID` |
| examVersionId | `UUID` |
| finalResult | `String` |
| score | `BigDecimal` |
| correctCount | `Integer` |
| totalQuestions | `Integer` |
| questions | `List<QuestionReviewResponse>` |

## ExamRuleResponse

Nguồn: [ExamRuleResponse](../../src/main/java/dts/com/examination/api/response/ExamRuleResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| id | `UUID` |
| title | `String` |
| allowRetry | `boolean` |
| maxRetry | `int` |
| retryIntervalSeconds | `int` |
| durationSeconds | `int` |
| gracePeriodSeconds | `int` |
| autoSubmit | `boolean` |
| navigationMode | `String` |
| allowSkip | `boolean` |
| reviewMode | `String` |
| allowPause | `boolean` |
| maxPauseCount | `int` |
| maxPauseDurationSeconds | `int` |
| allowResume | `boolean` |
| resumeTimeoutSeconds | `int` |
| shuffleSections | `boolean` |
| shuffleQuestionsWithinSection | `boolean` |
| shuffleQuestionsAcrossSections | `boolean` |
| shuffleOptions | `boolean` |
| resultReleaseMode | `String` |
| showAnswerAfterSubmit | `boolean` |
| showExplanationAfterSubmit | `boolean` |
| showQuestionScoreAfterSubmit | `boolean` |
| requireFullscreen | `boolean` |
| preventTabSwitch | `boolean` |
| maxTabSwitchCount | `int` |
| timeZone | `String` |
| status | `String` |
| metadata | `Map<String, Object>` |
| createdAt | `Instant` |
| createdBy | `UUID` |
| updatedAt | `Instant` |
| updatedBy | `UUID` |

## ExamSessionDetailResponse

Nguồn: [ExamSessionDetailResponse](../../src/main/java/dts/com/examination/api/response/ExamSessionDetailResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| sessionId | `UUID` |
| examVersionId | `UUID` |
| attemptNo | `Integer` |
| status | `String` |
| startedAt | `Instant` |
| expiredAt | `Instant` |
| durationSeconds | `Integer` |
| remainingSeconds | `Long` |
| answeredQuestions | `Long` |
| totalQuestions | `Long` |
| examRule | `ExamRuleResponse` |
| examId | `UUID` |
| version | `Integer` |
| currentPosition | `Integer` |
| markedQuestionIds | `java.util.List<UUID>` |
| serverNow | `Instant` |

## ExamSessionResponse

Nguồn: [ExamSessionResponse](../../src/main/java/dts/com/examination/api/response/ExamSessionResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| id | `UUID` |
| sessionId | `UUID` |
| examId | `UUID` |
| examVersionId | `UUID` |
| attemptNo | `Integer` |
| status | `String` |
| startedAt | `Instant` |
| expiredAt | `Instant` |
| durationSeconds | `Integer` |
| examRule | `ExamRuleResponse` |
| version | `Integer` |
| resumedExisting | `boolean` |
| serverNow | `Instant` |

## ExamStructureResponse

Nguồn: [ExamStructureResponse](../../src/main/java/dts/com/examination/api/response/ExamStructureResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| id | `UUID` |
| title | `String` |
| status | `String` |
| sections | `List<SectionDto>` |
| metadata | `Map<String, Object>` |
| createdAt | `OffsetDateTime` |
| updatedAt | `OffsetDateTime` |
| totalQuestions | `Integer` |
| maxScore | `Integer` |

## ExamVersionResponse

Nguồn: [ExamVersionResponse](../../src/main/java/dts/com/examination/api/response/ExamVersionResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| id | `UUID` |
| examId | `UUID` |
| versionNo | `Integer` |
| title | `String` |
| examType | `String` |
| thumbnailId | `UUID` |
| examStructureId | `UUID` |
| examRuleId | `UUID` |
| examCriteriaId | `UUID` |
| contentType | `String` |
| contentId | `UUID` |
| startedAt | `Instant` |
| endedAt | `Instant` |
| status | `String` |
| metadata | `Map<String, Object>` |
| createdAt | `Instant` |
| updatedAt | `Instant` |
| licenseClass | `String` |
| bankVersion | `String` |
| selectionMode | `ExamSelectionMode` |
| manualQuestionIds | `List<UUID>` |
| matrixPolicyId | `String` |
| totalQuestions | `Integer` |
| durationSeconds | `Integer` |
| passingCorrectAnswers | `Integer` |
| criticalQuestionCount | `Integer` |

## OptionPaperResponse

Nguồn: [OptionPaperResponse](../../src/main/java/dts/com/examination/api/response/OptionPaperResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| id | `UUID` |
| text | `String` |

## OptionReviewResponse

Nguồn: [OptionReviewResponse](../../src/main/java/dts/com/examination/api/response/OptionReviewResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| id | `UUID` |
| content | `String` |
| sortOrder | `Integer` |
| isCorrect | `Boolean` |

## PageResponse

Nguồn: [PageResponse](../../src/main/java/dts/com/examination/api/response/PageResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| page | `int` |
| size | `int` |
| totalElements | `long` |
| items | `List<T>` |

## PauseSessionResponse

Nguồn: [PauseSessionResponse](../../src/main/java/dts/com/examination/api/response/PauseSessionResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| sessionId | `UUID` |
| status | `String` |
| pausedAt | `Instant` |
| remainingSeconds | `int` |

## QuestionPaperResponse

Nguồn: [QuestionPaperResponse](../../src/main/java/dts/com/examination/api/response/QuestionPaperResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| questionId | `UUID` |
| display | `Map<String, Object>` |
| content | `String` |
| type | `String` |
| imageUrl | `String` |
| options | `List<OptionPaperResponse>` |
| selectedAnswer | `Map<String, Object>` |
| mediaFileIds | `List<UUID>` |
| isCritical | `Boolean` |

## QuestionReviewResponse

Nguồn: [QuestionReviewResponse](../../src/main/java/dts/com/examination/api/response/QuestionReviewResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| id | `UUID` |
| content | `String` |
| type | `String` |
| mediaFileIds | `List<String>` |
| options | `List<OptionReviewResponse>` |
| userAnswer | `Map<String, Object>` |
| isCorrect | `Boolean` |
| explanation | `String` |

## ResumeSessionResponse

Nguồn: [ResumeSessionResponse](../../src/main/java/dts/com/examination/api/response/ResumeSessionResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| sessionId | `UUID` |
| status | `String` |
| resumedAt | `Instant` |
| expiredAt | `Instant` |
| remainingSeconds | `int` |

## SaveAnswersResponse

Nguồn: [SaveAnswersResponse](../../src/main/java/dts/com/examination/api/response/SaveAnswersResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| savedAt | `Instant` |
| updatedQuestions | `Integer` |
| version | `Integer` |

## SessionHistoryItemResponse

Nguồn: [SessionHistoryItemResponse](../../src/main/java/dts/com/examination/api/response/SessionHistoryItemResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| sessionId | `UUID` |
| examVersionId | `UUID` |
| attemptNo | `Integer` |
| status | `String` |
| startedAt | `Instant` |
| submittedAt | `Instant` |
| summary | `ExamResultSummary` |

## SessionHistoryResponse

Nguồn: [SessionHistoryResponse](../../src/main/java/dts/com/examination/api/response/SessionHistoryResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| page | `int` |
| size | `int` |
| totalElements | `long` |
| totalPages | `int` |
| items | `List<SessionHistoryItemResponse>` |

## SessionProgressResponse

Nguồn: [SessionProgressResponse](../../src/main/java/dts/com/examination/api/response/SessionProgressResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| answeredQuestions | `long` |
| totalQuestions | `long` |
| remainingSeconds | `int` |
| version | `Integer` |
| currentPosition | `Integer` |
| status | `String` |
| serverNow | `java.time.Instant` |
| markedQuestionIds | `java.util.List<java.util.UUID>` |

## StudentExamDetailResponse

Nguồn: [StudentExamDetailResponse](../../src/main/java/dts/com/examination/api/response/StudentExamDetailResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| exam | `StudentExamSummaryResponse` |
| instructions | `String` |
| rules | `StudentExamRulesResponse` |
| grading | `GradingSummary` |
| serverTime | `Instant` |

## StudentExamListResponse

Nguồn: [StudentExamListResponse](../../src/main/java/dts/com/examination/api/response/StudentExamListResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| selectedLicenseClass | `String` |
| tab | `String` |
| page | `int` |
| size | `int` |
| totalElements | `long` |
| totalPages | `int` |
| items | `List<StudentExamSummaryResponse>` |
| serverTime | `Instant` |

## StudentExamRulesResponse

Nguồn: [StudentExamRulesResponse](../../src/main/java/dts/com/examination/api/response/StudentExamRulesResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| allowRetry | `boolean` |
| maxRetry | `int` |
| retryIntervalSeconds | `int` |
| durationSeconds | `int` |
| gracePeriodSeconds | `int` |
| autoSubmit | `boolean` |
| navigationMode | `String` |
| allowSkip | `boolean` |
| reviewMode | `String` |
| allowPause | `boolean` |
| maxPauseCount | `int` |
| maxPauseDurationSeconds | `int` |
| allowResume | `boolean` |
| resumeTimeoutSeconds | `int` |
| shuffleSections | `boolean` |
| shuffleQuestionsWithinSection | `boolean` |
| shuffleQuestionsAcrossSections | `boolean` |
| shuffleOptions | `boolean` |
| resultReleaseMode | `String` |
| showAnswerAfterSubmit | `boolean` |
| showExplanationAfterSubmit | `boolean` |
| showQuestionScoreAfterSubmit | `boolean` |
| requireFullscreen | `boolean` |
| preventTabSwitch | `boolean` |
| maxTabSwitchCount | `int` |
| timeZone | `String` |

## StudentExamSummaryResponse

Nguồn: [StudentExamSummaryResponse](../../src/main/java/dts/com/examination/api/response/StudentExamSummaryResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| examId | `UUID` |
| examVersionId | `UUID` |
| title | `String` |
| licenseClass | `String` |
| examType | `String` |
| bankVersion | `String` |
| startedAt | `Instant` |
| endedAt | `Instant` |
| questionCount | `int` |
| durationSeconds | `int` |
| maxAttempts | `Long` |
| usedAttempts | `long` |
| remainingAttempts | `Long` |
| availability | `String` |
| participationStatus | `String` |
| activeSessionId | `UUID` |
| canStart | `boolean` |
| canResume | `boolean` |
| reasonCode | `String` |
| retryAvailableAt | `Instant` |

## SubmitExamResponse

Nguồn: [SubmitExamResponse](../../src/main/java/dts/com/examination/api/response/SubmitExamResponse.java). Kiểu Java nullable khác primitive; Bean Validation/service vẫn quyết định field bắt buộc và phạm vi hợp lệ.

| Field | Kiểu |
| --- | --- |
| sessionId | `UUID` |
| status | `String` |
| submittedAt | `Instant` |
| summary | `ExamResultSummary` |
| version | `Integer` |
| resultPublished | `boolean` |
