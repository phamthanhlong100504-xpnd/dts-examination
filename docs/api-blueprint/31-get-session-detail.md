# 31. Get Session Detail API Blueprint

## Part 0 — Classification & Identity

- **API Name**: Get Session Detail
- **API Type**: Public
- **Module**: Exam Session Runtime
- **Feature**: Session Initialization
- **Description**: Retrieves the runtime status of an exam session. This API does not return the exam content (questions/answers), but rather the metadata and current progress (e.g., remaining time, status). It also dynamically evaluates the timeout and triggers status updates if the session has expired.
- **Related Tables**: `exam_sessions`, `exam_session_answers`
- **Related Services**: None

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: GET
- **URL**: `/api/v1/exam-sessions/{sessionId}`
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
| `sessionId` | UUID | Yes | ID of the target session | Must be a valid UUID |

---

### Response

- **Success Status**: 200 OK

#### Response Body

| Name | Type | Description |
| :--- | :--- | :--- |
| `sessionId` | UUID | The ID of the session |
| `examVersionId` | UUID | The exam version being taken |
| `attemptNo` | Integer | The attempt number |
| `status` | String | Current status of the session (`IN_PROGRESS`, `SUBMITTED`, `EXPIRED`, `CANCELLED`, `READY`) |
| `startedAt` | Timestamp | Time when the session officially started |
| `expiredAt` | Timestamp | Time when the session is forcefully expired |
| `durationSeconds` | Integer | Total duration allowed for the exam in seconds |
| `remainingSeconds` | Integer | Seconds left until `expiredAt`. `0` if expired or submitted |
| `answeredQuestions` | Integer | Count of questions that have a `selected_answer` |
| `totalQuestions` | Integer | Total number of questions in this session |

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | User doesn't own this session or lacks permission | Access denied |
| `NF_001` | 404 Not Found | Session does not exist | Exam session not found |

---

## Part 2 — Processing Specification

### Controller Layer
1. Authenticate request via `Authorization` header.
2. Validate path variables against JSR-380 annotations.
3. Extract `userId` from the security context.
4. Delegate to Service Layer.

---

### Service Layer

1. **Permission Validation**: Verify the user is authenticated.
2. **Business Validation**:
   - Fetch the `ExamSession` using `sessionId`. Throw `NF_001` if missing.
   - **Ownership Check**: Ensure `exam_session.user_id` matches the current `userId`. Throw `AUTH_003` if it does not match.
3. **Transaction Boundary**: Start database transaction.
4. **Business Workflow**:
   - If `ExamSession.status` is `IN_PROGRESS`:
     - Calculate `remainingSeconds` = `expired_at` - `now`.
     - If `remainingSeconds` <= 0:
       - Update `ExamSession.status` to `EXPIRED`.
       - Update the entity in the database.
       - Set `remainingSeconds` to 0.
   - If status is `SUBMITTED`, `EXPIRED` or `CANCELLED`, set `remainingSeconds` = 0.
   - Query `exam_session_answers` for this `sessionId`:
     - Count total rows to get `totalQuestions`.
     - Count rows where `selected_answer IS NOT NULL` to get `answeredQuestions`.
   - Commit transaction.
5. Map data to the response DTO and return.

---

### Repository Layer
- `ExamSessionRepository`: Fetch session by ID. Update status if expired.
- `ExamSessionAnswerRepository`: 
  - `countByExamSessionId(UUID sessionId)` -> returns `totalQuestions`.
  - `countByExamSessionIdAndSelectedAnswerIsNotNull(UUID sessionId)` -> returns `answeredQuestions`.

---

### External Interaction
- None

---

### Validation
- **Request Validation**: Valid UUID for `sessionId`.
- **Business Validation**: Must exist.
- **Permission Validation**: Strict ownership validation (`user_id == current_user.id`).

---

## Part 3 — Data Interaction

- **Operation Type**: SELECT
- **Target Table**: `exam_sessions`
- **Conditions**: `id = :sessionId`
- **Expected Result**: 1 row.

- **Operation Type**: UPDATE
- **Target Table**: `exam_sessions`
- **Conditions**: `id = :sessionId AND status = 'IN_PROGRESS' AND expired_at <= :now`
- **Expected Result**: 1 row updated (status changed to `EXPIRED`).

- **Operation Type**: SELECT
- **Target Table**: `exam_session_answers`
- **Conditions**: `exam_session_id = :sessionId`
- **Expected Result**: Aggregate count (`totalQuestions`).

- **Operation Type**: SELECT
- **Target Table**: `exam_session_answers`
- **Conditions**: `exam_session_id = :sessionId AND selected_answer IS NOT NULL`
- **Expected Result**: Aggregate count (`answeredQuestions`).

---

## Part 4 — Operational Notes

- **Idempotency**: This API is a read operation with side effects (expiration check). It is idempotent from the client's perspective (multiple calls return the same logical state).
- **Tenant Isolation**: Handled via standard DB boundaries if applicable.
- **Retry Strategy**: Standard HTTP GET retries are safe.
- **Audit Logging**: None required for read operations unless debugging timeout issues.
- **Monitoring**: Watch for slow queries on the `exam_session_answers` count operations if the table grows large.
- **Metrics**: Track API latency.
- **Tracing**: N/A.
