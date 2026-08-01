# Get Exam List

## Part 0 — Classification & Identity

- **API Name**: Get Exam List
- **API Type**: Internal (Admin)
- **Module**: Exam Management
- **Feature**: Exam Lifecycle
- **Description**: Retrieves a paginated list of exams with optional filtering.
- **Related Tables**: `exams`
- **Related Services**: Identity Service

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: GET
- **URL**: `/api/v1/exams`
- **Content Type**: `application/json`

### Request

#### Query Parameters

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| `page` | Integer | No | Page number (0-indexed) | >= 0 (default 0) |
| `size` | Integer | No | Page size | > 0, <= 100 (default 20) |
| `keyword` | String | No | Search by title | Max 255 chars |
| `status` | String | No | Filter by exam status | DRAFT, PUBLISHED, ARCHIVED, HIDDEN |
| `createdBy` | UUID | No | Filter by creator | Valid UUID |
| `sort` | String | No | Sort field | title, createdAt, updatedAt |

#### Headers

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| `Authorization` | String | Yes | Bearer token for authentication | Must be a valid JWT |

### Response

- **Success Status**: 200 OK

#### Response Body

| Name | Type | Description |
| :--- | :--- | :--- |
| `page` | Integer | Current page number |
| `size` | Integer | Number of items per page |
| `totalElements` | Long | Total number of elements matching the filter |
| `totalPages` | Integer | Total number of pages |
| `items` | JSON Array | List of exams |
| `items[].id` | UUID | Unique identifier of the exam |
| `items[].title` | String | Title of the exam |
| `items[].status` | String | Status of the exam |
| `items[].createdAt` | DateTime | ISO-8601 creation timestamp |

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | Lacks `exam:read` permission | Access denied |
| `VAL_001` | 400 Bad Request | Payload/Query validation failed | Invalid query parameters |

---

## Part 2 — Processing Specification

### Controller Layer

1. Authenticate request via `Authorization` header.
2. Validate query parameters format.
3. Delegate to Service Layer.

### Service Layer

1. **Permission Validation**: Verify the user has `exam:read` permission.
2. **Business Workflow**:
   - Construct pageable request using `page`, `size`, and `sort`.
   - Call Repository to fetch paginated data based on filters (`keyword`, `status`, `createdBy`).
3. Map fetched entities to list response DTOs.

### Repository Layer

1. Query `exams` table with pagination.
2. Apply `ILIKE` on `title` if `keyword` is provided.
3. Apply equality on `status` and `createdBy` if provided.
4. Exclude soft-deleted records (`deleted_at IS NULL`).

### External Interaction

None

### Validation

- **Request Validation**: Pagination bounds, UUID format, Enum format for status.
- **Permission Validation**: Requires `exam:read`.

---

## Part 3 — Data Interaction

- **Operation Type**: SELECT
- **Target Table**: `exams`
- **Conditions**: 
  - `deleted_at IS NULL`
  - `title ILIKE %:keyword%` (if present)
  - `status = :status` (if present)
  - `created_by = :createdBy` (if present)
- **Expected Result**: A paginated list of matching exams.

---

## Part 4 — Operational Notes

- **Idempotency**: Yes.
- **Tenant Isolation**: Not Applicable.
- **Retry Strategy**: Standard HTTP GET retries.
- **Audit Logging**: Not required for read operations.
- **Monitoring**: Standard API latency metrics.
- **Tracing**: Request ID propagation.
