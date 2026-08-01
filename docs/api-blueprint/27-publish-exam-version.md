# Examination Service – Publish Exam Version

## Part 1 — API Specification

### 1.1 Endpoint Overview
- **Path**: `POST /api/v1/exam-versions/{versionId}/publish`
- **Method**: `POST`
- **Purpose**: Publish a specific exam version. This action makes this version the single active (`PUBLISHED`) version for the parent exam.

### 1.2 Request Specification

#### Headers
- `Authorization`: `Bearer <token>`

#### Path Variables
| Name | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `versionId` | UUID | Yes | Unique identifier of the exam version |

### 1.3 Response Specification

- **Success Status**: 200 OK

#### Response Body
| Name | Type | Description |
| :--- | :--- | :--- |
| `id` | UUID | Unique identifier of the version |
| `status` | String | `PUBLISHED` |

### 1.4 Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | Lacks `exam_version:publish` permission | Access denied |
| `NF_001` | 404 Not Found | Version does not exist | Exam version not found |
| `BUS_005` | 409 Conflict | Invalid status transition | Only DRAFT versions can be published |

---

## Part 2 — Processing Specification

### Controller Layer
1. Authenticate request via `Authorization` header.
2. Delegate to Service Layer.

### Service Layer
1. **Permission Validation**: Verify the user has `exam_version:publish` permission.
2. **Business Validation**: 
   - Fetch target version. Throw 404 if missing.
   - If version `status` is not `DRAFT`, throw 409 Conflict.
   - Validate that referenced config IDs (`examStructureId`, etc.) still exist and are valid.
3. **Transaction Boundary**: Start database transaction.
4. **Business Workflow**:
   - Query for any currently `PUBLISHED` version belonging to the same `examId`.
   - If a published version exists, change its status to `ARCHIVED` and save.
   - Set the current version's `status` to `PUBLISHED`.
   - Fetch the parent `Exam` entity. Ensure `Exam` status is also `PUBLISHED` (or update it if needed).
   - Publish domain event `ExamVersionPublishedEvent` (if event-driven architecture is active).
5. Save entities via Repository.
6. Commit transaction.
7. Return basic DTO.

### Repository Layer
1. Query `exam_versions` by `id`.
2. Query `exam_versions` where `exam_id = :examId AND status = 'PUBLISHED'`.
3. Update `exam_versions` status.
4. Update `exams` status.

### External Interaction
- **Message Broker**: Optional publishing of domain event `ExamVersionPublishedEvent`.

---

## Part 3 — Data Interaction

- **Operation Type**: UPDATE
- **Target Table**: `exam_versions`, `exams`
- **Conditions**: `id = :versionId` / `exam_id = :examId`
- **Expected Result**: Status transitions applied correctly.
