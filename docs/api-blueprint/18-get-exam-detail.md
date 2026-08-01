# Get Exam Detail

## Part 0 — Classification & Identity

- **API Name**: Get Exam Detail
- **API Type**: Internal (Admin)
- **Module**: Exam Management
- **Feature**: Exam Lifecycle
- **Description**: Retrieves full details of a specific exam by ID.
- **Related Tables**: `exams`
- **Related Services**: Identity Service

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: GET
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

- **Success Status**: 200 OK

#### Response Body

| Name | Type | Description |
| :--- | :--- | :--- |
| `id` | UUID | Unique identifier of the exam |
| `title` | String | Title of the exam |
| `thumbnailId` | UUID | Reference to exam thumbnail |
| `status` | String | Status of the exam |
| `metadata` | JSON Object | Extensible metadata |
| `createdAt` | DateTime | ISO-8601 creation timestamp |
| `updatedAt` | DateTime | ISO-8601 update timestamp |

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | Lacks `exam:read` permission | Access denied |
| `VAL_001` | 400 Bad Request | Payload/Query validation failed | Invalid parameters |
| `NF_001` | 404 Not Found | Exam does not exist | Exam not found |

---

## Part 2 — Processing Specification

### Controller Layer

1. Authenticate request via `Authorization` header.
2. Validate path variable format.
3. Delegate to Service Layer.

### Service Layer

1. **Permission Validation**: Verify the user has `exam:read` permission.
2. **Business Workflow**:
   - Call Repository to fetch `Exam` by `id`. 
   - If not found or soft-deleted, throw `ResourceNotFoundException`.
3. Map fetched entity to response DTO.

### Repository Layer

1. Query `exams` by `id` where `deleted_at IS NULL`.

### External Interaction

None

### Validation

- **Request Validation**: UUID format check.
- **Permission Validation**: Requires `exam:read`.

---

## Part 3 — Data Interaction

- **Operation Type**: SELECT
- **Target Table**: `exams`
- **Conditions**: `id = :examId AND deleted_at IS NULL`
- **Expected Result**: A single exam record.

---

## Part 4 — Operational Notes

- **Idempotency**: Yes.
- **Tenant Isolation**: Not Applicable.
- **Retry Strategy**: Standard HTTP GET retries.
- **Audit Logging**: Not required.
- **Monitoring**: Standard API latency metrics.
- **Tracing**: Request ID propagation.
