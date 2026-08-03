# 38. Get Exam Result API Blueprint

## Part 0 — Classification & Identity

- **API Name**: Get Exam Result
- **API Type**: Public
- **Module**: Exam Session Runtime
- **Feature**: History & Results
- **Description**: Retrieves the result and detailed breakdown of a submitted exam session. Used for reviewing the exam after completion.
- **Related Tables**: `exam_sessions`, `exam_session_answers`
- **Related Services**: None

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: GET
- **URL**: `/api/v1/exam-sessions/{sessionId}/result`
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
| `status` | String | `SUBMITTED` |
| `submittedAt` | Timestamp | Time when the session was submitted |
| `summary` | Object | Summary of the exam results |
| `answers` | Array | Detailed array of all questions and the user's answers |
| `answers[].questionId` | UUID | Original ID of the question |
| `answers[].display` | Object | Snapshot of how the question was displayed |
| `answers[].selectedAnswer` | Object | The user's answer |
| `answers[].isCorrect` | Boolean | True if correct, False if wrong, Null if ungraded |
| `answers[].score` | Numeric | Score awarded for this question |

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | User doesn't own this session | Access denied |
| `NF_001` | 404 Not Found | Session does not exist | Exam session not found |
| `BUS_014` | 409 Conflict | Session is not SUBMITTED | Exam is not submitted yet |
| `BUS_015` | 403 Forbidden | ExamRule forbids showing result immediately | Result is hidden until exam period ends |

---

## Part 2 — Processing Specification

### Controller Layer
1. Authenticate request via `Authorization` header.
2. Delegate to Service Layer.

---

### Service Layer

1. **Permission Validation**: Verify the user is authenticated.
2. **Business Validation**:
   - Fetch the `ExamSession` using `sessionId`. Throw `NF_001` if missing.
   - **Ownership Check**: Ensure `user_id` matches current user.
   - Check `ExamSession.status`. Must be `SUBMITTED`. Throw `BUS_014` if not.
   - Load `ExamRule`.
   - Check if `ExamRule.showResultImmediately` is true. If false, verify if the overall exam publish period has ended. If not, throw `BUS_015`.
3. **Transaction Boundary**: Read-only transaction.
4. **Business Workflow**:
   - Fetch all `exam_session_answers` for this session.
   - Calculate the `summary` object dynamically based on the DB records (Count correct, wrong, sum score). Note: The summary isn't persisted in `exam_sessions`, it's derived from `exam_session_answers`.
   - Map the answers array, preserving the `display_snapshot`.
5. Return the result object.

---

### Repository Layer
- `ExamSessionRepository`: Fetch session.
- `ExamSessionAnswerRepository`: Fetch all answers.

---

### External Interaction
- None. (This API does not fetch question content, only the grading result. If the client needs the question text to render the review screen, the client can use the Question Bank API separately, or we could include the batch fetch here. Assuming per blueprint it only returns answer grading state).

---

### Validation
- **Request Validation**: Valid UUID.
- **Business Validation**: Session must be SUBMITTED. Rule must allow showing results.
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

---

## Part 4 — Operational Notes

- **Idempotency**: Read-only API, highly idempotent.
- **Tenant Isolation**: Not Specified.
- **Retry Strategy**: Safe to retry.
- **Audit Logging**: None.
- **Monitoring**: N/A.
- **Metrics**: N/A.
- **Tracing**: N/A.
