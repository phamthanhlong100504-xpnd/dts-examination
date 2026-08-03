# 39. Get Session History API Blueprint

## Part 0 — Classification & Identity

- **API Name**: Get Session History Of Exam
- **API Type**: Public
- **Module**: Exam Session Runtime
- **Feature**: History & Results
- **Description**: Retrieves the history of exam sessions (attempts) for the current user for a specific exam. Provides a paginated list of sessions with their high-level summary.
- **Related Tables**: `exam_sessions`, `exam_session_answers`, `exam_versions`
- **Related Services**: None

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: GET
- **URL**: `/api/v1/exams/{examId}/sessions`
- **Content Type**: `application/json`

---

### Request

#### Headers

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| `Authorization` | String | Yes | Bearer token for authentication | Must be a valid JWT |

#### Path Variables

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| `examId` | UUID | Yes | ID of the target Exam | Must be a valid UUID |

#### Query Parameters

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| `page` | Integer | No | Page number (0-indexed) | Must be >= 0. Default is 0. |
| `size` | Integer | No | Page size | Must be > 0 and <= 100. Default is 20. |
| `status` | String | No | Filter by session status | e.g. `SUBMITTED`, `IN_PROGRESS` |
| `sort` | String | No | Sort order | e.g. `startedAt,desc` |

---

### Response

- **Success Status**: 200 OK

#### Response Body

| Name | Type | Description |
| :--- | :--- | :--- |
| `page` | Integer | Current page index |
| `size` | Integer | Number of items per page |
| `totalElements` | Integer | Total number of matched items |
| `totalPages` | Integer | Total pages |
| `items` | Array | List of session history records |
| `items[].sessionId` | UUID | The ID of the session |
| `items[].examVersionId` | UUID | The ID of the exam version taken |
| `items[].attemptNo` | Integer | The attempt number |
| `items[].status` | String | Current status of the session |
| `items[].startedAt` | Timestamp | Time when the session started |
| `items[].submittedAt` | Timestamp | Time when the session was submitted (if applicable) |
| `items[].summary` | Object | High-level summary (only populated if `SUBMITTED`) |
| `items[].summary.score` | Numeric | Total score achieved |
| `items[].summary.result` | String | Pass/Fail result |
| `items[].summary.correctQuestions` | Integer | Number of correct answers |
| `items[].summary.totalQuestions` | Integer | Total questions in the exam |

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `NF_001` | 404 Not Found | Target Exam does not exist | Exam not found |

---

## Part 2 — Processing Specification

### Controller Layer
1. Authenticate request via `Authorization` header.
2. Validate path variables and query parameters.
3. Delegate to Service Layer.

---

### Service Layer

1. **Permission Validation**: Verify the user is authenticated.
2. **Business Validation**:
   - Verify that the `Exam` exists using `examId`. Throw `NF_001` if missing.
3. **Transaction Boundary**: Read-only transaction.
4. **Business Workflow**:
   - Query `exam_sessions` joined with `exam_versions` to fetch sessions where `exam_versions.exam_id = :examId` AND `exam_sessions.user_id = :currentUserId`.
   - Apply pagination, sorting, and optional `status` filter.
   - For each returned session:
     - If the status is `SUBMITTED`, calculate the `summary` by aggregating data from `exam_session_answers`.
     - Otherwise, leave `summary` as null.
   - Construct the paginated response.
5. Return the response.

---

### Repository Layer
- `ExamRepository`: Verify exam existence.
- `ExamSessionRepository`: Fetch paginated sessions by `user_id` and `exam_id` (requires joining with `ExamVersion`).
- `ExamSessionAnswerRepository`: Fetch aggregates (count correct, sum score, etc.) for `SUBMITTED` sessions.

---

### External Interaction
- None

---

### Validation
- **Request Validation**: Valid UUID, valid pagination parameters.
- **Business Validation**: Exam must exist.
- **Permission Validation**: User only sees their own data.

---

## Part 3 — Data Interaction

- **Operation Type**: SELECT
- **Target Table**: `exams`
- **Conditions**: `id = :examId`
- **Expected Result**: 1 row (exists check).

- **Operation Type**: SELECT
- **Target Table**: `exam_sessions` (JOIN `exam_versions`)
- **Conditions**: `exam_versions.exam_id = :examId AND exam_sessions.user_id = :userId`
- **Expected Result**: Paginated result set.

- **Operation Type**: SELECT
- **Target Table**: `exam_session_answers`
- **Conditions**: `exam_session_id IN (:sessionIds)`
- **Expected Result**: Aggregated data to build the summary.
- **Performance Notes**: Use a `GROUP BY exam_session_id` query to fetch summaries for all sessions on the current page in a single DB trip to avoid N+1 queries.

---

## Part 4 — Operational Notes

- **Idempotency**: Read-only API, highly idempotent.
- **Tenant Isolation**: Not Specified.
- **Retry Strategy**: Safe to retry.
- **Audit Logging**: None.
- **Monitoring**: Watch performance of the summary aggregation query.
- **Metrics**: Track API latency.
- **Tracing**: N/A.
