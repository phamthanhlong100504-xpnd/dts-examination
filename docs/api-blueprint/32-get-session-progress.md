# 32. Get Session Progress API Blueprint

## Part 0 — Classification & Identity

- **API Name**: Get Session Progress
- **API Type**: Public
- **Module**: Exam Session Runtime
- **Feature**: Session Initialization
- **Description**: A lightweight polling API for frontend applications to fetch the real-time progress of an active exam session (e.g., remaining time, number of answered questions). This avoids calling the heavier Get Session Detail API repeatedly.
- **Related Tables**: `exam_sessions`, `exam_session_answers`
- **Related Services**: None

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: GET
- **URL**: `/api/v1/exam-sessions/{sessionId}/progress`
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
| `answeredQuestions` | Integer | Count of questions that have a `selected_answer` |
| `totalQuestions` | Integer | Total number of questions in this session |
| `remainingSeconds` | Integer | Seconds left until `expiredAt`. `0` if expired or submitted |

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
3. **Transaction Boundary**: Start database transaction (read-only is acceptable here if we want to delay expiration mutation to the next submit/detail call to keep this API fast).
4. **Business Workflow**:
   - Calculate `remainingSeconds`:
     - If status is `IN_PROGRESS`: `remainingSeconds` = `expired_at` - `now`. (Floor at 0).
     - Otherwise: `remainingSeconds` = 0.
   - Query `exam_session_answers` for this `sessionId`:
     - Count total rows to get `totalQuestions`.
     - Count rows where `selected_answer IS NOT NULL` to get `answeredQuestions`.
   - Commit transaction.
5. Map data to the response DTO and return.

---

### Repository Layer
- `ExamSessionRepository`: Fetch session summary (just id, user_id, status, expired_at) using projection for performance.
- `ExamSessionAnswerRepository`: 
  - `countByExamSessionId(UUID sessionId)`
  - `countByExamSessionIdAndSelectedAnswerIsNotNull(UUID sessionId)`

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
- **Expected Result**: 1 row (ideally using a Projection to only load `user_id`, `status`, `expired_at`).
- **Performance Notes**: Highly optimized lookup since it's a polling endpoint.

- **Operation Type**: SELECT
- **Target Table**: `exam_session_answers`
- **Conditions**: `exam_session_id = :sessionId`
- **Expected Result**: Aggregate count (`totalQuestions`).
- **Performance Notes**: Should leverage index on `exam_session_id`.

- **Operation Type**: SELECT
- **Target Table**: `exam_session_answers`
- **Conditions**: `exam_session_id = :sessionId AND selected_answer IS NOT NULL`
- **Expected Result**: Aggregate count (`answeredQuestions`).
- **Performance Notes**: Should leverage index on `exam_session_id`.

---

## Part 4 — Operational Notes

- **Idempotency**: This API is idempotent and read-only.
- **Tenant Isolation**: Handled via standard DB boundaries if applicable.
- **Retry Strategy**: Standard HTTP GET retries are safe. Frontend timers should handle fetch failures gracefully.
- **Audit Logging**: Do NOT log every request to avoid log spam, as this is a high-frequency polling endpoint.
- **Monitoring**: Ensure P99 latency is under 50ms due to high volume polling.
- **Metrics**: Track API latency and throughput.
- **Tracing**: Limit trace sampling for this specific path to avoid trace bloat.
