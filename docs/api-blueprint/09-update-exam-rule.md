# Update Exam Rule

Cập nhật nội dung của một bộ quy tắc.

**URL**: `/api/v1/exam-rules/{ruleId}`
**Method**: `PATCH`
**Permission**: `exam_rule:update`

## Request

**Headers**:
- `Authorization`: `Bearer {token}`
- `Content-Type`: `application/json`

**Path Variables**:
- `ruleId` (UUID, Required): ID của rule.

**Body**: (Chỉ gửi các field cần cập nhật)
```json
{
  "allowRetry": true,
  "maxRetry": 3,
  "retryIntervalSeconds": 600,
  "durationSeconds": 1800,
  "navigationMode": "SEQUENTIAL"
}
```

### Validate
- Rule phải tồn tại và `deleted_at IS NULL`.
- `status` phải đang là `ACTIVE`.
- `title` nếu có sửa thì không được trùng với rule khác.
- Validate logic chéo giống như Create API:
  - Nếu `allowRetry = false` thì `maxRetry` phải bằng `0`.
  - Nếu `allowPause = true` thì `allowResume` phải là `true`.
  - Nếu `shuffleQuestionsAcrossSections = true` thì `shuffleQuestionsWithinSection` phải là `false` và `shuffleSections` phải là `false`.
  - Nếu `preventTabSwitch = false` thì `maxTabSwitchCount` phải bằng `0`.
- `navigationMode`: Nếu có, phải thuộc Enum `FREE`, `SEQUENTIAL`.
- `reviewMode`: Nếu có, phải thuộc Enum `NONE`, `CURRENT_SECTION`, `ALL`.
- `resultReleaseMode`: Nếu có, phải thuộc Enum `IMMEDIATE`, `AFTER_SUBMIT`, `AFTER_EXAM_END`, `MANUAL`.
- **Business Rule**: KHÔNG cho phép cập nhật nếu Rule đang được sử dụng bởi bất kỳ `ExamVersion` nào có trạng thái `PUBLISHED`.

## Luồng hoạt động
1. Xác thực Request, kiểm tra quyền `exam_rule:update`.
2. Kiểm tra rule tồn tại và đang active.
3. Kiểm tra logic chéo (kết hợp data cũ và mới).
4. Kiểm tra sự ràng buộc với `ExamVersion` (published).
5. Update Database và ghi lại `updated_by`, `updated_at`.
6. Trả kết quả DTO chi tiết (sau khi update).

## Responses

### 200 OK
```json
{
  "id": "uuid",
  "allowRetry": true,
  "maxRetry": 3,
  "retryIntervalSeconds": 600,
  "durationSeconds": 1800,
  "navigationMode": "SEQUENTIAL"
}
```

### 400 Bad Request
Lỗi logic (vd: Đang được publish nên không thể sửa).

### 404 Not Found
Exam rule không tồn tại.

### 401 Unauthorized / 403 Forbidden
Lỗi bảo mật (thiếu Token / sai Quyền).
