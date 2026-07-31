# Exam Criteria Management API Blueprint - Get Criteria Detail

## Part 0 — Classification & Identity

- API Name: Exam Criteria Management API - Detail
- API Type: Internal
- Module: Examination Service
- Feature: Exam Criteria Management
- Description: Retrieves detailed information about a specific exam criteria including its logical rules and configuration.
- Related Tables: `exam_criterias`
- Related Services: None

---

## Part 1 — API Contract

### Endpoint

- HTTP Method: GET
- URL: `/api/v1/exam-criterias/{criteriaId}`
- Content Type: `application/json`

### Request

**Path Variables**

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| criteriaId | UUID | Yes | Criteria unique identifier | Must be a valid UUID |

### Response

- Success Status: 200 OK

**Response Body**

| Name | Type | Description |
| :--- | :--- | :--- |
| id | UUID | Criteria ID |
| title | String | Criteria title |
| status | String | Current status |
| criteria | Object | Evaluation criteria logic |
| metadata | Object | Additional metadata |

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| VALIDATION_ERROR | 400 Bad Request | Invalid path variable | Invalid criteria ID format |
| UNAUTHORIZED | 401 Unauthorized | Missing or invalid authentication | Please authenticate |
| FORBIDDEN | 403 Forbidden | Missing `exam_criteria:read` permission | You do not have permission |
| NOT_FOUND | 404 Not Found | Criteria does not exist or is deleted | Criteria not found |

---

## Part 2 — Processing Specification

### Controller Layer
1. Validate `criteriaId` format.
2. Delegate detail retrieval to Service layer.

### Service Layer
1. Extract user authentication information.
2. Validate user has `exam_criteria:read` permission.
3. Call Repository to find criteria by ID.
4. If not found or soft-deleted, throw `NOT_FOUND` error.
5. Return criteria detail.

### Repository Layer
1. Query `exam_criterias` table by `id` where `deleted_at` IS NULL.

### External Interaction
None

### Validation
- Request Validation: `criteriaId` is UUID.
- Permission Validation: Requires `exam_criteria:read`.

---

## Part 3 — Data Interaction

- Operation Type: SELECT
- Target Table: `exam_criterias`
- Conditions: `id` = {criteriaId} AND `deleted_at` IS NULL.
- Expected Result: A single record.

---

## Part 4 — Operational Notes

- **Idempotency**: GET operations are inherently idempotent.
- **Tenant Isolation**: Not Applicable
- **Retry Strategy**: Not Applicable
- **Audit Logging**: Not Applicable for GET.
- **Monitoring**: Standard API latency and error rates tracking.
- **Metrics**: Not Applicable
- **Tracing**: Request id propagation via standard `traceId`.
