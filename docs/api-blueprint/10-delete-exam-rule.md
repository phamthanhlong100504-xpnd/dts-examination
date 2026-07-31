# Delete Exam Rule

Xóa mềm (Soft Delete) một bộ quy tắc làm bài.

**URL**: `/api/v1/exam-rules/{ruleId}`
**Method**: `DELETE`
**Permission**: `exam_rule:delete`

## Request

**Headers**:
- `Authorization`: `Bearer {token}`

**Path Variables**:
- `ruleId` (UUID, Required): ID của rule.

### Validate
- Rule phải tồn tại.
- **Business Rule**: KHÔNG cho phép xóa nếu rule đang được tham chiếu bởi bất kỳ `ExamVersion` nào (kể cả DRAFT).

## Luồng hoạt động
1. Xác thực Request, kiểm tra quyền `exam_rule:delete`.
2. Truy vấn rule. Nếu không có ném `ResourceNotFoundException`.
3. Kiểm tra bảng `exam_versions` xem ruleId có đang được tham chiếu không. (Sẽ throw `BusinessException` nếu có).
4. Set trường `deleted_at` = current timestamp.
5. Cập nhật `updated_by`.

## Responses

### 204 No Content
Xóa thành công, không trả về body.

### 400 Bad Request
Không thể xóa do đang được tham chiếu.
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Cannot delete exam rule because it is being used by existing exam versions",
  "traceId": "..."
}
```

### 404 Not Found
Exam rule không tồn tại.

### 401 Unauthorized / 403 Forbidden
Lỗi bảo mật (thiếu Token / sai Quyền).
