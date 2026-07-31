# Exam Criteria Management API Blueprint - Update Criteria

## Part 0 — Classification & Identity

- API Name: Exam Criteria Management API - Update
- API Type: Internal
- Module: Examination Service
- Feature: Exam Criteria Management
- Description: Updates an existing exam criteria. Prevents update if criteria is referenced by a PUBLISHED exam version.
- Related Tables: `exam_criterias`, `exam_versions` (for dependency check)
- Related Services: None

---

## Part 1 — API Contract

### Endpoint

- HTTP Method: PATCH
- URL: `/api/v1/exam-criterias/{criteriaId}`
- Content Type: `application/json`

### Request

**Path Variables**

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| criteriaId | UUID | Yes | Criteria unique identifier | Must be a valid UUID |

**Request Body**

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| title | String | No | Title of the criteria | Not empty, max 255 characters |
| criteria | Object | No | Evaluation criteria logic | Must be valid JSON object |
| criteria.passScore | Integer | No | Minimum score required to pass | >= 0 |
| criteria.totalScore | Integer | No | Maximum possible score | >= passScore |
| criteria.gradingMethod | String | No | Method for grading | Enum: SUM, WEIGHTED, PERCENTAGE, BEST_OF, AVERAGE |
| criteria.rounding | Object | No | Score rounding rules | Must be valid object |
| criteria.rounding.mode | String | No | Rounding mode | Enum: HALF_UP, etc. |
| criteria.rounding.precision | Integer | No | Number of decimal places | >= 0 |
| criteria.mandatoryRules | Array[Object] | No | Rules that must be satisfied | Valid array of rules |
| criteria.mandatoryRules[].type | String | No | Type of mandatory rule | Enum: MUST_CORRECT, MUST_ATTEMPT, AT_LEAST_ONE, MAX_WRONG |
| criteria.mandatoryRules[].questionIds | Array[String] | No | Applicable question IDs | Valid array of IDs |
| criteria.sectionRules | Array[Object] | No | Rules specific to sections | Valid array of rules |
| criteria.sectionRules[].sectionId | String | No | Section identifier | Not empty |
| criteria.sectionRules[].minScore | Integer | No | Minimum score for section | >= 0 |
| criteria.penalties | Array[Object] | No | Penalty configurations | Valid array of penalties |
| criteria.penalties[].type | String | No | Type of penalty | Enum: UNANSWERED, WRONG_ANSWER |
| criteria.penalties[].deduct | Integer/Decimal | No | Points to deduct | >= 0 |

### Response

- Success Status: 200 OK

**Response Body**

| Name | Type | Description |
| :--- | :--- | :--- |
| id | UUID | Criteria ID |
| title | String | Updated criteria title |
| status | String | Current status |

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| VALIDATION_ERROR | 400 Bad Request | Invalid input payload | Invalid request parameters |
| UNAUTHORIZED | 401 Unauthorized | Missing or invalid authentication | Please authenticate |
| FORBIDDEN | 403 Forbidden | Missing `exam_criteria:update` permission | You do not have permission |
| NOT_FOUND | 404 Not Found | Criteria does not exist or is deleted | Criteria not found |
| CONFLICT | 409 Conflict | Criteria is used by PUBLISHED ExamVersion | Cannot update referenced criteria |

---

## Part 2 — Processing Specification

### Controller Layer
1. Validate `criteriaId` and payload format.
2. Delegate update request to Service layer.

### Service Layer
1. Extract user authentication information.
2. Validate user has `exam_criteria:update` permission.
3. Call Repository to find criteria by ID.
4. If not found or soft-deleted, throw `NOT_FOUND` error.
5. Verify current criteria `status` is `ACTIVE`.
6. Validate business logic for the new `criteria` data (same logic as Create).
7. Check dependencies: Query `exam_versions` to see if any version with `PUBLISHED` status references this `criteriaId`.
8. If referenced, throw `CONFLICT` error.
9. Update `title`, `criteria` fields on the entity.
10. Set `updated_by` to the current user ID.
11. Delegate to Repository to update the database.
12. Return updated `ExamCriteria` information.

### Repository Layer
1. Query `exam_criterias` table by `id`.
2. Query `exam_versions` table by `criteria_id` and `status` = 'PUBLISHED'.
3. Update specific fields in `exam_criterias` table.

### External Interaction
None

### Validation
- Request Validation: Data types and constraints.
- Permission Validation: Requires `exam_criteria:update`.
- Business Validation: Criteria constraints, `PUBLISHED` ExamVersion dependency check.

---

## Part 3 — Data Interaction

**Check Dependency**
- Operation Type: SELECT
- Target Table: `exam_versions`
- Conditions: `criteria_id` = {criteriaId} AND `status` = 'PUBLISHED'.
- Expected Result: True/False if exists.

**Execute Update**
- Operation Type: UPDATE
- Target Table: `exam_criterias`
- Conditions: `id` = {criteriaId}.
- Expected Result: `title`, `criteria`, `updated_at`, `updated_by` are updated.

---

## Part 4 — Operational Notes

- **Idempotency**: PATCH operations are idempotent if applying the exact same payload results in the exact same state without unintended side effects.
- **Tenant Isolation**: Not Applicable
- **Retry Strategy**: Not Applicable
- **Audit Logging**: `updated_by` field is maintained.
- **Monitoring**: Standard API latency and error rates tracking.
- **Metrics**: Count of updates rejected due to PUBLISHED dependencies.
- **Tracing**: Request id propagation via standard `traceId`.
