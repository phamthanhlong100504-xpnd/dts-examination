# 33. Get Exam Paper API Blueprint

## Part 0 — Classification & Identity

- **API Name**: Get Exam Paper
- **API Type**: Public
- **Module**: Exam Session Runtime
- **Feature**: Paper Delivery
- **Description**: Retrieves the full exam paper for an active session. The questions are returned in the exact order they were generated (using the `display_snapshot`), preserving any shuffle rules applied when the session started.
- **Related Tables**: `exam_sessions`, `exam_session_answers`
- **Related Services**: Question Bank Service (to fetch question content)

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: GET
- **URL**: `/api/v1/exam-sessions/{sessionId}/paper`
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
| `status` | String | Current status of the session |
| `remainingSeconds` | Integer | Seconds left until `expiredAt` |
| `questions` | Array | List of questions in the exam paper |
| `questions[].questionId` | UUID | Original ID of the question |
| `questions[].display` | Object | Snapshot of how the question should be displayed |
| `questions[].display.position` | Integer | The sequence number of the question in the test |
| `questions[].display.sectionId` | String | (Optional) Section category |
| `questions[].content` | String | The actual text/content of the question |
| `questions[].options` | Array | List of options for the question (shuffled according to snapshot if applicable) |
| `questions[].options[].id` | String | Option ID |
| `questions[].options[].text` | String | Option text/content |
| `questions[].selectedAnswer` | Object | The answer currently selected by the user (if any) |

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | User doesn't own this session | Access denied |
| `NF_001` | 404 Not Found | Session does not exist | Exam session not found |
| `BUS_005` | 409 Conflict | Session has expired | Session Expired |
| `BUS_006` | 409 Conflict | Session is cancelled or submitted | Cannot get paper for this session state |

---

## Part 2 — Processing Specification

### Controller Layer
1. Authenticate request via `Authorization` header.
2. Validate path variables.
3. Extract `userId` from security context.
4. Delegate to Service Layer.

---

### Service Layer

1. **Permission Validation**: Verify the user is authenticated.
2. **Business Validation**:
   - Fetch the `ExamSession` using `sessionId`. Throw `NF_001` if missing.
   - **Ownership Check**: Ensure `exam_session.user_id` matches `userId`. Throw `AUTH_003` if not.
   - Check `ExamSession.status`. If `SUBMITTED`, `EXPIRED`, or `CANCELLED`, throw `BUS_006`.
3. **Transaction Boundary**: Start database transaction.
4. **Business Workflow**:
   - If `status = IN_PROGRESS`, calculate `remainingSeconds = expired_at - now`.
     - If `remainingSeconds <= 0`:
       - Change status to `EXPIRED`.
       - Commit and throw `BUS_005` (Session Expired).
   - Fetch all `exam_session_answers` for this `sessionId`.
   - Sort the answers based on `display_snapshot.position` (ASC).
   - Extract the list of unique `question_id`s from the answers.
   - **External Interaction (Batch)**: Call the Question Bank Service to fetch content for all extracted `question_id`s in one batch request.
   - Map the returned question content to the sorted answer records.
   - Apply `display_snapshot.optionOrder` to re-order the options for each question so that the client receives the exact shuffled layout determined at start time.
   - Attach any existing `selected_answer` from the DB record to the response.
5. Return the mapped exam paper.

---

### Repository Layer
- `ExamSessionRepository`: Fetch session by ID.
- `ExamSessionAnswerRepository`: Fetch all records by `exam_session_id`.

---

### External Interaction
- **REST/gRPC to Question Bank Service**:
  - `POST /internal/questions/batch` with a payload of `questionIds`.
  - This batch fetch prevents N+1 network calls and is critical for performance.

---

### Validation
- **Request Validation**: Valid UUID for `sessionId`.
- **Business Validation**: Must exist, user must own session, session must be active.
- **Permission Validation**: Strict ownership.

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
- **Target Table**: `exam_sessions`
- **Conditions**: `id = :sessionId` (Only if timeout detected)
- **Expected Result**: 1 row updated (status changed to `EXPIRED`).

---

## Part 4 — Operational Notes

- **Idempotency**: This API is idempotent (barring the edge case where it triggers expiration on read).
- **Tenant Isolation**: Not Specified.
- **Retry Strategy**: None required for the client unless timeout.
- **Audit Logging**: N/A.
- **Monitoring**: Monitor the response time of the batch call to the Question Bank service.
- **Metrics**: Track API latency.
- **Tracing**: Important to trace the cross-service call to Question Bank.
