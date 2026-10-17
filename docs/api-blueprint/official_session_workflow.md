# API Blueprint: Phiên thi chính thức và câu trả lời

ROLE_STUDENT, chủ thể JWT, Cache-Control:no-store. Path trực tiếp /api/v1/exam-sessions; Gateway phải dùng /api/examination/v1/exam-sessions.

| Method | Path sau base | Ý nghĩa / thành công |
| --- | --- | --- |
| POST | base | Start201 hoặc reuse200 ExamSessionResponse |
| GET | /{sessionId} |200 detail safe, version/currentPosition/marked/serverNow |
| GET | /{sessionId}/paper |200 ExamPaperResponse của active IN_PROGRESS/READY |
| GET | /{sessionId}/progress |200 SessionProgressResponse |
| POST | /{sessionId}/answers |200 SaveAnswersResponse |
| PATCH | /{sessionId}/questions/{questionId}/mark |200 SessionProgressResponse |
| PATCH | /{sessionId}/navigation |200 SessionProgressResponse |
| PATCH | /{sessionId}/pause |200 PauseSessionResponse |
| PATCH | /{sessionId}/resume |200 ResumeSessionResponse |
| POST | /{sessionId}/violations |200 SessionProgressResponse |
| POST | /{sessionId}/submit |200 SubmitExamResponse |

## Start / phục hồi

```json
{"examId":"00000000-0000-0000-0000-000000000001","examVersionId":"00000000-0000-0000-0000-000000000002","clientInfo":{"platform":"web"}}
```

UUID minh họa cần thay bằng ID server. Ít nhất examId hoặc examVersionId, nếu cả hai phải cùng parent/version. Start kiểm profile và policy, lock nguồn, không tự cấp attempt mới khi có active hợp lệ. Response có id/sessionId (alias tương thích),examId,examVersionId,attemptNo,status,startedAt,expiredAt,durationSeconds,examRule,version,resumedExisting,serverNow. Không lấy sessionToken hay officialPaper riêng để lưu frontend.

Paper/options đã đóng băng, có position/selectedAnswer; không trả correct answer. Dùng field DTO thực trong [dto reference](dto_reference.md), không tự remap option bằng index sau shuffle. Paper khi SUBMITTED/EXPIRED409SESSION_CLOSED; xem result/review theo release.

## Lưu câu trả lời

```json
{"version":0,"answers":[{"questionId":"00000000-0000-0000-0000-000000000003","selectedAnswer":{"type":"single_choice","value":"00000000-0000-0000-0000-000000000004"}}]}
```

version lấy từ session/progress/save gần nhất, không cố định0. Batch không rỗng, không question trùng, chỉ câu của paper được phép truy cập. SINGLE_CHOICE dùng single_choice, TRUE_FALSE chấp nhận true_false hoặc single_choice theo normalize; MULTIPLE_CHOICE dùng multiple_choice với value là chuỗi UUID phân tách dấu phẩy, không JSON array. Không nhận option của câu khác, UUID trùng, field dư ngoài type/value. value null hoặc chuỗi rỗng xóa đáp án, nhưng selectedAnswer object không được null theo Bean Validation.

Save tăng version; frontend xếp hàng mutation và cập nhật version mới. VERSION_CONFLICT409 phải reload persisted state, không gửi lại bản cũ với version cộng tay. Map đáp án không phải nơi gửi điểm/isCorrect. Tổng saved/answered/remaining xem DTO; không giả mọi mutation trả cùng response.

## Mark / navigation

Mark body {"version":3,"marked":true}. Navigation body {"version":4,"targetPosition":2}, position bắt đầu1. Cùng SessionActionRequest nhưng chỉ field phù hợp được service sử dụng. Mark không tự ghi đáp án. FREE cho truy cập tự do; SEQUENTIAL không nhảy qua bước tiến và review policy hạn chế quay lại. allowSkip=false chặn chuyển khỏi câu chưa trả lời. Progress trả answeredQuestions,totalQuestions,remainingSeconds,version,currentPosition,status,serverNow,markedQuestionIds.

## Pause / resume / vi phạm

Pause/resume không có version body trong controller. READY là paused, remaining được lưu server; không tự đổi thành PAUSED string. Giới hạn pause count/duration/resume timeout và end của exam theo frozen rule. Hết hạn resume409SESSION_NOT_RESUMABLE; trả lời trong pause/qua deadline409SESSION_CLOSED.

Violations body {"eventId":"00000000-0000-0000-0000-000000000005","eventType":"TAB_SWITCH"}, hoặc FULLSCREEN_EXIT. EventId duy nhất để retry, event lặp không cộng lại. TAB_SWITCH chỉ đếm khi preventTabSwitch, vượt max làm failure; fullscreen exit khi requireFullscreen làm failure. Giới hạn log1000 event, không dùng route như API tùy ý sửa grade.

## Nộp

POST /{sessionId}/submit, header Idempotency-Key là chuỗi1..100 ký tự [A-Za-z0-9._:-]. Không body grade. Giữ cùng key khi retry cùng nộp; key khác đã nộp có thể409IDEMPOTENCY_CONFLICT. Response sessionId,status,submittedAt,version,resultPublished; trường summary của DTO không bảo đảm có giá trị cho official submit. Đọc /result sau đó theo resultPublished.

Hết answer deadline chỉ nộp frozen answers trong grace, không ghi thêm đáp án. autoSubmit xử lý theo cutoff và exam end; allowSkip=false đòi đủ câu khi manual submit. Lỗi owner403, missing404; snapshot thiếu/không hợp lệ409 cần rà hành chính, không tạo paper ngẫu nhiên thay lịch sử cũ.
