# Examination Service – Clone Exam Version

## Part 1 — API Specification

### 1.1 Endpoint Overview
- **Path**: `POST /api/v1/exam-versions/{versionId}/clone`
- **Method**: `POST`
- **Purpose**: Creates an exact duplicate of an existing exam version under the same parent exam, generating a new `versionNo` and assigning it `DRAFT` status.

### 1.2 Request Specification

#### Headers
- `Authorization`: `Bearer <token>`

#### Path Variables
| Name | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `versionId` | UUID | Yes | Unique identifier of the source exam version to clone |

### 1.3 Response Specification

- **Success Status**: 201 Created

#### Response Body
| Name | Type | Description |
| :--- | :--- | :--- |
| `id` | UUID | Unique identifier of the NEW cloned version |
| `examId` | UUID | Parent exam ID |
| `versionNo` | Integer | New version sequence number |
| `title` | String | Title (e.g., "[Clone] Original Title") |
| `status` | String | `DRAFT` |

### 1.4 Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | Lacks `exam_version:clone` permission | Access denied |
| `NF_001` | 404 Not Found | Source version does not exist | Exam version not found |

---

## Part 2 — Processing Specification

### Controller Layer
1. Authenticate request via `Authorization` header.
2. Delegate to Service Layer.

### Service Layer
1. **Permission Validation**: Verify the user has `exam_version:clone` permission.
2. **Business Validation**: 
   - Fetch source version using `versionId`. Throw 404 if missing.
3. **Transaction Boundary**: Start database transaction.
4. **Business Workflow**:
   - Query maximum `version_no` for the `examId`.
   - Create a new `ExamVersion` instance copying all config IDs (`examStructureId`, `examRuleId`, etc.) from the source version.
   - Set `version_no = max + 1`.
   - Set `status = DRAFT`.
   - Append a suffix like `(Clone)` to the title.
   - Set `created_by` to authenticated user ID.
   - Set `created_at` to current UTC timestamp.
5. Save the new entity via Repository.
6. Commit transaction.
7. Return response DTO mapped from the newly created entity.

### Repository Layer
1. Query `exam_versions` by `id`.
2. Query `SELECT MAX(version_no) FROM exam_versions WHERE exam_id = :examId`.
3. Persist new `ExamVersion` row.

### External Interaction
None.

---

## Part 3 — Data Interaction

- **Operation Type**: INSERT
- **Target Table**: `exam_versions`
- **Conditions**: N/A
- **Expected Result**: A new duplicate row is inserted.
