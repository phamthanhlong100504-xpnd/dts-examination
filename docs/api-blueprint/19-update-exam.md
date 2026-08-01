# Update Exam

## Part 0 — Classification & Identity

- **API Name**: Update Exam
- **API Type**: Internal (Admin)
- **Module**: Exam Management
- **Feature**: Exam Lifecycle
- **Description**: Partially updates an existing exam.
- **Related Tables**: `exams`
- **Related Services**: Identity Service

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: PATCH
- **URL**: `/api/v1/exams/{examId}`
- **Content Type**: `application/json`

### Request

#### Path Variables

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| `examId` | UUID | Yes | Target exam ID | Valid UUID format |

#### Headers

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| `Authorization` | String | Yes | Bearer token for authentication | Must be a valid JWT |

#### Request Body

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| `title` | String | No | New title of the exam | Max 100 chars, unique |
| `thumbnailId` | UUID | No | New reference to media | Valid UUID |
| `metadata` | JSON Object | No | New metadata | Valid JSON object |

### Response

- **Success Status**: 200 OK

#### Response Body

| Name | Type | Description |
| :--- | :--- | :--- |
| `id` | UUID | Unique identifier of the exam |
| `title` | String | Title of the exam |
| `status` | String | Status of the exam |

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | Lacks `exam:update` permission | Access denied |
| `VAL_001` | 400 Bad Request | Payload validation failed | Invalid input data |
| `NF_001` | 404 Not Found | Exam does not exist | Exam not found |
| `BUS_002` | 409 Conflict | Title already exists | Exam title must be unique |
| `BUS_003` | 409 Conflict | Exam is ARCHIVED | Cannot update ARCHIVED exam |

---

## Part 2 — Processing Specification

### Controller Layer

1. Authenticate request via `Authorization` header.
2. Validate path variable and payload.
3. Map JSON payload to update DTO.
4. Delegate to Service Layer.

### Service Layer

1. **Permission Validation**: Verify the user has `exam:update` permission.
2. **Business Validation**: 
   - Check if an exam with the requested `title` already exists and belongs to a different ID (excluding soft-deleted records). Throw 409 Conflict if found.
   - Fetch target exam. Throw 404 Not Found if missing.
   - If exam status is `ARCHIVED`, throw 409 Conflict.
3. **Transaction Boundary**: Start database transaction.
4. **Business Workflow**:
   - Update `title`, `thumbnailId`, `metadata` if they are present in the request.
   - Set `updated_by` to the authenticated user ID.
5. Save the entity via Repository.
6. Commit transaction.
7. Map saved entity to response DTO.

### Repository Layer

1. Query `exams` by `id` where `deleted_at IS NULL`.
2. Query `exams` to check for title uniqueness.
3. Persist updated `Exam` entity.

### External Interaction

None

### Validation

- **Request Validation**: Null checks for partial updates, max length.
- **Business Validation**: Title uniqueness, block updates on ARCHIVED state.
- **Permission Validation**: Requires `exam:update`.

---

## Part 3 — Data Interaction

- **Operation Type**: SELECT
- **Target Table**: `exams`
- **Conditions**: `id = :examId AND deleted_at IS NULL`
- **Expected Result**: Fetch target exam.

- **Operation Type**: SELECT (EXISTS)
- **Target Table**: `exams`
- **Conditions**: `title = :title AND id != :examId AND deleted_at IS NULL`
- **Expected Result**: Return true if duplicate title exists.

- **Operation Type**: UPDATE
- **Target Table**: `exams`
- **Conditions**: `id = :examId`
- **Expected Result**: Exam record is updated.

---

## Part 4 — Operational Notes

- **Idempotency**: Yes.
- **Tenant Isolation**: Not Applicable.
- **Retry Strategy**: None.
- **Audit Logging**: Required for updates.
- **Monitoring**: Standard API latency metrics.
- **Tracing**: Request ID propagation.
