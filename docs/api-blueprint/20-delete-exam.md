# Delete Exam

## Part 0 — Classification & Identity

- **API Name**: Delete Exam
- **API Type**: Internal (Admin)
- **Module**: Exam Management
- **Feature**: Exam Lifecycle
- **Description**: Soft deletes an exam.
- **Related Tables**: `exams`, `exam_versions` (future)
- **Related Services**: Identity Service

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: DELETE
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

### Response

- **Success Status**: 204 No Content

#### Response Body

None

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | Lacks `exam:delete` permission | Access denied |
| `VAL_001` | 400 Bad Request | Payload validation failed | Invalid parameters |
| `NF_001` | 404 Not Found | Exam does not exist | Exam not found |
| `BUS_004` | 409 Conflict | Has published versions | Cannot delete exam with published versions |

---

## Part 2 — Processing Specification

### Controller Layer

1. Authenticate request via `Authorization` header.
2. Validate path variable.
3. Delegate to Service Layer.

### Service Layer

1. **Permission Validation**: Verify the user has `exam:delete` permission.
2. **Business Validation**: 
   - Fetch target exam. Throw 404 Not Found if missing or already deleted.
   - Check if there are any child `ExamVersion` records in `PUBLISHED` state. Throw 409 Conflict if found.
3. **Transaction Boundary**: Start database transaction.
4. **Business Workflow**:
   - Set `deleted_at` to current timestamp.
   - Set `updated_by` to authenticated user ID.
5. Save the entity via Repository.
6. Commit transaction.
7. Return void.

### Repository Layer

1. Query `exams` by `id` where `deleted_at IS NULL`.
2. Update `deleted_at` timestamp.

### External Interaction

None

### Validation

- **Request Validation**: UUID format check.
- **Business Validation**: Dependency check with PUBLISHED versions.
- **Permission Validation**: Requires `exam:delete`.

---

## Part 3 — Data Interaction

- **Operation Type**: SELECT
- **Target Table**: `exams`
- **Conditions**: `id = :examId AND deleted_at IS NULL`
- **Expected Result**: Fetch target exam.

- **Operation Type**: SELECT (EXISTS) - (Deferred/TODO)
- **Target Table**: `exam_versions`
- **Conditions**: `exam_id = :examId AND status = 'PUBLISHED'`
- **Expected Result**: Return true if active versions exist.

- **Operation Type**: UPDATE
- **Target Table**: `exams`
- **Conditions**: `id = :examId`
- **Expected Result**: Row is soft deleted.

---

## Part 4 — Operational Notes

- **Idempotency**: Yes.
- **Tenant Isolation**: Not Applicable.
- **Retry Strategy**: None.
- **Audit Logging**: Required for deletes.
- **Monitoring**: Standard API latency metrics.
- **Tracing**: Request ID propagation.
