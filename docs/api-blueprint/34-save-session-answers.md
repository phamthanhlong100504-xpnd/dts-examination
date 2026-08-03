# 34. Save Session Answers API Blueprint

## Part 0 — Classification & Identity

- **API Name**: Save Session Answers
- **API Type**: Public
- **Module**: Exam Session Runtime
- **Feature**: Answer Persistence
- **Description**: Saves or updates the candidate's answers for specific questions. This API is used for real-time autosave and manual save actions. It is highly idempotent and does not evaluate correctness.
- **Related Tables**: `exam_sessions`, `exam_session_answers`
- **Related Services**: None

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: POST
- **URL**: `/api/v1/exam-sessions/{sessionId}/answers`
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

#### Request Body

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| `answers` | Array | Yes | List of answers to save | Must not be empty |
| `answers[].questionId` | UUID | Yes | ID of the question | Must be a valid UUID |
| `answers[].selectedAnswer` | Object | Yes | The user's answer | Must be a valid JSON object following answer schemas (e.g. `single_choice`, `text`) |
| `answers[].selectedAnswer.type` | String | Yes | Answer type | e.g. `single_choice`, `multiple_choice`, `text` |

---

### Response

- **Success Status**: 200 OK

#### Response Body

| Name | Type | Description |
| :--- | :--- | :--- |
| `savedAt` | Timestamp | Server time when the answers were persisted |
| `updatedQuestions` | Integer | Number of answers successfully updated |

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | User doesn't own this session | Access denied |
| `VAL_001` | 400 Bad Request | Payload validation failed | Invalid input data |
| `NF_001` | 404 Not Found | Session does not exist | Exam session not found |
| `BUS_005` | 409 Conflict | Session has expired | Session Expired |
| `BUS_007` | 409 Conflict | Session is not IN_PROGRESS | Cannot save answers for this session |

---

## Part 2 — Processing Specification

### Controller Layer
1. Authenticate request via `Authorization` header.
2. Validate payload.
3. Delegate to Service Layer.

---

### Service Layer

1. **Permission Validation**: Verify the user is authenticated.
2. **Business Validation**:
   - Fetch the `ExamSession` using `sessionId`. Throw `NF_001` if missing.
   - **Ownership Check**: Ensure `user_id` matches current user. Throw `AUTH_003` if not.
   - Check `ExamSession.status`. Must be `IN_PROGRESS`. Throw `BUS_007` if not.
   - Check if current time exceeds `expired_at`. If so, change status to `EXPIRED` and throw `BUS_005`.
3. **Transaction Boundary**: Start database transaction.
4. **Business Workflow**:
   - For each answer in the `answers` array payload:
     - Find the corresponding record in `exam_session_answers` by `questionId` and `sessionId`.
     - Update `selected_answer` with the payload object.
     - Update `answered_at` to `now`.
   - Commit transaction.
5. Return the save confirmation with the count of updated rows.

---

### Repository Layer
- `ExamSessionRepository`: Fetch session by ID.
- `ExamSessionAnswerRepository`: Update `selected_answer` and `answered_at` for specific `questionId`s within the session.

---

### External Interaction
- None

---

### Validation
- **Request Validation**: Valid UUID, Non-empty array, Valid answer schemas.
- **Business Validation**: Active `IN_PROGRESS` session, not expired.
- **Permission Validation**: Ownership required.

---

## Part 3 — Data Interaction

- **Operation Type**: SELECT
- **Target Table**: `exam_sessions`
- **Conditions**: `id = :sessionId`
- **Expected Result**: 1 row.

- **Operation Type**: UPDATE
- **Target Table**: `exam_session_answers`
- **Conditions**: `exam_session_id = :sessionId AND question_id IN (:questionIds)`
- **Expected Result**: K rows updated (K = length of payload).
- **Performance Notes**: Use batch update for performance if a single payload contains many answers.

---

## Part 4 — Operational Notes

- **Idempotency**: Fully idempotent. Sending the exact same answer multiple times simply overwrites the JSONB field with the same data.
- **Tenant Isolation**: Not Specified.
- **Retry Strategy**: Client should automatically retry on network failures.
- **Audit Logging**: Do NOT log the answer payload to avoid log bloat.
- **Monitoring**: Ensure latency is low since this is called frequently for autosaving.
- **Metrics**: Track average payload size and save latency.
- **Tracing**: N/A.
