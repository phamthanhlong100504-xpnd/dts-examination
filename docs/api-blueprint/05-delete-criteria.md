# Exam Criteria Management API Blueprint - Delete Criteria

## Part 0 — Classification & Identity

- API Name: Exam Criteria Management API - Delete
- API Type: Internal
- Module: Examination Service
- Feature: Exam Criteria Management
- Description: Soft deletes an exam criteria. Prevents deletion if criteria is referenced by any exam version.
- Related Tables: `exam_criterias`, `exam_versions` (for dependency check)
- Related Services: None

---

## Part 1 — API Contract

### Endpoint

- HTTP Method: DELETE
- URL: `/api/v1/exam-criterias/{criteriaId}`
- Content Type: `application/json`

### Request

**Path Variables**

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| criteriaId | UUID | Yes | Criteria unique identifier | Must be a valid UUID |

### Response

- Success Status: 204 No Content

**Response Body**

None

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| VALIDATION_ERROR | 400 Bad Request | Invalid path variable | Invalid criteria ID format |
| UNAUTHORIZED | 401 Unauthorized | Missing or invalid authentication | Please authenticate |
| FORBIDDEN | 403 Forbidden | Missing `exam_criteria:delete` permission | You do not have permission |
| NOT_FOUND | 404 Not Found | Criteria does not exist or is deleted | Criteria not found |
| CONFLICT | 409 Conflict | Criteria is used by any ExamVersion | Cannot delete referenced criteria |

---

## Part 2 — Processing Specification

### Controller Layer
1. Validate `criteriaId` format.
2. Delegate deletion to Service layer.

### Service Layer
1. Extract user authentication information.
2. Validate user has `exam_criteria:delete` permission.
3. Call Repository to find criteria by ID.
4. If not found or soft-deleted, throw `NOT_FOUND` error.
5. Check dependencies: Query `exam_versions` to see if ANY version references this `criteriaId`.
6. If referenced, throw `CONFLICT` error.
7. Set `deleted_at` timestamp.
8. Delegate to Repository to persist soft delete.

### Repository Layer
1. Query `exam_criterias` table by `id`.
2. Query `exam_versions` table by `criteria_id`.
3. Update `deleted_at` field in `exam_criterias` table.

### External Interaction
None

### Validation
- Request Validation: `criteriaId` is UUID.
- Permission Validation: Requires `exam_criteria:delete`.
- Business Validation: General `ExamVersion` dependency check.

---

## Part 3 — Data Interaction

**Check Dependency**
- Operation Type: SELECT
- Target Table: `exam_versions`
- Conditions: `criteria_id` = {criteriaId}.
- Expected Result: True/False if exists.

**Execute Soft Delete**
- Operation Type: UPDATE
- Target Table: `exam_criterias`
- Conditions: `id` = {criteriaId}.
- Expected Result: `deleted_at` is populated.

---

## Part 4 — Operational Notes

- **Idempotency**: DELETE operations are implemented as soft-deletes and are safe to retry (subsequent deletes will result in 404 or 204 depending on exact API design, commonly 204 for idempotent soft delete).
- **Tenant Isolation**: Not Applicable
- **Retry Strategy**: Not Applicable
- **Audit Logging**: `deleted_at` field implements soft delete logic.
- **Monitoring**: Standard API latency and error rates tracking.
- **Metrics**: Not Applicable
- **Tracing**: Request id propagation via standard `traceId`.
