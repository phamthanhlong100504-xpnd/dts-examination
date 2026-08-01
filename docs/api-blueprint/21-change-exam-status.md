# Change Exam Status

## Part 0 — Classification & Identity

- **API Name**: Change Exam Status
- **API Type**: Internal (Admin)
- **Module**: Exam Management
- **Feature**: Exam Lifecycle
- **Description**: Transitions an exam between statuses.
- **Related Tables**: `exams`
- **Related Services**: Identity Service

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: PATCH
- **URL**: `/api/v1/exams/{examId}/status`
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
| `status` | String | Yes | New status | Valid enum value |

### Response

- **Success Status**: 200 OK

#### Response Body

| Name | Type | Description |
| :--- | :--- | :--- |
| `id` | UUID | Unique identifier of the exam |
| `status` | String | New status of the exam |

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | Lacks `exam:update_status` permission | Access denied |
| `VAL_001` | 400 Bad Request | Payload validation failed | Invalid input data |
| `NF_001` | 404 Not Found | Exam does not exist | Exam not found |
| `BUS_005` | 409 Conflict | Invalid state transition | Invalid status transition requested |

---

## Part 2 — Processing Specification

### Controller Layer

1. Authenticate request via `Authorization` header.
2. Validate path variable and payload.
3. Delegate to Service Layer.

### Service Layer

1. **Permission Validation**: Verify the user has `exam:update_status` permission.
2. **Business Validation**: 
   - Fetch target exam. Throw 404 Not Found if missing.
   - Enforce state machine transitions:
     - `DRAFT` -> `PUBLISHED` or `HIDDEN`
     - `PUBLISHED` -> `ARCHIVED` or `HIDDEN`
     - `HIDDEN` -> `DRAFT` or `PUBLISHED`
   - Target status cannot be the same as current status.
   - If current status is `ARCHIVED`, it cannot be transitioned to any other state. Throw 409 Conflict.
3. **Transaction Boundary**: Start database transaction.
4. **Business Workflow**:
   - Update `status` field.
   - Set `updated_by` to the authenticated user ID.
5. Save the entity via Repository.
6. Commit transaction.
7. Map saved entity to response DTO.

### Repository Layer

1. Query `exams` by `id` where `deleted_at IS NULL`.
2. Persist updated `Exam` entity.

### External Interaction

None

### Validation

- **Request Validation**: Enum format check.
- **Business Validation**: State machine transition rules.
- **Permission Validation**: Requires `exam:update_status`.

---

## Part 3 — Data Interaction

- **Operation Type**: SELECT
- **Target Table**: `exams`
- **Conditions**: `id = :examId AND deleted_at IS NULL`
- **Expected Result**: Fetch target exam.

- **Operation Type**: UPDATE
- **Target Table**: `exams`
- **Conditions**: `id = :examId`
- **Expected Result**: Row status is updated.

---

## Part 4 — Operational Notes

- **Idempotency**: Yes.
- **Tenant Isolation**: Not Applicable.
- **Retry Strategy**: None.
- **Audit Logging**: Required for status transitions.
- **Monitoring**: Standard API latency metrics.
- **Tracing**: Request ID propagation.
