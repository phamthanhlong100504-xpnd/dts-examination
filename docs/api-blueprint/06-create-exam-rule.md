# Create Exam Rule

Tạo bộ quy tắc làm bài mới. `ExamRule` định nghĩa toàn bộ hành vi của phiên làm bài.

**URL**: `/api/v1/exam-rules`
**Method**: `POST`
**Permission**: `exam_rule:create`

## Request

**Headers**:
- `Authorization`: `Bearer {token}`
- `Content-Type`: `application/json`

**Body**:
```json
{
  "title": "Quy tắc thi GPLX B2 chuẩn",
  "allowRetry": false,
  "maxRetry": 0,
  "retryIntervalSeconds": 0,
  "durationSeconds": 1200,
  "gracePeriodSeconds": 0,
  "autoSubmit": true,
  "navigationMode": "FREE",
  "allowSkip": true,
  "reviewMode": "ALL",
  "allowPause": false,
  "maxPauseCount": 0,
  "maxPauseDurationSeconds": 0,
  "allowResume": false,
  "resumeTimeoutSeconds": 0,
  "shuffleSections": false,
  "shuffleQuestionsWithinSection": true,
  "shuffleQuestionsAcrossSections": false,
  "shuffleOptions": true,
  "resultReleaseMode": "IMMEDIATE",
  "showAnswerAfterSubmit": true,
  "showExplanationAfterSubmit": false,
  "showQuestionScoreAfterSubmit": false,
  "requireFullscreen": false,
  "preventTabSwitch": false,
  "maxTabSwitchCount": 0,
  "timeZone": "Asia/Ho_Chi_Minh",
  "metadata": {}
}
```

### Validate
- `title`: Bắt buộc, không được rỗng, tối đa 255 ký tự, **phải là unique**.
- `maxRetry`: Phải >= 0.
- `retryIntervalSeconds`: Phải >= 0.
- `durationSeconds`: Phải > 0.
- `gracePeriodSeconds`: Phải >= 0.
- `maxPauseCount`: Phải >= 0.
- `maxPauseDurationSeconds`: Phải >= 0.
- `resumeTimeoutSeconds`: Phải >= 0.
- `maxTabSwitchCount`: Phải >= 0.
- `navigationMode`: Phải thuộc Enum `FREE`, `SEQUENTIAL`.
- `reviewMode`: Phải thuộc Enum `NONE`, `CURRENT_SECTION`, `ALL`.
- `resultReleaseMode`: Phải thuộc Enum `IMMEDIATE`, `AFTER_SUBMIT`, `AFTER_EXAM_END`, `MANUAL`.
- `timeZone`: Optional, nếu có phải là IANA Time Zone hợp lệ.
- **Logic chéo**:
  - Nếu `allowRetry = false` thì `maxRetry` phải bằng `0`.
  - Nếu `allowPause = true` thì `allowResume` phải là `true`.
  - Nếu `shuffleQuestionsAcrossSections = true` thì `shuffleQuestionsWithinSection` phải là `false` và `shuffleSections` phải là `false`.
  - Nếu `preventTabSwitch = false` thì `maxTabSwitchCount` phải bằng `0`.

## Luồng hoạt động
1. Xác thực Request, kiểm tra quyền `exam_rule:create`.
2. Kiểm tra `title` đã tồn tại chưa.
3. Validate request và logic giữa các field.
4. Tạo bản ghi vào bảng `exam_rules` (status = `ACTIVE`).
5. Map sang Response DTO.

## Responses

### 201 Created
```json
{
  "id": "uuid",
  "title": "Quy tắc thi GPLX B2 chuẩn",
  "status": "ACTIVE"
}
```

### 400 Bad Request
Lỗi validate dữ liệu hoặc logic chéo.
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Nếu không cho phép làm lại (allowRetry=false) thì maxRetry phải bằng 0",
  "traceId": "..."
}
```

### 401 Unauthorized
Missing or invalid token.

### 403 Forbidden
Missing `exam_rule:create` permission.
```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "Access Denied",
  "traceId": "..."
}
```
