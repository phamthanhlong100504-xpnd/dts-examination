# 36. Resume Session API Blueprint

## Part 0 — Classification & Identity

- **API Name**: Resume Session
- **API Type**: Public
- **Module**: Exam Session Runtime
- **Feature**: Session Control
- **Description**: Resumes a previously paused exam session, reactivating the timer based on the remaining time.
- **Related Tables**: `exam_sessions`
- **Related Services**: None

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: PATCH
- **URL**: `/api/v1/exam-sessions/{sessionId}/resume`
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
| `status` | String | `IN_PROGRESS` |
| `resumedAt` | Timestamp | Time when the session was resumed |
| `expiredAt` | Timestamp | The new recalculated expiration time |
| `remainingSeconds` | Integer | Seconds left |

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | User doesn't own this session | Access denied |
| `NF_001` | 404 Not Found | Session does not exist | Exam session not found |
| `BUS_010` | 409 Conflict | Session cannot be resumed because ExamRule forbids it | Resume is not allowed |
| `BUS_011` | 409 Conflict | Session is not READY | Cannot resume session in current state |

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
   - Check `ExamSession.status`. Must be `READY`. Throw `BUS_011` if not.
   - Load `ExamRule`.
   - Check if `ExamRule.allowResume` is true. Throw `BUS_010` if false.
3. **Transaction Boundary**: Start database transaction.
4. **Business Workflow**:
   - Calculate `remainingSeconds` (this would be based on the frozen `expired_at` from the pause step minus the time of pause, or based on `duration_seconds` minus elapsed active time). Given the blueprint logic: "Tính remainingSeconds từ expired_at - pausedAt".
   - Set `ExamSession.status = IN_PROGRESS`.
   - Recalculate and set `ExamSession.expired_at = now + remainingSeconds`.
   - Commit transaction.
5. Return the response.

---

### Repository Layer
- `ExamSessionRepository`: Fetch session, Update session.

---

### External Interaction
- None

---

### Validation
- **Request Validation**: Valid UUID.
- **Business Validation**: Session must be READY, rule allows resume.
- **Permission Validation**: Ownership required.

---

## Part 3 — Data Interaction

- **Operation Type**: SELECT
- **Target Table**: `exam_sessions`
- **Conditions**: `id = :sessionId`
- **Expected Result**: 1 row.

- **Operation Type**: UPDATE
- **Target Table**: `exam_sessions`
- **Conditions**: `id = :sessionId`
- **Expected Result**: 1 row updated (status changed to `IN_PROGRESS`).

---

## Part 4 — Operational Notes

- **Idempotency**: Yes. If already `IN_PROGRESS`, reject or do nothing.
- **Tenant Isolation**: Not Specified.
- **Retry Strategy**: None.
- **Audit Logging**: Log resume action.
- **Monitoring**: N/A.
- **Metrics**: N/A.
- **Tracing**: N/A.
