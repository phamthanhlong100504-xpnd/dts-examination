# Create Exam

## Part 0 — Classification & Identity

- **API Name**: Create Exam
- **API Type**: Internal (Admin)
- **Module**: Exam Management
- **Feature**: Exam Lifecycle
- **Description**: Creates a new exam record in DRAFT status.
- **Related Tables**: `exams`
- **Related Services**: Identity Service

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: POST
- **URL**: `/api/v1/exams`
- **Content Type**: `application/json`

### Request

#### Headers

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| `Authorization` | String | Yes | Bearer token for authentication | Must be a valid JWT |

#### Request Body

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| `title` | String | Yes | Title of the exam | Not blank, max 100 chars, unique |
| `thumbnailId` | UUID | No | Reference to media service | Valid UUID |
| `metadata` | JSON Object | No | Extensible metadata | Valid JSON object |

### Response

- **Success Status**: 201 Created

#### Response Body

| Name | Type | Description |
| :--- | :--- | :--- |
| `id` | UUID | Unique identifier of the exam |
| `title` | String | Title of the exam |
| `thumbnailId` | UUID | Reference to exam thumbnail |
| `status` | String | Status of the exam (DRAFT) |
| `metadata` | JSON Object | Extensible metadata |
| `createdAt` | DateTime | ISO-8601 creation timestamp |

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | Lacks `exam:create` permission | Access denied |
| `VAL_001` | 400 Bad Request | Payload validation failed | Invalid input data |
| `BUS_002` | 409 Conflict | Title already exists | Exam title must be unique |

---

## Part 2 — Processing Specification

### Controller Layer

1. Authenticate request via `Authorization` header.
2. Validate input payload format against JSR-303 requirements.
3. Map the JSON payload to internal creation DTO.
4. Delegate to Service Layer.

### Service Layer

1. **Permission Validation**: Verify the user has `exam:create` permission.
2. **Business Validation**: 
   - Check if an exam with the requested `title` already exists (excluding soft-deleted records). Throw 409 Conflict if found.
3. **Transaction Boundary**: Start database transaction.
4. **Business Workflow**:
   - Initialize a new `Exam` entity.
   - Assign `title`, `thumbnailId`, and `metadata` from request.
   - Set `status` to `DRAFT`.
   - Set `created_by` and `updated_by` from the authenticated user context.
5. Save the entity via Repository.
6. Commit transaction.
7. Map saved entity to response DTO.

### Repository Layer

1. Query `exams` to check for title uniqueness where `deleted_at IS NULL`.
2. Persist new `Exam` entity to database.

### External Interaction

None

### Validation

- **Request Validation**: `title` (not blank, max 100).
- **Business Validation**: Title uniqueness.
- **Permission Validation**: Requires `exam:create`.

---

## Part 3 — Data Interaction

- **Operation Type**: SELECT (EXISTS)
- **Target Table**: `exams`
- **Conditions**: `title = :title AND deleted_at IS NULL`
- **Expected Result**: Return true if exists, false otherwise.

- **Operation Type**: INSERT
- **Target Table**: `exams`
- **Conditions**: N/A
- **Expected Result**: A new exam row is inserted.

---

## Part 4 — Operational Notes

- **Idempotency**: Not guaranteed.
- **Tenant Isolation**: Not Applicable.
- **Retry Strategy**: None.
- **Audit Logging**: Successful creation is logged with the user ID.
- **Monitoring**: Standard API latency metrics.
- **Tracing**: Request ID propagation.
