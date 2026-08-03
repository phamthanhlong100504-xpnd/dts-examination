# 35. Pause Session API Blueprint

## Part 0 — Classification & Identity

- **API Name**: Pause Session
- **API Type**: Public
- **Module**: Exam Session Runtime
- **Feature**: Session Control
- **Description**: Pauses an active exam session, freezing the remaining time. Only allowed if the ExamRule explicitly permits pausing.
- **Related Tables**: `exam_sessions`
- **Related Services**: None

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: PATCH
- **URL**: `/api/v1/exam-sessions/{sessionId}/pause`
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
| `status` | String | `READY` |
| `pausedAt` | Timestamp | Time when the session was paused |
| `remainingSeconds` | Integer | Seconds left (frozen) |

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | User doesn't own this session | Access denied |
| `NF_001` | 404 Not Found | Session does not exist | Exam session not found |
| `BUS_008` | 409 Conflict | Session cannot be paused because ExamRule forbids it | Pause is not allowed |
| `BUS_009` | 409 Conflict | Session is not IN_PROGRESS | Cannot pause session in current state |

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
   - Check `ExamSession.status`. Must be `IN_PROGRESS`. Throw `BUS_009` if not.
   - Load `ExamRule` linked to the `ExamVersion`.
   - Check if `ExamRule.allowPause` is true. Throw `BUS_008` if false.
   - Check if session has expired. If so, fail the pause request and change status to `EXPIRED`.
3. **Transaction Boundary**: Start database transaction.
4. **Business Workflow**:
   - Calculate `remainingSeconds = expired_at - now`.
   - Set `ExamSession.status = READY`.
   - Set `ExamSession.expired_at = now + remainingSeconds` (essentially freezing it by sliding the expiration window upon resume, but saving the exact relative offset in `expired_at` for DB logic). Wait, a better approach is to store `remainingSeconds` or shift `expired_at` when `RESUME` is called. Assuming we shift `expired_at = now + remainingSeconds`.
   - Actually, to freeze time properly: when paused, we should calculate `remainingSeconds` and then we can update `expired_at` upon *resume*. For the pause step, just set `status = READY`. We can leave `expired_at` as is, and compute the delta during resume. Alternatively, set `duration_seconds` to track remaining. Let's stick to the blueprint definition: "Cập nhật expired_at = now + remainingSeconds". (This means `expired_at` temporarily points to a future time based on current time, which acts as a holder). 
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
- **Business Validation**: Active session, rule allows pause.
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
- **Expected Result**: 1 row updated (status changed to `READY`).

---

## Part 4 — Operational Notes

- **Idempotency**: Yes. If already `READY`, reject or do nothing.
- **Tenant Isolation**: Not Specified.
- **Retry Strategy**: None.
- **Audit Logging**: Log pause action.
- **Monitoring**: N/A.
- **Metrics**: N/A.
- **Tracing**: N/A.
