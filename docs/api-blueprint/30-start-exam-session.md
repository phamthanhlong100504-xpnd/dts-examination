# 30. Start Exam Session API Blueprint

## Part 0 — Classification & Identity

- **API Name**: Start Exam Session
- **API Type**: Public
- **Module**: Exam Session Runtime
- **Feature**: Session Initialization
- **Description**: Creates a new exam session (`exam_session`) and initializes all answer records (`exam_session_answers`). This is the entry point for starting an exam.
- **Related Tables**: `exam_sessions`, `exam_session_answers`, `exam_versions`, `exam_rules`, `exam_structures`
- **Related Services**: Question Bank Service (to fetch question IDs based on content config)

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: POST
- **URL**: `/api/v1/exam-sessions`
- **Content Type**: `application/json`

---

### Request

#### Headers

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| `Authorization` | String | Yes | Bearer token for authentication | Must be a valid JWT containing the permission `exam_session:create` |

#### Request Body

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| `examVersionId` | UUID | Yes | ID of the exam version to take | Must be a valid UUID. Must exist in `exam_versions` and have status `PUBLISHED` |
| `clientInfo` | Object | No | Information about the client's device/browser | Must be a valid JSON object if provided |
| `clientInfo.device` | String | No | Device identifier | - |
| `clientInfo.platform` | String | No | OS or platform identifier | - |
| `clientInfo.ip` | String | No | Client IP address | - |

---

### Response

- **Success Status**: 201 Created

#### Response Body

| Name | Type | Description |
| :--- | :--- | :--- |
| `sessionId` | UUID | The ID of the newly created session |
| `examVersionId` | UUID | The exam version being taken |
| `attemptNo` | Integer | The attempt number for this user and this exam version |
| `status` | String | Initial status of the session (`IN_PROGRESS`) |
| `startedAt` | Timestamp | Time when the session officially started |
| `expiredAt` | Timestamp | Time when the session will be forcefully expired |
| `durationSeconds` | Integer | Total duration allowed for the exam in seconds |

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | User lacks necessary permission or role | Access denied |
| `VAL_001` | 400 Bad Request | Payload validation failed | Invalid input data |
| `NF_001` | 404 Not Found | Target exam version does not exist | Exam version not found |
| `BUS_001` | 409 Conflict | Target exam version is not published | Exam version is not published |
| `BUS_002` | 409 Conflict | Exam is not currently active based on `started_at` and `ended_at` | Exam is not within active period |
| `BUS_003` | 409 Conflict | User has exceeded `maxRetry` limit for this exam | Maximum attempts exceeded |
| `BUS_004` | 409 Conflict | User already has an `IN_PROGRESS` session and parallel attempts are not allowed | An active session already exists |

---

## Part 2 — Processing Specification

### Controller Layer
1. Authenticate request via `Authorization` header.
2. Validate JSON payload against JSR-380 annotations.
3. Extract `userId` from the security context.
4. Delegate to Service Layer.

---

### Service Layer

1. **Permission Validation**: Verify the user is authenticated and has permission to start an exam (`PERM_exam_session:create`). *(Lưu ý: Tạm thời vô hiệu hóa ở Controller trong môi trường dev local để dễ test)*.
2. **Business Validation**:
   - Fetch the `ExamVersion` using `examVersionId`. Throw `NF_001` if missing.
   - Check if `ExamVersion.status` is `PUBLISHED`. Throw `BUS_001` if not.
   - Check if the current time is between `ExamVersion.started_at` and `ExamVersion.ended_at` (if configured). Throw `BUS_002` if not.
   - Load the corresponding `ExamRule` linked to the version.
   - Count the number of existing sessions for this user and exam version to determine `attemptNo`.
   - Check if `attemptNo` exceeds `ExamRule.maxRetry`. Throw `BUS_003` if exceeded.
   - Check for any existing session for this user with status `IN_PROGRESS` on this exam version (assuming parallel sessions aren't allowed). Throw `BUS_004` if found.
3. **Business Workflow**:
   - Calculate `attempt_no` = existing session count + 1.
   - Fetch the `ExamStructure` and resolve the list of target `question_id`s from the Question Bank service using the `contentId` and `contentType` (e.g., specific Chapter or Learning Program).
   - Apply rules defined in `ExamRule`:
     - If `shuffleQuestion` is true, shuffle the resolved question list.
     - Generate the `display_snapshot` for each question (including position, section mapping, and shuffled `optionOrder` if `shuffleOption` is true).
   - Calculate `startedAt` = now.
   - Calculate `durationSeconds` based on `ExamRule.duration`.
   - Calculate `expiredAt` = `startedAt` + `durationSeconds`.
   - Generate a secure `sessionToken`.
4. **Transaction Boundary**: Start database transaction.
5. **Database Operations**:
   - Insert the new session into `exam_sessions`.
   - Insert ALL resolved questions into `exam_session_answers` with `selected_answer` as null.
   - Commit transaction.
6. Return the session metadata.

---

### Repository Layer
- `ExamVersionRepository`: Fetch exam version.
- `ExamSessionRepository`: Count previous attempts, check for active sessions, insert new session.
- `ExamSessionAnswerRepository`: Batch insert generated answer snapshots.

---

### External Interaction
- **REST/gRPC to Question Bank Service**: 
  - Need to fetch the list of `question_id`s and option counts based on the `contentType` and `contentId` in order to generate the structure and snapshots.

---

### Validation
- **Request Validation**: Valid UUID, non-null fields.
- **Business Validation**: Exam state check, timing window check, retry limit check, active session constraint check.
- **Permission Validation**: Must be a valid system user.

---

## Part 3 — Data Interaction

- **Operation Type**: SELECT
- **Target Table**: `exam_versions`
- **Conditions**: `id = :examVersionId`
- **Expected Result**: 1 row.

- **Operation Type**: SELECT
- **Target Table**: `exam_sessions`
- **Conditions**: `exam_version_id = :examVersionId AND user_id = :userId`
- **Expected Result**: Count of previous attempts.

- **Operation Type**: INSERT
- **Target Table**: `exam_sessions`
- **Conditions**: N/A
- **Expected Result**: 1 new session created with `status = 'IN_PROGRESS'`.

- **Operation Type**: INSERT
- **Target Table**: `exam_session_answers`
- **Conditions**: N/A
- **Expected Result**: N rows inserted (one for each question generated).
- **Performance Notes**: Use batch insert for `exam_session_answers` to optimize performance, as an exam might contain 50-100 questions.

---

## Part 4 — Operational Notes

- **Idempotency**: This API is NOT idempotent. Repeated calls will create multiple sessions if `maxRetry` allows it.
- **Tenant Isolation**: Tenant scoping is handled via standard multi-tenant boundaries (if applicable in the architecture).
- **Retry Strategy**: None required for the client unless it fails before transaction commit.
- **Audit Logging**: Log the creation of a new session, including the user, exam version, and client IP/Device.
- **Monitoring**: Monitor the failure rates (especially business constraint violations like max retry exceeded).
- **Metrics**: Track average exam generation time (specifically the call to Question Bank and DB batch inserts).
- **Tracing**: Pass tracing headers down to the Question Bank service call.
