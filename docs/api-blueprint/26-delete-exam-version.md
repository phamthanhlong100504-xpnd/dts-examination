# Examination Service – Delete Exam Version

## Part 1 — API Specification

### 1.1 Endpoint Overview
- **Path**: `DELETE /api/v1/exam-versions/{versionId}`
- **Method**: `DELETE`
- **Purpose**: Soft delete an exam version. A version cannot be deleted if it is `PUBLISHED` or if it has active/past `exam_sessions`.

### 1.2 Request Specification

#### Headers
- `Authorization`: `Bearer <token>`

#### Path Variables
| Name | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `versionId` | UUID | Yes | Unique identifier of the exam version |

### 1.3 Response Specification

- **Success Status**: 204 No Content

### 1.4 Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | Lacks `exam_version:delete` permission | Access denied |
| `NF_001` | 404 Not Found | Version does not exist | Exam version not found |
| `BUS_003` | 409 Conflict | Version is published | Cannot delete a PUBLISHED version |
| `BUS_004` | 409 Conflict | Has exam sessions | Cannot delete version because it has been taken by users |

---

## Part 2 — Processing Specification

### Controller Layer
1. Authenticate request via `Authorization` header.
2. Delegate to Service Layer.

### Service Layer
1. **Permission Validation**: Verify the user has `exam_version:delete` permission.
2. **Business Validation**: 
   - Fetch target version. Throw 404 if missing or already deleted.
   - If version `status` is `PUBLISHED`, throw 409 Conflict (`BUS_003`).
   - Query `exam_sessions` table (or call exam-session service/repository). If any session exists for this `versionId`, throw 409 Conflict (`BUS_004`). *(Note: Implement via ExamSessionRepository)*
3. **Transaction Boundary**: Start database transaction.
4. **Business Workflow**:
   - Set `deleted_at` to current timestamp.
   - Set `updated_by` to authenticated user ID.
5. Save the entity via Repository.
6. Commit transaction.
7. Return void.

### Repository Layer
1. Query `exam_versions` to verify status.
2. Query `exam_sessions` (or equivalent) to check for existence of sessions.
3. Update `deleted_at` timestamp in `exam_versions`.

### External Interaction
None.

---

## Part 3 — Data Interaction

- **Operation Type**: UPDATE (Soft Delete)
- **Target Table**: `exam_versions`
- **Conditions**: `id = :versionId`
- **Expected Result**: Row is marked as deleted.
