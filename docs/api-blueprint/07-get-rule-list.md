# Get Exam Rule List

Lấy danh sách các bộ quy tắc làm bài có phân trang và bộ lọc.

**URL**: `/api/v1/exam-rules`
**Method**: `GET`
**Permission**: `exam_rule:read`

## Request

**Headers**:
- `Authorization`: `Bearer {token}`

**Query Parameters**:
| Parameter | Type   | Required | Default | Description |
| --------- | ------ | -------- | ------- | ----------- |
| page      | int    | No       | 0       | Page index (0-based) |
| size      | int    | No       | 20      | Page size (max 100) |
| keyword   | string | No       |         | Tìm kiếm theo title |
| status    | string | No       |         | Lọc theo trạng thái (ACTIVE, INACTIVE) |

### Validate
- `page` >= 0
- `size` >= 1 và <= 100
- `status` phải thuộc Enum nếu có truyền lên.

## Luồng hoạt động
1. Xác thực Request, kiểm tra quyền `exam_rule:read`.
2. Truy vấn vào bảng `exam_rules` lọc những bản ghi có `deleted_at IS NULL`.
3. Áp dụng các bộ lọc `keyword` (LIKE %title%) và `status` nếu có.
4. Phân trang và map kết quả.

## Responses

### 200 OK
```json
{
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "content": [
    {
      "id": "uuid",
      "title": "Quy tắc thi GPLX B2 chuẩn",
      "status": "ACTIVE",
      "createdAt": "2026-07-31T15:00:00Z"
    }
  ]
}
```

### 401 Unauthorized
Missing or invalid token.

### 403 Forbidden
Missing `exam_rule:read` permission.
