# API Blueprint: Danh sách bài thi của học viên

## GET /api/v1/student/exams

ROLE_STUDENT, Bearer forward sang Identity /api/v1/users/me, user ACTIVE và selectedLicenseClass hợp lệ. Query tab mặc định OPEN, hỗ trợ OPEN/UPCOMING/PARTICIPATED đúng tên; page mặc định0,0..1000000; size20,1..100. licenseClass optional, nếu có trim/uppercase và phải bằng profile.

200 StudentExamListResponse:

```json
{"selectedLicenseClass":"A1","tab":"OPEN","page":0,"size":20,"totalElements":0,"totalPages":0,"items":[],"serverTime":"2026-10-18T00:00:00Z"}
```

Ví dụ shape rỗng, không phải kết quả runtime. Item StudentExamSummaryResponse có examId,examVersionId,title,licenseClass,examType,bankVersion,startedAt,endedAt,questionCount,durationSeconds,maxAttempts,usedAttempts,remainingAttempts,availability,participationStatus,activeSessionId,canStart,canResume,reasonCode,retryAvailableAt.

title lấy version title. maxAttempts/remainingAttempts nullable khi không giới hạn; không chuyển null thành0. availability OPEN/UPCOMING/CLOSED; participation và reason là trạng thái server suy ra. Dùng canStart/canResume để bật hành động, không chỉ so thời gian ở browser. activeSessionId dùng để tiếp tục đúng phiên, không tự tạo lại.

PARTICIPATED giữ lịch sử của user kể cả parent hidden khi có nguồn phù hợp. OPEN/UPCOMING yêu cầu bài công khai, version published, profile/time phù hợp. Source không tự trả bài hạng khác chỉ vì đổi query.

## GET /api/v1/student/exams/{examId}

Optional examVersionId.200 StudentExamDetailResponse có **exam**,instructions,rules,grading,**serverTime**. exam là summary trên, không phải field summary. grading gồm totalScore,passScore,gradingMethod,passingCorrectAnswers,criticalQuestionCount; không có answer key. rules là StudentExamRulesResponse chỉ các flag tham gia/hiển thị.

Không tìm thấy candidate404. Profile chưa chọn GPLX409LICENSE_CLASS_REQUIRED; chọn query khác403LICENSE_CLASS_MISMATCH; Identity thiếu/không hợp lệ502IDENTITY_INVALID_RESPONSE theo reader, lỗi upstream xem fallback. Không chọn examVersionId chỉ dựa bản mới nhất phía frontend; dùng ID server đã trả.

Luồng UI: đọc profile→list→detail→POST exam-sessions với examId/examVersionId. Khi canResume dùng activeSessionId đọc detail/paper, start có thể trả resumedExisting=true và HTTP200. Phiên mới201. [Session workflow](official_session_workflow.md).

Nguồn: [StudentExamController](../../src/main/java/dts/com/examination/api/controller/StudentExamController.java), [StudentExamReadService](../../src/main/java/dts/com/examination/application/service/StudentExamReadService.java), [ExamParticipationPolicy](../../src/main/java/dts/com/examination/application/service/ExamParticipationPolicy.java).
