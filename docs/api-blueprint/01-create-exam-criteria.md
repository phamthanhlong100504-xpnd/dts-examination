# Exam Criteria Management API Blueprint - Create Exam Criteria

## Part 0 — Classification & Identity

- API Name: Exam Criteria Management API - Create
- API Type: Internal
- Module: Examination Service
- Feature: Exam Criteria Management
- Description: Manages exam evaluation criteria, defining passing conditions, scoring logic, critical questions, and section weights. This specific API creates a new criteria.
- Related Tables: `exam_criterias`
- Related Services: None

---

## Part 1 — API Contract

### Endpoint

- HTTP Method: POST
- URL: `/api/v1/exam-criterias`
- Content Type: `application/json`

### Request

**Request Body**

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| title | String | Yes | Title of the criteria | Not empty, max 255 characters |
| criteria | Object | Yes | Evaluation criteria logic | Must be valid JSON object |
| criteria.passScore | Integer | Yes | Minimum score required to pass | >= 0 |
| criteria.totalScore | Integer | Yes | Maximum possible score | >= passScore |
| criteria.gradingMethod | String | Yes | Method for grading | Enum: SUM, WEIGHTED, PERCENTAGE, BEST_OF, AVERAGE |
| criteria.rounding | Object | No | Score rounding rules | Must be valid object |
| criteria.rounding.mode | String | No | Rounding mode | Enum: HALF_UP, etc. |
| criteria.rounding.precision | Integer | No | Number of decimal places | >= 0 |
| criteria.mandatoryRules | Array[Object] | No | Rules that must be satisfied | Valid array of rules |
| criteria.mandatoryRules[].type | String | Yes | Type of mandatory rule | Enum: MUST_CORRECT, MUST_ATTEMPT, AT_LEAST_ONE, MAX_WRONG |
| criteria.mandatoryRules[].questionIds | Array[String] | Yes | Applicable question IDs | Valid array of IDs |
| criteria.sectionRules | Array[Object] | No | Rules specific to sections | Valid array of rules |
| criteria.sectionRules[].sectionId | String | Yes | Section identifier | Not empty |
| criteria.sectionRules[].minScore | Integer | Yes | Minimum score for section | >= 0 |
| criteria.penalties | Array[Object] | No | Penalty configurations | Valid array of penalties |
| criteria.penalties[].type | String | Yes | Type of penalty | Enum: UNANSWERED, WRONG_ANSWER |
| criteria.penalties[].deduct | Integer/Decimal | Yes | Points to deduct | >= 0 |
| metadata | Object | No | Additional metadata (e.g., vehicleType) | Must be valid JSON object |

### Response

- Success Status: 201 Created

**Response Body**

| Name | Type | Description |
| :--- | :--- | :--- |
| id | UUID | Unique identifier of the created criteria |
| title | String | Title of the criteria |
| status | String | Current status (`ACTIVE`) |

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| VALIDATION_ERROR | 400 Bad Request | Invalid input payload | Invalid request parameters |
| UNAUTHORIZED | 401 Unauthorized | Missing or invalid authentication | Please authenticate |
| FORBIDDEN | 403 Forbidden | Missing `exam_criteria:create` permission | You do not have permission |

---

## Part 2 — Processing Specification

### Controller Layer
1. Validate incoming JSON payload.
2. Delegate creation request to Service layer.

### Service Layer
1. Extract user authentication information.
2. Validate user has `exam_criteria:create` permission.
3. Validate business logic:
    - `criteria.passScore` >= 0 and `criteria.totalScore` >= `criteria.passScore`.
    - `criteria.gradingMethod` must be one of [SUM, WEIGHTED, PERCENTAGE, BEST_OF, AVERAGE].
    - `criteria.rounding.precision` >= 0 if rounding is provided.
    - `criteria.mandatoryRules` types must be valid enums.
    - `criteria.penalties` deduct value must be >= 0.
4. Prepare new `ExamCriteria` entity:
    - Set `status` = `ACTIVE`.
    - Generate unique UUID.
    - Set `created_by` to the current user ID.
5. Delegate to Repository to persist.
6. Return created `ExamCriteria` information.

### Repository Layer
1. Insert new record into `exam_criterias` table.

### External Interaction
None

### Validation
- Request Validation: Basic data types, length constraints (title max 255), JSON structure.
- Permission Validation: Requires `exam_criteria:create`.
- Business Validation: Constraint checks on `criteria` object properties (score ranges, weights sum).

---

## Part 3 — Data Interaction

- Operation Type: INSERT
- Target Table: `exam_criterias`
- Expected Result: A new record is created with `status` = 'ACTIVE', `created_at` populated.

---

## Part 4 — Operational Notes

- **Idempotency**: POST operations are not idempotent by default, creating a new record each time.
- **Tenant Isolation**: Not Applicable
- **Retry Strategy**: Not Applicable
- **Audit Logging**: `created_by` field is maintained.
- **Monitoring**: Standard API latency and error rates tracking.
- **Metrics**: Count of created criteria.
- **Tracing**: Request id propagation via standard `traceId`.
