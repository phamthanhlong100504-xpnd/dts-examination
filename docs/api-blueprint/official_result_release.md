# API Blueprint: Kết quả, review và công bố

## GET /api/v1/exam-sessions/{sessionId}/result

ROLE_STUDENT và đúng owner,200 ExamResultResponse khi SUBMITTED. Chưa nộp409SESSION_NOT_SUBMITTED. Source người khác403ACCESS_DENIED.

Trước công bố trả sessionId,status,submittedAt,resultPublished=false,resultReleaseMode,releaseAt; summary/answers không có nội dung grade. Tùy Jackson field null có thể được serialize null, không dựa vào việc key tồn tại để coi là đã công bố. Sau release resultPublished=true,summary và answers theo flag.

Summary gồm score,result PASS/FAIL,totalQuestions,correctQuestions,answeredQuestions,wrongQuestions (đã trả lời sai),unansweredQuestions. Answer item questionId,display(position/sectionCode/sectionTitle/marked),selectedAnswer,isCorrect nếu showAnswerAfterSubmit,score nếu showQuestionScoreAfterSubmit. Không dựng isCorrect từ option order hay server metadata.

## GET /api/v1/exam-sessions/{sessionId}/review

Chưa submitted/released409RESULT_NOT_PUBLISHED. reviewMode NONE409REVIEW_NOT_ALLOWED. CURRENT_SECTION chỉ section của currentPosition đã lưu, ALL toàn paper. Response có sessionId,examId,examVersionId,finalResult PASS/FAIL,score,correctCount,totalQuestions,questions.

Question review gồm id,content,type,mediaFileIds,userAnswer,isCorrect,options(id,content,isCorrect),explanation. isCorrect bị ẩn nếu showAnswerAfterSubmit=false; explanation chỉ khi showExplanationAfterSubmit. Không coi resultAvailable là chắc chắn có quyền review; xử lý409 theo source.

## POST /api/v1/exam-sessions/{sessionId}/release-result

Admin có exam update permission, không dùng student role. Chỉ SUBMITTED và mode MANUAL, thành công204 body rỗng. Retry204 không thay marker, không chấm lại. Không parse JSON khi204. Source khác trạng thái/mode409RESULT_RELEASE_NOT_ALLOWED.

| resultReleaseMode | Thời điểm |
| --- | --- |
| IMMEDIATE / AFTER_SUBMIT | Sau submitted |
| AFTER_EXAM_END | End time từ frozen session |
| MANUAL | Admin release marker |

## Đưa vào lịch sử Result

Finish lưu grade snapshot và resultEvent stable ID trong session metadata cùng transaction. Worker chỉ gửi learning-results khi released, chờ Kafka ack, retry nếu lỗi. Đây là at-least-once, consumer Result giữ idempotency. Không yêu cầu học viên nộp lại khi chờ Kafka. Chưa release phải vắng khỏi public history.

Event version2 có activityType OFFICIAL_EXAM,sourceType EXAM_SESSION,sourceId=sessionId,targetType EXAM,targetId=examId,displayTitle,licenseClass,correctCount,questionCount,attemptNo,startedAt/completedAt,resultAvailable/reviewAvailable. Event result dùng PASSED/FAILED, source summary dùng PASS/FAIL; không thay string của hai contract cho nhau.

Title source student list là version title; title lịch sử là parent title chụp lúc start. Historical event chưa từng chụp title giữ null thay vì lấy tên hiện tại. Frontend dùng fallback hiển thị hợp lý khi null và giữ sourceId để điều hướng về đúng /exam-sessions/{id}/result hoặc /review.

Admin ẩn parent không xóa frozen session/result đã thuộc người dùng. Result history ownership404 theo repo Result; source Examination owner403. [Contract notes](contract_notes.md).
