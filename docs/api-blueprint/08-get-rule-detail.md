# Get Exam Rule Detail

Lấy thông tin chi tiết của một bộ quy tắc.

**URL**: `/api/v1/exam-rules/{ruleId}`
**Method**: `GET`
**Permission**: `exam_rule:read`

## Request

**Headers**:
- `Authorization`: `Bearer {token}`

**Path Variables**:
- `ruleId` (UUID, Required): ID của rule.

## Luồng hoạt động
1. Xác thực Request, kiểm tra quyền `exam_rule:read`.
2. Query Database tìm `ExamRule` có `id = ruleId` và `deleted_at IS NULL`.
3. Nếu không tìm thấy, throw `ResourceNotFoundException`.
4. Trả về DTO chi tiết.

## Responses

### 200 OK
```json
{
  "id": "uuid",
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
  "status": "ACTIVE",
  "metadata": {},
  "createdAt": "2026-07-31T15:00:00Z",
  "updatedAt": "2026-07-31T15:00:00Z"
}
```

### 404 Not Found
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Exam rule not found",
  "traceId": "..."
}
```

### 401 Unauthorized
Missing or invalid token.

### 403 Forbidden
Missing `exam_rule:read` permission.
