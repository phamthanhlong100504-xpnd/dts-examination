# Get Exam Structure Detail

## Part 0 — Classification & Identity

- **API Name**: Get Exam Structure Detail
- **API Type**: Internal (Admin)
- **Module**: Content Builder
- **Feature**: Exam Structure Configuration
- **Description**: Retrieves the complete details of a specific exam structure by ID.
- **Related Tables**: `exam_structures`
- **Related Services**: Identity Service (for authorization and auditing)

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: GET
- **URL**: `/api/v1/exam-structures/{structureId}`
- **Content Type**: `application/json`

### Request

#### Headers

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| `Authorization` | String | Yes | Bearer token for authentication | Must be a valid JWT |

#### Path Variables

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| `structureId` | UUID | Yes | Target exam structure ID | Valid UUID format |

### Response

- **Success Status**: 200 OK

#### Response Body

| Name | Type | Description |
| :--- | :--- | :--- |
| `id` | UUID | Unique identifier of the structure |
| `title` | String | Title of the exam structure |
| `status` | String | Status of the structure |
| `sections` | JSON Array | Defined sections of the exam |
| `metadata` | JSON Object | Extensible metadata |
| `createdAt` | DateTime | ISO-8601 creation timestamp |
| `updatedAt` | DateTime | ISO-8601 update timestamp |

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | Lacks `exam_structure:read` permission | Access denied |
| `VAL_002` | 400 Bad Request | Invalid structureId format | Invalid UUID |
| `NF_001` | 404 Not Found | Structure does not exist or is deleted | Exam structure not found |

---

## Part 2 — Processing Specification

### Controller Layer

1. Authenticate request.
2. Validate path variable format.
3. Delegate retrieval to Service Layer.

### Service Layer

1. **Permission Validation**: Verify the user has `exam_structure:read` permission.
2. **Business Workflow**:
   - Call Repository Layer to find structure by `id`.
   - If not found or `deleted_at` is not null, throw `ResourceNotFoundException`.
   - Map entity to response DTO.

### Repository Layer

1. Fetch record by `id`.

### External Interaction

None

### Validation

- **Permission Validation**: Requires `exam_structure:read`.
- **Business Validation**: Verify the resource is not soft-deleted.

---

## Part 3 — Data Interaction

- **Operation Type**: SELECT
- **Target Table**: `exam_structures`
- **Conditions**: `id = :structureId AND deleted_at IS NULL`
- **Expected Result**: A single row or none.

---

## Part 4 — Operational Notes

- **Idempotency**: Yes (Safe Method).
- **Tenant Isolation**: Not Applicable.
- **Retry Strategy**: Client can retry safely.
- **Audit Logging**: None.
- **Monitoring**: Standard API latency metrics.
- **Tracing**: Request ID propagation.
