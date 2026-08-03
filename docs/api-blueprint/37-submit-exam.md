# 37. Submit Exam API Blueprint

## Part 0 — Classification & Identity

- **API Name**: Submit Exam
- **API Type**: Public
- **Module**: Exam Session Runtime
- **Feature**: Submission & Evaluation
- **Description**: Submits the exam session and evaluates the candidate's answers against the correct answers fetched from the Question Bank Service. Changes the session status to `SUBMITTED`.
- **Related Tables**: `exam_sessions`, `exam_session_answers`
- **Related Services**: Question Bank Service

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: POST
- **URL**: `/api/v1/exam-sessions/{sessionId}/submit`
- **Content Type**: `application/json`

---

### Request

#### Headers

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| `Authorization` | String | Yes | Bearer token for authentication | Must be a valid JWT |
| `Idempotency-Key` | String | Yes | Unique key to prevent double-submission | Must be a UUID or unique hash |

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
| `status` | String | `SUBMITTED` |
| `submittedAt` | Timestamp | Time when the session was successfully submitted |
| `summary` | Object | Summary of the exam results |
| `summary.totalQuestions` | Integer | Total questions in the exam |
| `summary.answeredQuestions` | Integer | Number of questions answered |
| `summary.correctQuestions` | Integer | Number of correct answers |
| `summary.wrongQuestions` | Integer | Number of wrong answers |
| `summary.unansweredQuestions` | Integer | Number of unanswered questions |
| `summary.score` | Numeric | Total score achieved |
| `summary.result` | String | Overall result (`PASS` or `FAIL` based on ExamRule) |

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | User doesn't own this session | Access denied |
| `VAL_001` | 400 Bad Request | Missing Idempotency-Key | Missing Idempotency Key |
| `NF_001` | 404 Not Found | Session does not exist | Exam session not found |
| `BUS_012` | 409 Conflict | Session already submitted | Session has already been submitted |
| `BUS_013` | 409 Conflict | Session is EXPIRED and autoSubmit is false | Exam time has expired, cannot submit manually |

---

## Part 2 — Processing Specification

### Controller Layer
1. Authenticate request via `Authorization` header.
2. Validate presence of `Idempotency-Key` header.
3. Delegate to Service Layer.

---

### Service Layer

1. **Permission Validation**: Verify the user is authenticated.
2. **Business Validation**:
   - Fetch the `ExamSession` using `sessionId`. Throw `NF_001` if missing.
   - **Ownership Check**: Ensure `user_id` matches current user.
   - If `idempotency_key` in DB matches the request header, return the existing result (Early return for retry).
   - Check `ExamSession.status`:
     - If `SUBMITTED` or `CANCELLED`, throw `BUS_012`.
     - If `EXPIRED`: Check `ExamRule.autoSubmit`. If `false`, throw `BUS_013`. If `true`, proceed.
3. **Transaction Boundary**: Start database transaction.
4. **Business Workflow**:
   - Load all `exam_session_answers` for this session.
   - **External Interaction (Batch)**: Call the Question Bank Service (`POST /internal/questions/batch/evaluate` or similar, or just fetch correct answers and evaluate locally). Assuming we fetch correct answers and evaluate locally: call `POST /internal/questions/batch` to get correct answers.
   - Iterate through each answer:
     - Compare `selected_answer` with the correct answer from Question Bank.
     - Determine `is_correct` (TRUE/FALSE).
     - Calculate `score` for the question based on correctness and question weight.
     - Update the `exam_session_answer` record.
   - Aggregate statistics:
     - Count total, answered, correct, wrong, unanswered.
     - Sum `score`.
   - Evaluate PASS/FAIL condition based on `ExamRule.passMark` or `passScore`.
   - Update `ExamSession`:
     - `status = SUBMITTED`
     - `submitted_at = now`
     - `idempotency_key = request.idempotencyKey`
   - Commit transaction.
5. Return the result summary.

---

### Repository Layer
- `ExamSessionRepository`: Fetch session, update status, idempotency_key.
- `ExamSessionAnswerRepository`: Fetch all answers, update `is_correct` and `score` for all.

---

### External Interaction
- **REST/gRPC to Question Bank Service**: Fetch correct answers/grading metadata for a batch of `questionId`s.

---

### Validation
- **Request Validation**: Valid UUID, Idempotency key present.
- **Business Validation**: Active session, not already submitted.
- **Permission Validation**: Ownership required.

---

## Part 3 — Data Interaction

- **Operation Type**: SELECT
- **Target Table**: `exam_sessions`
- **Conditions**: `id = :sessionId`
- **Expected Result**: 1 row.

- **Operation Type**: SELECT
- **Target Table**: `exam_session_answers`
- **Conditions**: `exam_session_id = :sessionId`
- **Expected Result**: N rows.

- **Operation Type**: UPDATE
- **Target Table**: `exam_session_answers`
- **Conditions**: `id = :answerId`
- **Expected Result**: N rows updated (setting `is_correct` and `score`).
- **Performance Notes**: Use batch update for performance.

- **Operation Type**: UPDATE
- **Target Table**: `exam_sessions`
- **Conditions**: `id = :sessionId`
- **Expected Result**: 1 row updated (status changed to `SUBMITTED`, `idempotency_key` set).

---

## Part 4 — Operational Notes

- **Idempotency**: Strictly enforced via the `Idempotency-Key` header and database unique constraint.
- **Tenant Isolation**: Not Specified.
- **Retry Strategy**: Client should automatically retry network timeouts using the exact same `Idempotency-Key`.
- **Audit Logging**: Log the submission action and final score.
- **Monitoring**: Monitor transaction duration, as evaluating 100 questions could take time.
- **Metrics**: Track average grading time and batch call latency.
- **Tracing**: Trace the grading flow and Question Bank call.
