# Examination — contract hiện hành

## Origin và route

Tất cả path bên dưới là path trực tiếp trên Examination8088. Qua Gateway8888 thay tiền tố /api/v1 bằng /api/examination/v1. Ví dụ GET /api/v1/student/exams → GET /api/examination/v1/student/exams. Không dùng alias /api/v1/exam-sessions tại Gateway vì thuộc Practice.

API trả DTO trực tiếp, không bọc success/data chung như Identity. List admin version trả Spring Page, list cấu hình có PageResponse, student list có items/totalElements/totalPages riêng. Không ép một parser pagination cho mọi controller.

## Chủ thể và quyền

Bearer JWT có sub UUID; không nhận userId do frontend tự chọn. ROLE_STUDENT cho student catalog/session. Admin dùng @examAdminAccess: bắt buộc ROLE_ADMIN và permission module PERM_exams:read/create/update/delete hoặc legacy PERM_exam:action, PERM_exam_version:action, PERM_exam_structure:action, PERM_exam_rule:action, PERM_exam_criteria:action. Publish/archive map module update, clone map create. Không suy ra ROLE_ADMIN đơn lẻ đủ mọi quyền.

Phiên không tồn tại404, phiên người khác403ACCESS_DENIED. Result history detail của dts-result có thể404 cho người khác; đó không thay contract source Examination. Không mở đáp án qua query/header role giả. /actuator/** và OpenAPI/Swagger có ngoại lệ security theo source.

## Lỗi và trace

```json
{"status":409,"error":"Conflict","message":"Reload the persisted session before retrying an older save","traceId":"request-correlation-id","code":"VERSION_CONFLICT"}
```

ErrorResponse có status,error,message,traceId,code. Header X-Request-ID, Cache-Control:no-store;401 thêm WWW-Authenticate:Bearer. Validation JSON/parameter400BAD_REQUEST; lỗi nghiệp vụ có code riêng; not found404NOT_FOUND hoặc mã source cụ thể; exception ngoài mapping500INTERNAL_ERROR. Frontend ưu tiên HTTP+code, không parse message để quyết định nghiệp vụ.

| Code / HTTP | Cách xử lý client |
| --- | --- |
| UNAUTHENTICATED /401 | Đăng nhập/refresh theo Identity |
| ACCESS_DENIED /403 | Không được truy cập thao tác/phiên |
| LICENSE_CLASS_REQUIRED /409 | Chọn GPLX tại Identity |
| LICENSE_CLASS_MISMATCH /403 | Đồng bộ hạng đang chọn, không giả query |
| VERSION_REQUIRED /400 | Gửi version session hiện tại |
| VERSION_CONFLICT /409 | Reload persisted session rồi xác nhận ý định lưu |
| ANSWER_INVALID /400 | Kiểm loại answer và option UUID của paper |
| SESSION_CLOSED /409 | Dừng lưu đáp án, đọc trạng thái/kết quả |
| RESULT_NOT_PUBLISHED /409 | Chờ release; không dựng grade ở browser |
| VERSION_NOT_READY /409 | Cấu hình/snapshot publish cần admin rà |
| SESSION_SNAPSHOT_MISSING/INVALID /409 | Giữ nguyên lịch sử, báo admin rà nguồn |

SessionNoStoreFilter áp dụng /api/v1/exam-sessions, gồm progress/pause/resume. DTO khác dùng serverTime ở student catalog, serverNow ở session; không đổi tên field để đồng nhất trong client. Timestamp Instant là ISO8601 có timezone; timer dùng server time, không dùng thời gian máy người dùng làm deadline.

Nguồn: [controllers](../../src/main/java/dts/com/examination/api/controller), [GlobalExceptionHandler](../../src/main/java/dts/com/examination/application/exception/GlobalExceptionHandler.java), [security](../../src/main/java/dts/com/examination/config/security).
