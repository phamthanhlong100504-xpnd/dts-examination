# Get Exam Structure List

## Part 0 — Classification & Identity

- **API Name**: Get Exam Structure List
- **API Type**: Internal (Admin)
- **Module**: Content Builder
- **Feature**: Exam Structure Configuration
- **Description**: Retrieves a paginated list of exam structures with optional filtering and sorting.
- **Related Tables**: `exam_structures`
- **Related Services**: Identity Service (for authorization and auditing)

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: GET
- **URL**: `/api/v1/exam-structures`
- **Content Type**: `application/json`

### Request

#### Headers

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| `Authorization` | String | Yes | Bearer token for authentication | Must be a valid JWT |

#### Query Parameters

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| `page` | Integer | No | Page number (0-indexed) | >= 0, default 0 |
| `size` | Integer | No | Page size | 1-100, default 20 |
| `keyword` | String | No | Search keyword for title | N/A |
| `status` | String | No | Filter by status (ACTIVE/INACTIVE) | Must be valid enum |
| `sort` | String | No | Sorting criteria (e.g., `title,asc`) | Format: field,direction |

### Response

- **Success Status**: 200 OK

#### Response Body

| Name | Type | Description |
| :--- | :--- | :--- |
| `page` | Integer | Current page number |
| `size` | Integer | Size of the page |
| `totalElements` | Long | Total number of elements matching the filter |
| `items` | Array | List of exam structures |
| `items[].id` | UUID | Structure ID |
| `items[].title` | String | Structure Title |
| `items[].status` | String | Current status |

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | Lacks `exam_structure:read` permission | Access denied |
| `VAL_001` | 400 Bad Request | Invalid pagination or sorting parameters | Invalid request parameters |

---

## Part 2 — Processing Specification

### Controller Layer

1. Authenticate the request via `Authorization` header.
2. Validate query parameters (`page`, `size`, `status`, `sort`).
3. Construct query/filter criteria object.
4. Delegate to the Service Layer.

### Service Layer

1. **Permission Validation**: Verify the user has `exam_structure:read` permission.
2. **Business Workflow**:
   - Forward search criteria to Repository Layer to fetch paginated results.
   - Construct pageable response payload.

### Repository Layer

1. Query database using filters (`keyword` LIKE title, `status` EXACT).
2. Count total matching rows for pagination metadata.

### External Interaction

None

### Validation

- **Request Validation**: Parameter boundary checks.
- **Permission Validation**: Requires `exam_structure:read`.

---

## Part 3 — Data Interaction

- **Operation Type**: SELECT
- **Target Table**: `exam_structures`
- **Conditions**: Filter by `title` ILIKE (if keyword provided), filter by `status` (if provided), `deleted_at IS NULL`.
- **Expected Result**: Return paginated result set.
- **Performance Notes**: Leverage `ix_exam_structures_status` if status filtering is applied.

---

## Part 4 — Operational Notes

- **Idempotency**: Yes (Safe Method).
- **Tenant Isolation**: Not Applicable.
- **Retry Strategy**: Client can retry safely.
- **Audit Logging**: None.
- **Monitoring**: Standard API latency metrics.
- **Tracing**: Request ID propagation.
