# Exam Criteria Management API Blueprint - Get Criteria List

## Part 0 — Classification & Identity

- API Name: Exam Criteria Management API - List
- API Type: Internal
- Module: Examination Service
- Feature: Exam Criteria Management
- Description: Retrieves a paginated list of exam criteria with optional filtering by title and status.
- Related Tables: `exam_criterias`
- Related Services: None

---

## Part 1 — API Contract

### Endpoint

- HTTP Method: GET
- URL: `/api/v1/exam-criterias`
- Content Type: `application/json`

### Request

**Query Parameters**

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| page | Integer | No | Page number | >= 0, default 0 |
| size | Integer | No | Page size | <= 100, default 20 |
| keyword | String | No | Search by title | None |
| status | String | No | Filter by status | Must be `ACTIVE` or `INACTIVE` |

### Response

- Success Status: 200 OK

**Response Body**

| Name | Type | Description |
| :--- | :--- | :--- |
| page | Integer | Current page number |
| size | Integer | Current page size |
| totalElements | Integer | Total number of elements matching the query |
| items | Array[Object] | List of criteria items |
| items[].id | UUID | Criteria ID |
| items[].title | String | Criteria title |
| items[].status | String | Current status |

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| VALIDATION_ERROR | 400 Bad Request | Invalid query parameters | Invalid pagination or status |
| UNAUTHORIZED | 401 Unauthorized | Missing or invalid authentication | Please authenticate |
| FORBIDDEN | 403 Forbidden | Missing `exam_criteria:read` permission | You do not have permission |

---

## Part 2 — Processing Specification

### Controller Layer
1. Validate pagination and filtering parameters.
2. Delegate query request to Service layer.

### Service Layer
1. Extract user authentication information.
2. Validate user has `exam_criteria:read` permission.
3. Delegate to Repository to fetch paginated list matching keyword and status filters.
4. Return paginated result.

### Repository Layer
1. Execute query on `exam_criterias` table with conditions (`title` LIKE keyword, `status` = status, `deleted_at` IS NULL).
2. Apply pagination (LIMIT, OFFSET).

### External Interaction
None

### Validation
- Request Validation: Pagination ranges (`page` >= 0, `size` <= 100). Enum validation for `status`.
- Permission Validation: Requires `exam_criteria:read`.

---

## Part 3 — Data Interaction

- Operation Type: SELECT
- Target Table: `exam_criterias`
- Conditions: `deleted_at` IS NULL. Optional filters on `title` (LIKE), `status`.
- Expected Result: A list of matching records and a total count.

---

## Part 4 — Operational Notes

- **Idempotency**: GET operations are inherently idempotent.
- **Tenant Isolation**: Not Applicable
- **Retry Strategy**: Not Applicable
- **Audit Logging**: Not Applicable for GET.
- **Monitoring**: Standard API latency and error rates tracking.
- **Metrics**: Not Applicable
- **Tracing**: Request id propagation via standard `traceId`.
